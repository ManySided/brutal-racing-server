package ru.make.alex.brutal.racing.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.engine.GameRoom;
import ru.make.alex.brutal.racing.security.JwtService;
import ru.make.alex.brutal.racing.service.RoomManager;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomManager roomManager;
    private final JwtService jwtService;

    public RoomController(RoomManager roomManager, JwtService jwtService) {
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/create")
    public Mono<Map<String, String>> createRoom(@RequestHeader(HttpHeaders.AUTHORIZATION) String auth) {
        return resolvePlayerId(auth).map(playerId -> {
            GameRoom room = roomManager.createRoom(playerId);
            return Map.of("roomId", room.getRoomId());
        });
    }

    @PostMapping("/find")
    public Mono<Map<String, String>> findRoom(@RequestHeader(HttpHeaders.AUTHORIZATION) String auth) {
        return resolvePlayerId(auth).map(playerId -> {
            GameRoom room = roomManager.findAvailableRoom(playerId);
            return Map.of("roomId", room.getRoomId());
        });
    }

    private Mono<String> resolvePlayerId(String auth) {
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : "";
        String playerId = jwtService.validateTokenAndGetPlayerId(token);
        return playerId != null
                ? Mono.just(playerId)
                : Mono.error(new RuntimeException("Unauthorized"));
    }
}
