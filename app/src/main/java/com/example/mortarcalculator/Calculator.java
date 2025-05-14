package com.example.mortarcalculator;

public class Calculator {
    public static class MortarResult {
        public double angle;
        public double velocity;

        public MortarResult(double angle, double velocity) {
            this.angle = angle;
            this.velocity = velocity;
        }
    }

    public static MortarResult calculateMortar(double x, double y, double z) {
        // Placeholder: return test values
        return new MortarResult(45.0, 100.0);
    }
}