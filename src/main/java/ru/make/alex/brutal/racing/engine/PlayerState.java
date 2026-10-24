package ru.make.alex.brutal.racing.engine;

public class PlayerState {
    public final String playerId;
    public int lane = 0;
    public double speed = 0;
    public double distance = 0;
    public double rpm = 0;
    public int gear = 1;
    public boolean isAlive = true;
    public boolean hasShield = true;

    // Anti-cheat timestamps
    public long lastMoveTime = 0;
    public long lastGearShift = 0;
    public long lastRocketTime = 0;

    public PlayerState(String playerId) {
        this.playerId = playerId;
    }
}
