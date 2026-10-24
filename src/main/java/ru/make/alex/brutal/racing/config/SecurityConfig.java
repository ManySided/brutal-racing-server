package ru.make.alex.brutal.racing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Отключаем CSRF-защиту (мы используем JWT, а не cookies)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Разрешаем доступ ко всем эндпоинтам (авторизацию делаем сами через JWT)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )

                // Отключаем form login и http basic (мы делаем свою авторизацию)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .build();
    }
}
