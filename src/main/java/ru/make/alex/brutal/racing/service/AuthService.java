package ru.make.alex.brutal.racing.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.model.Player;
import ru.make.alex.brutal.racing.model.dto.AuthResponse;
import ru.make.alex.brutal.racing.repository.PlayerRepository;
import ru.make.alex.brutal.racing.security.JwtService;

@Service
public class AuthService {
    private final PlayerRepository playerRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(PlayerRepository playerRepository, JwtService jwtService) {
        this.playerRepository = playerRepository;
        this.jwtService = jwtService;
    }

    public Mono<AuthResponse> register(String login, String password) {
        return playerRepository.existsByLogin(login)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new RuntimeException("Login already exists"));
                    }

                    Player newPlayer = new Player(
                            null,
                            login,
                            passwordEncoder.encode(password)
                    );

                    return playerRepository.save(newPlayer)
                            .map(player -> new AuthResponse(
                                    jwtService.generateToken(player.id().toString()),
                                    player.id().toString(),
                                    player.login()
                            ));
                });
    }

    public Mono<AuthResponse> login(String login, String password) {
        return playerRepository.findByLogin(login)
                .flatMap(player -> {
                    if (passwordEncoder.matches(password, player.passwordHash())) {
                        return Mono.just(new AuthResponse(
                                jwtService.generateToken(player.id().toString()),
                                player.id().toString(),
                                player.login()
                        ));
                    }
                    return Mono.error(new RuntimeException("Invalid credentials"));
                })
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
    }
}
