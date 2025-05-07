package com.example.mortarcalculator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Checking storage permissions");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Requesting storage permissions");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        } else {
            Log.d(TAG, "onCreate: Permissions already granted, initializing map");
            copyMbTilesFromAssets();
            initializeMap();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onRequestPermissionsResult: Permissions granted, initializing map");
            copyMbTilesFromAssets();
            initializeMap();
        } else {
            Log.e(TAG, "onRequestPermissionsResult: Permissions denied");
            Toast.makeText(this, "Требуется разрешение на доступ к хранилищу", Toast.LENGTH_LONG).show();
        }
    }

    private void copyMbTilesFromAssets() {
        try {
            String[] files = {"map.mbtiles", "heightmap.mbtiles"};
            File filesDir = getExternalFilesDir(null);
            if (filesDir == null) {
                Log.e(TAG, "copyMbTilesFromAssets: External files directory is null");
                Toast.makeText(this, "Не удалось получить директорию для файлов", Toast.LENGTH_LONG).show();
                return;
            }
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                Log.e(TAG, "copyMbTilesFromAssets: Failed to create directory: " + filesDir.getAbsolutePath());
                Toast.makeText(this, "Не удалось создать директорию для файлов", Toast.LENGTH_LONG).show();
                return;
            }

            for (String file : files) {
                File destFile = new File(filesDir, file);
                if (!destFile.exists()) {
                    Log.d(TAG, "Copying " + file + " from assets to " + destFile.getAbsolutePath());
                    InputStream is = getAssets().open(file);
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    fos.close();
                    Log.d(TAG, "Copied " + file + " to " + destFile.getAbsolutePath());
                } else {
                    Log.d(TAG, file + " already exists at " + destFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying MBTiles from assets", e);
            Toast.makeText(this, "Ошибка копирования карт: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeMap() {
        Log.d(TAG, "initializeMap: Starting map initialization");
        File filesDir = getExternalFilesDir(null);
        if (filesDir == null) {
            Log.e(TAG, "initializeMap: External files directory is null");
            Toast.makeText(this, "Не удалось получить директорию для файлов", Toast.LENGTH_LONG).show();
            return;
        }
        if (!filesDir.exists() && !filesDir.mkdirs()) {
            Log.e(TAG, "initializeMap: Failed to create directory: " + filesDir.getAbsolutePath());
            Toast.makeText(this, "Не удалось создать директорию для файлов", Toast.LENGTH_LONG).show();
            return;
        }

        String mapPath = filesDir.getAbsolutePath() + "/map.mbtiles";
        String heightmapPath = filesDir.getAbsolutePath() + "/heightmap.mbtiles";
        Log.d(TAG, "initializeMap: Map path: " + mapPath);
        Log.d(TAG, "initializeMap: Heightmap path: " + heightmapPath);

        File mapFile = new File(mapPath);
        File heightmapFile = new File(heightmapPath);
        if (!mapFile.exists() || !heightmapFile.exists()) {
            Log.e(TAG, "initializeMap: MBTiles files not found - map: " + mapPath + ", heightmap: " + heightmapPath);
            Toast.makeText(this, "Файлы карты или высот не найдены", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Log.d(TAG, "initializeMap: Loading MBTiles files");
            TouchableImageView mapView = findViewById(R.id.map_view);
            mapView.setMapAndHeightmap(mapPath, heightmapPath);
            Log.d(TAG, "initializeMap: Map initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "initializeMap: Error loading MBTiles", e);
            Toast.makeText(this, "Ошибка загрузки карты: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}