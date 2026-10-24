package ru.make.alex.brutal.racing.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class GameRoom {
    private final String roomId;
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final List<TrackEvent> track;
    private final List<Rocket> rockets = Collections.synchronizedList(new ArrayList<>());
    private final Sinks.Many<String> broadcastSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper mapper = new ObjectMapper();

    private Disposable gameLoop;
    private boolean isRunning = false;
    private final AtomicInteger currentTick = new AtomicInteger(0);

    public GameRoom(String roomId) {
        this.roomId = roomId;
        this.track = TrackGenerator.generateTrack();
    }

    public void addPlayer(String playerId) {
        players.put(playerId, new PlayerState(playerId));
    }

    public void removePlayer(String playerId) {
        PlayerState p = players.get(playerId);
        if (p != null) {
            p.isAlive = false; // Дисконнект = смерть
        }
    }

    public void startGame() {
        if (isRunning) return;
        isRunning = true;

        // 30 FPS (тиков в секунду)
        gameLoop = Flux.interval(Duration.ofMillis(33))
                .subscribe(tick -> {
                    updatePhysics();
                    broadcastState();
                });
    }

    private void updatePhysics() {
        int tick = currentTick.incrementAndGet();

        // Обновляем ракеты
        rockets.removeIf(Rocket::isExploded);
        rockets.forEach(Rocket::update);

        // Обновляем игроков
        for (PlayerState player : players.values()) {
            if (!player.isAlive) continue;

            // Физика ускорения
            double accel = 0.5;
            if (player.rpm > 30 && player.rpm < 70) {
                accel = 1.5; // Зеленая зона тахометра
            } else if (player.rpm >= 70) {
                accel = 0.2; // Красная зона (перегрев)
            }

            player.speed += accel;
            player.distance += player.speed / 10.0;
            player.rpm = Math.min(100, (player.speed / (player.gear * 20.0)) * 100);

            // Проверка столкновений с трассой
            for (TrackEvent event : track) {
                if (event.distance() > player.distance && event.distance() < player.distance + 10) {
                    if (event.lane() == player.lane) {
                        if (event.type() == TrackEvent.EventType.OBSTACLE ||
                                event.type() == TrackEvent.EventType.POTHOLE ||
                                event.type() == TrackEvent.EventType.SHIELD_OBSTACLE) {
                            player.isAlive = false; // ВЗРЫВ!
                        }
                    }
                }
            }

            // Проверка столкновений с ракетами
            for (Rocket rocket : rockets) {
                if (rocket.getLane() == player.lane &&
                        Math.abs(rocket.getDistance() - player.distance) < 20) {
                    if (!player.hasShield) {
                        player.isAlive = false; // ВЗРЫВ!
                    } else {
                        player.hasShield = false; // Щит поглощает урон
                    }
                }
            }
        }
    }

    private void broadcastState() {
        try {
            // Берем первого живого игрока для расчета upcoming events
            PlayerState referencePlayer = players.values().stream()
                    .filter(p -> p.isAlive)
                    .findFirst()
                    .orElse(players.values().iterator().next());

            Map<String, Object> state = new HashMap<>();
            state.put("type", "GAME_STATE");
            state.put("tick", currentTick.get());

            // Состояние всех игроков
            List<Map<String, Object>> playersData = players.values().stream()
                    .map(p -> {
                        Map<String, Object> pData = new HashMap<>();
                        pData.put("id", p.playerId);
                        pData.put("lane", p.lane);
                        pData.put("speed", (int) p.speed);
                        pData.put("distance", (int) p.distance);
                        pData.put("rpm", (int) p.rpm);
                        pData.put("gear", p.gear);
                        pData.put("alive", p.isAlive);
                        pData.put("hasShield", p.hasShield);
                        return pData;
                    })
                    .toList();
            state.put("players", playersData);

            // Предстоящие события (для HUD)
            List<TrackEvent> upcoming = track.stream()
                    .filter(e -> e.distance() > referencePlayer.distance &&
                            e.distance() < referencePlayer.distance + 300)
                    .limit(5)
                    .toList();
            state.put("upcomingEvents", upcoming);

            // Ракеты
            List<Map<String, Object>> rocketsData = rockets.stream()
                    .map(r -> {
                        Map<String, Object> rData = new HashMap<>();
                        rData.put("ownerId", r.getOwnerId());
                        rData.put("lane", r.getLane());
                        rData.put("distance", (int) r.getDistance());
                        return rData;
                    })
                    .toList();
            state.put("rockets", rocketsData);

            broadcastSink.tryEmitNext(mapper.writeValueAsString(state));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleInput(String playerId, String action) {
        PlayerState player = players.get(playerId);
        if (player == null || !player.isAlive) return;

        long currentTime = System.currentTimeMillis();

        switch (action) {
            case "MOVE_LEFT" -> {
                if (currentTime - player.lastMoveTime > 100) {
                    player.lane = Math.max(0, player.lane - 1);
                    player.lastMoveTime = currentTime;
                }
            }
            case "MOVE_RIGHT" -> {
                if (currentTime - player.lastMoveTime > 100) {
                    player.lane = Math.min(3, player.lane + 1);
                    player.lastMoveTime = currentTime;
                }
            }
            case "SHIFT_GEAR" -> {
                if (currentTime - player.lastGearShift > 1000) {
                    player.gear = (player.gear % 4) + 1;
                    player.lastGearShift = currentTime;
                }
            }
            case "DROP_SHIELD" -> {
                if (player.hasShield) {
                    player.hasShield = false;
                    player.speed += 10; // Бонус к скорости
                    // Спавним щит как препятствие
                    track.add(new TrackEvent(
                            (int) player.distance + 50,
                            player.lane,
                            TrackEvent.EventType.SHIELD_OBSTACLE
                    ));
                }
            }
            case "FIRE_ROCKET" -> {
                if (currentTime - player.lastRocketTime > 3000) {
                    rockets.add(new Rocket(playerId, player.lane, player.distance));
                    player.lastRocketTime = currentTime;
                }
            }
        }
    }

    public boolean isFull() {
        return players.size() >= 4;
    }

    public Flux<String> getGameStream() {
        return broadcastSink.asFlux();
    }
}
