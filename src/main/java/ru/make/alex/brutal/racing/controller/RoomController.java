package ru.make.alex.brutal.racing.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.make.alex.brutal.racing.engine.GameRoom;
import ru.make.alex.brutal.racing.service.RoomManager;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {
    private final RoomManager roomManager;

    public RoomController(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @PostMapping("/create")
    public Map<String, String> createRoom() {
        GameRoom room = roomManager.createPrivateRoom();
        return Map.of("roomId", room.toString());
    }

    @PostMapping("/find")
    public Map<String, String> findRoom() {
        GameRoom room = roomManager.findAvailableRoom();
        return Map.of("roomId", room.getRoomId());
    }
}
