package ru.make.alex.brutal.racing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("players")
public record Player(
        @Id UUID id,
        String login,
        String passwordHash,
        LocalDateTime createdAt
) {
    public Player(UUID id, String login, String passwordHash) {
        this(id, login, passwordHash, LocalDateTime.now());
    }
}
