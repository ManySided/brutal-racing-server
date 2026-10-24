package ru.make.alex.brutal.racing.config;

import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

// @Configuration
public class CorsConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Применяем ко всем эндпоинтам
                .allowedOrigins("*") // Разрешаем ЛЮБОЙ источник (для локальной разработки)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Разрешенные HTTP методы
                .allowedHeaders("*") // Разрешаем любые заголовки (включая Authorization)
                .maxAge(3600); // Кэшируем preflight-запрос на 1 час, чтобы не спамить сервер
    }
}
