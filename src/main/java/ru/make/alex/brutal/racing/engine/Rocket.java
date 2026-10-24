package ru.make.alex.brutal.racing.engine;

import lombok.Getter;

@Getter
public class Rocket {
    private final String ownerId;
    private final int lane;
    private double distance;
    private final double speed;
    private final double maxDistance;
    private boolean exploded;

    public Rocket(String ownerId, int lane, double startDistance) {
        this.ownerId = ownerId;
        this.lane = lane;
        this.distance = startDistance;
        this.speed = 50.0; // Скорость ракеты (постоянная)
        this.maxDistance = startDistance + 500; // Запас хода
        this.exploded = false;
    }

    public void update() {
        if (!exploded) {
            distance += speed / 30.0; // 30 FPS
            if (distance >= maxDistance) {
                exploded = true;
            }
        }
    }
}
