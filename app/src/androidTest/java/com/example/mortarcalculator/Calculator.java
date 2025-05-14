package com.example.mortarcalculator;

public class Calculator {
    public static class MortarResult {
        public double angle;
        public double distance;
    }

    public static MortarResult calculateMortar(float targetX, float targetY, float mortarX, float mortarY) {
        MortarResult result = new MortarResult();
        float dx = targetX - mortarX;
        float dy = targetY - mortarY;
        result.distance = Math.sqrt(dx*dx + dy*dy);
        result.angle = Math.toDegrees(Math.atan2(dy, dx));
        return result;
    }
}