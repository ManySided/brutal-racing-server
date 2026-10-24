package ru.make.alex.brutal.racing.model.dto;

public record AuthResponse(String token,
                           String playerId,
                           String login) {
}
