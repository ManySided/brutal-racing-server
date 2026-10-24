package ru.make.alex.brutal.racing.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.engine.GameRoom;
import ru.make.alex.brutal.racing.security.JwtService;
import ru.make.alex.brutal.racing.service.RoomManager;

import java.util.UUID;

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
        // 1. Читаем токен из query parameter
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractTokenFromQuery(query);

        if (token == null || token.isEmpty()) {
            System.out.println("WebSocket connection rejected: No token in query parameter");
            return session.close();
        }

        // 2. Валидируем токен
        String playerId = jwtService.validateTokenAndGetPlayerId(token);
        if (playerId == null) {
            System.out.println("WebSocket connection rejected: Invalid token");
            return session.close();
        }

        System.out.println("Player " + playerId + " authenticated and connected via query param.");

        // 3. Получаем или создаем комнату
        GameRoom room = roomManager.getOrCreateRoom(UUID.randomUUID().toString());
        room.addPlayer(playerId);
        room.startGame();

        // 4. Отправка данных клиенту (Game Loop)
        Mono<Void> output = session.send(room.getGameStream().map(session::textMessage));

        // 5. Получение и обработка инпутов от клиента
        Mono<Void> input = session.receive()
                .map(msg -> {
                    try {
                        JsonNode node = mapper.readTree(msg.getPayloadAsText());
                        return node.get("action").asText();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(action -> action != null)
                .doOnNext(action -> room.handleInput(playerId, action))
                .doFinally(signal -> {
                    System.out.println("Player " + playerId + " disconnected. EXPLODING!");
                    room.removePlayer(playerId);
                })
                .then();

        return Mono.zip(input, output).then();
    }

    // Извлекаем токен из query string: "token=eyJhbGci..."
    private String extractTokenFromQuery(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}
