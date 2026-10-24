package ru.make.alex.brutal.racing.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.model.Player;

import java.util.UUID;

public interface PlayerRepository extends ReactiveCrudRepository<Player, UUID> {
    Mono<Player> findByLogin(String login);
    Mono<Boolean> existsByLogin(String login);
}