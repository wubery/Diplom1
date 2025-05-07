package com.example.mortarcalculator;

public class Calculator {
    public static class MortarResult {
        public double distance;
        public double angle;

        public MortarResult(double distance, double angle) {
            this.distance = distance;
            this.angle = angle;
        }
    }

    public static MortarResult calculateMortar(double targetX, double targetY, double mortarX, double mortarY) {
        double dx = targetX - mortarX;
        double dy = targetY - mortarY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360; // Нормализация угла (0-360 градусов)
        }
        return new MortarResult(distance, angle);
    }
}