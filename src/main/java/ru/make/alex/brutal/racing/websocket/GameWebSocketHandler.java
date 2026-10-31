package ru.make.alex.brutal.racing.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.engine.GameRoom;
import ru.make.alex.brutal.racing.security.JwtService;
import ru.make.alex.brutal.racing.service.RoomManager;

import java.util.Objects;

@Component
public class GameWebSocketHandler implements WebSocketHandler {

    private final RoomManager roomManager;
    private final JwtService jwtService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GameWebSocketHandler(RoomManager roomManager, JwtService jwtService) {
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractParam(query, "token");
        String roomId = extractParam(query, "roomId");
        String login = extractParam(query, "login"); // <-- ИЗВЛЕКАЕМ LOGIN

        if (token == null || roomId == null || login == null) {
            System.out.println("WebSocket connection rejected: Missing token, roomId or login");
            return session.close();
        }

        String playerId = jwtService.validateTokenAndGetPlayerId(token);
        if (playerId == null) {
            System.out.println("WebSocket connection rejected: Invalid token");
            return session.close();
        }

        var room = roomManager.getRoom(roomId);
        if (room == null) {
            System.out.println("WebSocket connection rejected: Room not found");
            return session.close();
        }

        System.out.println("Player " + login + " (" + playerId + ") joined room " + roomId);
        room.addPlayer(playerId, login); // <-- ПЕРЕДАЁМ LOGIN

        // Отправляем текущее состояние лобби новому игроку СРАЗУ
        Mono<Void> initialState = session.send(
                Flux.just(session.textMessage(room.getLastLobbyState()))
        );

        // Затем поток из gameStream
        Mono<Void> output = session.send(
                Flux.concat(
                        Flux.just(session.textMessage(room.getLastLobbyState())),
                        room.getGameStream().map(session::textMessage)
                )
        );

        Mono<Void> input = session.receive()
                .map(msg -> {
                    try {
                        return mapper.readTree(msg.getPayloadAsText());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .doOnNext(node -> {
                    String type = node.has("type") ? node.get("type").asText() : "";

                    if ("READY".equals(type)) {
                        boolean ready = node.get("ready").asBoolean();
                        room.setPlayerReady(playerId, ready);
                    } else if ("START".equals(type)) {
                        if (playerId.equals(room.getCreatorId())) {
                            System.out.println("Start requested by creator " + playerId);
                            room.startCountdown();
                        } else {
                            System.out.println("Start rejected: player " + playerId + " is not creator");
                        }
                    } else if ("ACTION".equals(type)) {
                        String action = node.get("action").asText();
                        room.handleInput(playerId, action);
                    }
                })
                .doFinally(signal -> {
                    System.out.println("Player " + playerId + " disconnected");
                    room.removePlayer(playerId);
                })
                .then();

        return Mono.zip(input, output).then();
    }

    private String extractParam(String query, String paramName) {
        if (query == null || query.isEmpty()) return null;
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}