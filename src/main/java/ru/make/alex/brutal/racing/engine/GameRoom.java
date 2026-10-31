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

    public enum GamePhase {
        WAITING, COUNTDOWN, RACING, FINISHED
    }

    private final String roomId;
    private final String creatorId;
    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final List<TrackEvent> track;
    private final List<Rocket> rockets = Collections.synchronizedList(new ArrayList<>());
    private final Sinks.Many<String> broadcastSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper mapper = new ObjectMapper();

    private Disposable gameLoop;
    private GamePhase phase = GamePhase.WAITING;
    private int countdown = 5;
    private final AtomicInteger currentTick = new AtomicInteger(0);

    private static final int MAX_PLAYERS = 4;
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final double MAX_RPM = 100.0;
    private static final double ENGINE_EXPLODE_RPM = 95.0;

    private String lastLobbyState = "{}";

    public GameRoom(String roomId, String creatorId) {
        this.roomId = roomId;
        this.creatorId = creatorId;
        this.track = TrackGenerator.generateTrack();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public void addPlayer(String playerId, String login) {
        if (players.size() < MAX_PLAYERS && phase == GamePhase.WAITING) {
            players.put(playerId, new PlayerState(playerId, login));
            broadcastLobbyState();
        }
    }

    public void removePlayer(String playerId) {
        PlayerState p = players.get(playerId);
        if (p != null) {
            p.isAlive = false;
            players.remove(playerId);
            broadcastLobbyState();
        }
    }

    public void setPlayerReady(String playerId, boolean ready) {
        PlayerState p = players.get(playerId);
        if (p != null && phase == GamePhase.WAITING) {
            p.isReady = ready;
            broadcastLobbyState();
        }
    }

    public void startCountdown() {
        if (phase != GamePhase.WAITING) return;
        if (players.size() < MIN_PLAYERS_TO_START) return;

        boolean allReady = players.values().stream().allMatch(p -> p.isReady);
        if (!allReady) return;

        phase = GamePhase.COUNTDOWN;
        countdown = 5;

        gameLoop = Flux.interval(Duration.ofSeconds(1))
                .subscribe(tick -> {
                    if (countdown > 0) {
                        broadcastCountdown();
                        countdown--;
                    } else {
                        phase = GamePhase.RACING;
                        startRace();
                    }
                });
    }

    private void startRace() {
        if (gameLoop != null) gameLoop.dispose();

        gameLoop = Flux.interval(Duration.ofMillis(33))
                .subscribe(tick -> {
                    updatePhysics();
                    broadcastRaceState();
                });
    }

    private void updatePhysics() {
        int tick = currentTick.incrementAndGet();

        rockets.removeIf(Rocket::isExploded);
        rockets.forEach(Rocket::update);

        for (PlayerState player : players.values()) {
            if (!player.isAlive) continue;

            // ========== ИСПРАВЛЕННАЯ ФИЗИКА ==========

            // Базовое ускорение зависит от зоны тахометра
            double accel;
            if (player.rpm < 30) {
                accel = 0.3; // Синяя зона - слабое ускорение
            } else if (player.rpm < 70) {
                accel = 2.0; // Зелёная зона - сильное ускорение
            } else if (player.rpm < 90) {
                accel = 1.0; // Жёлтая зона - среднее ускорение
            } else {
                accel = 0.1; // Красная зона - очень слабое (перегрев)
            }

            // Скорость растёт
            player.speed += accel;

            // Дистанция растёт пропорционально скорости
            player.distance += player.speed / 10.0;

            // ========== ИСПРАВЛЕННЫЙ РАСЧЁТ RPM ==========
            // Чем выше передача, тем медленнее растут обороты
            // gear=1: rpm растёт быстро
            // gear=4: rpm растёт медленно
            double rpmGrowth = player.speed / (player.gear * 50.0) * 100;
            player.rpm = Math.min(MAX_RPM, rpmGrowth);

            // Проверка взрыва двигателя
            if (player.rpm >= ENGINE_EXPLODE_RPM) {
                player.explodeTime++;
                if (player.explodeTime > 30) {
                    player.isAlive = false;
                    player.deathReason = "ENGINE_EXPLOSION";
                }
            } else {
                player.explodeTime = 0;
            }

            // Проверка столкновений с трассой
            for (TrackEvent event : track) {
                if (event.distance() > player.distance && event.distance() < player.distance + 10) {
                    if (event.lane() == player.lane) {
                        if (event.type() == TrackEvent.EventType.OBSTACLE ||
                                event.type() == TrackEvent.EventType.POTHOLE ||
                                event.type() == TrackEvent.EventType.SHIELD_OBSTACLE) {
                            player.isAlive = false;
                            player.deathReason = "COLLISION";
                        }
                    }
                }
            }

            // Проверка столкновений с ракетами
            for (Rocket rocket : rockets) {
                if (rocket.getLane() == player.lane &&
                        Math.abs(rocket.getDistance() - player.distance) < 20) {
                    if (!player.hasShield) {
                        player.isAlive = false;
                        player.deathReason = "ROCKET_HIT";
                    } else {
                        player.hasShield = false;
                    }
                }
            }

            // Проверка финиша
            if (player.distance >= 5000 && !player.finished) {
                player.finished = true;
                player.finishTime = currentTick.get();
                player.finishPosition = getFinishPosition(player);
            }
        }

        // Проверка окончания гонки
        long aliveCount = players.values().stream().filter(p -> p.isAlive).count();
        long finishedCount = players.values().stream().filter(p -> p.finished).count();

        if (aliveCount == 0 || finishedCount == players.size()) {
            phase = GamePhase.FINISHED;
            if (gameLoop != null) gameLoop.dispose();
            broadcastRaceState();
        }
    }

    private int getFinishPosition(PlayerState player) {
        long finishedBefore = players.values().stream()
                .filter(p -> p.finished && p.finishTime < player.finishTime)
                .count();
        return (int) finishedBefore + 1;
    }

    private void broadcastLobbyState() {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("type", "LOBBY_STATE");
            state.put("roomId", roomId);
            state.put("phase", phase.name());
            state.put("creatorId", creatorId);

            List<Map<String, Object>> playersData = players.values().stream()
                    .map(p -> {
                        Map<String, Object> pData = new HashMap<>();
                        pData.put("id", p.playerId);
                        pData.put("login", p.login);
                        pData.put("ready", p.isReady);
                        return pData;
                    })
                    .toList();
            state.put("players", playersData);

            String json = mapper.writeValueAsString(state);
            lastLobbyState = json;
            broadcastSink.tryEmitNext(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastCountdown() {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("type", "COUNTDOWN");
            state.put("countdown", countdown);
            broadcastSink.tryEmitNext(mapper.writeValueAsString(state));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastRaceState() {
        try {
            List<PlayerState> sortedPlayers = players.values().stream()
                    .sorted((a, b) -> Double.compare(b.distance, a.distance))
                    .toList();

            Map<String, Object> state = new HashMap<>();
            state.put("type", "RACE_STATE");
            state.put("tick", currentTick.get());
            state.put("phase", phase.name());

            List<Map<String, Object>> leaderboard = new ArrayList<>();
            for (int i = 0; i < sortedPlayers.size(); i++) {
                PlayerState p = sortedPlayers.get(i);
                Map<String, Object> pData = new HashMap<>();
                pData.put("position", i + 1);
                pData.put("id", p.playerId);
                pData.put("login", p.login);
                pData.put("lane", p.lane);
                pData.put("speed", (int) p.speed);
                pData.put("distance", (int) p.distance);
                pData.put("rpm", (int) p.rpm);
                pData.put("gear", p.gear);
                pData.put("alive", p.isAlive);
                pData.put("hasShield", p.hasShield);
                pData.put("finished", p.finished);
                pData.put("finishPosition", p.finishPosition);
                leaderboard.add(pData);
            }
            state.put("leaderboard", leaderboard);

            // ========== ИСПРАВЛЕНО: препятствия для каждого игрока ==========
            Map<String, List<Map<String, Object>>> hazardsByPlayer = new HashMap<>();
            for (PlayerState p : players.values()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (TrackEvent e : track) {
                    int delta = e.distance() - (int) p.distance;
                    if (delta > 0 && delta < 400) {
                        Map<String, Object> hazard = new HashMap<>();
                        hazard.put("distance", e.distance());
                        hazard.put("delta", delta);
                        hazard.put("lane", e.lane());
                        hazard.put("type", e.type().name());
                        list.add(hazard);
                    }
                }
                hazardsByPlayer.put(p.playerId, list);
            }
            state.put("hazardsByPlayer", hazardsByPlayer);

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

            List<Map<String, Object>> overtakeEvents = detectOvertakes(sortedPlayers);
            state.put("overtakes", overtakeEvents);

            broadcastSink.tryEmitNext(mapper.writeValueAsString(state));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Map<String, Object>> detectOvertakes(List<PlayerState> sortedPlayers) {
        List<Map<String, Object>> overtakes = new ArrayList<>();

        for (PlayerState player : sortedPlayers) {
            int currentPosition = sortedPlayers.indexOf(player);

            if (player.lastPosition != -1 && currentPosition < player.lastPosition) {
                Map<String, Object> overtake = new HashMap<>();
                overtake.put("playerId", player.playerId);
                overtake.put("overtakenPosition", player.lastPosition + 1);
                overtake.put("newPosition", currentPosition + 1);
                overtakes.add(overtake);
            }

            player.lastPosition = currentPosition;
        }

        return overtakes;
    }

    public void handleInput(String playerId, String action) {
        PlayerState player = players.get(playerId);
        if (player == null || !player.isAlive || phase != GamePhase.RACING) return;

        long currentTime = System.currentTimeMillis();

        switch (action) {
            case "MOVE_LEFT" -> {
                // ========== ИСПРАВЛЕНО: кулдаун 50 мс (быстрее реакция) ==========
                if (currentTime - player.lastMoveTime > 50) {
                    player.lane = Math.max(0, player.lane - 1);
                    player.lastMoveTime = currentTime;
                }
            }
            case "MOVE_RIGHT" -> {
                if (currentTime - player.lastMoveTime > 50) {
                    player.lane = Math.min(3, player.lane + 1);
                    player.lastMoveTime = currentTime;
                }
            }
            case "SHIFT_GEAR" -> {
                // ========== ИСПРАВЛЕНО: кулдаун 300 мс (быстрее переключение) ==========
                if (currentTime - player.lastGearShift > 300) {
                    player.gear = (player.gear % 4) + 1;
                    player.lastGearShift = currentTime;
                }
            }
            case "DROP_SHIELD" -> {
                if (player.hasShield) {
                    player.hasShield = false;
                    player.speed += 10;
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

    public Flux<String> getGameStream() {
        return broadcastSink.asFlux();
    }
}