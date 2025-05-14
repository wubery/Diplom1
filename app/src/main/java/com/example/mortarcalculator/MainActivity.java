package com.example.mortarcalculator;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MapView mapView;
    private TouchableImageView touchableImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация osmdroid
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Настройка MapView
        mapView = findViewById(R.id.mapView);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        // Отключаем тайлы, так как используется BitmapOverlay
        mapView.setUseDataConnection(false);
        Log.d(TAG, "MapView initialized without tile source");

        // Центрирование карты (пример координат для Al Basrah)
        mapView.getController().setCenter(new GeoPoint(30.5, 47.8));
        mapView.getController().setZoom(12.0);
        Log.d(TAG, "MapView centered");

        // Настройка TouchableImageView
        touchableImageView = findViewById(R.id.touchableImageView);
        touchableImageView.setMapView(mapView);
        Log.d(TAG, "TouchableImageView linked");

        // Настройка UI-элементов
        TextView mortarCoordsText = findViewById(R.id.mortarCoordsText);
        TextView targetAnglesText = findViewById(R.id.targetAnglesText);
        Button deleteButton = findViewById(R.id.deleteButton);
        Button resetButton = findViewById(R.id.resetButton);

        touchableImageView.setMortarCoordsText(mortarCoordsText);
        touchableImageView.setTargetAnglesText(targetAnglesText);
        touchableImageView.setDeleteButton(deleteButton);

        // Кнопка сброса
        resetButton.setOnClickListener(v -> {
            touchableImageView.reset();
            Log.d(TAG, "Reset button clicked");
        });

        // Добавление наложения карты
        BitmapOverlay bitmapOverlay = new BitmapOverlay(this, mapView);
        mapView.getOverlays().add(bitmapOverlay);
        Log.d(TAG, "BitmapOverlay added");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}