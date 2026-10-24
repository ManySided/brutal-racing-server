package ru.make.alex.brutal.racing.service;

import org.springframework.stereotype.Service;
import ru.make.alex.brutal.racing.engine.GameRoom;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public GameRoom getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, GameRoom::new);
    }

    public GameRoom createPrivateRoom() {
        String roomId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(roomId);
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom findAvailableRoom() {
        return rooms.values().stream()
                .filter(room -> !room.isFull())
                .findFirst()
                .orElseGet(this::createPrivateRoom);
    }
}
