package ru.make.alex.brutal.racing.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrackGenerator {
    private static final int TOTAL_DISTANCE = 5000;
    private static final int LANES = 4;

    public static List<TrackEvent> generateTrack() {
        List<TrackEvent> events = new ArrayList<>();
        Random random = new Random();
        int currentDistance = 300; // Начальная безопасная зона

        while (currentDistance < TOTAL_DISTANCE) {
            int lane = random.nextInt(LANES);

            // 70% смертельных препятствий, 30% маяков
            TrackEvent.EventType type;
            if (random.nextDouble() < 0.7) {
                type = random.nextBoolean() ?
                        TrackEvent.EventType.OBSTACLE :
                        TrackEvent.EventType.POTHOLE;
            } else {
                type = random.nextBoolean() ?
                        TrackEvent.EventType.BEACON_VFX :
                        TrackEvent.EventType.BEACON_SFX;
            }

            events.add(new TrackEvent(currentDistance, lane, type));

            // Увеличиваем сложность: чем дальше, тем чаще события
            int gap = Math.max(50, 150 - (currentDistance / 50));
            currentDistance += gap + random.nextInt(100);
        }

        return events;
    }

}
