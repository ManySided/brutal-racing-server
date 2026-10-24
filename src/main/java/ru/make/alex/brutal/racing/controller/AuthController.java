package ru.make.alex.brutal.racing.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.make.alex.brutal.racing.model.dto.AuthResponse;
import ru.make.alex.brutal.racing.model.dto.LoginRequest;
import ru.make.alex.brutal.racing.model.dto.RegisterRequest;
import ru.make.alex.brutal.racing.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<AuthResponse> register(@RequestBody RegisterRequest request) {
        return authService.register(request.login(), request.password());
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request.login(), request.password());
    }
}
