package ru.make.alex.brutal.racing.engine;

public record TrackEvent(int distance,
                         int lane,
                         EventType type) {
    public enum EventType {
        OBSTACLE,      // Смертельное препятствие
        POTHOLE,       // Смертельная яма
        BEACON_VFX,    // Визуальный маяк (для эффектов)
        BEACON_SFX,    // Звуковой маяк
        SHIELD_OBSTACLE // Щит другого игрока
    }
}
