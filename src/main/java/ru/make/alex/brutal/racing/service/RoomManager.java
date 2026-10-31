package ru.make.alex.brutal.racing.service;

import org.springframework.stereotype.Service;
import ru.make.alex.brutal.racing.engine.GameRoom;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public GameRoom createRoom(String creatorId) {
        String roomId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(roomId, creatorId);
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom findAvailableRoom(String playerId) {
        return rooms.values().stream()
                .filter(room -> !room.isFull() && room.getPhase() == GameRoom.GamePhase.WAITING)
                .findFirst()
                .orElseGet(() -> createRoom(playerId));
    }
}
