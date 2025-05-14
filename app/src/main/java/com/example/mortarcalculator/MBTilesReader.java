package com.example.mortarcalculator;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MBTilesReader {
    private SQLiteDatabase database;
    private int minZoom;
    private int maxZoom;

    public MBTilesReader(String mbTilesPath) {
        try {
            database = SQLiteDatabase.openDatabase(mbTilesPath, null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = database.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null);
            if (cursor.moveToFirst()) {
                minZoom = cursor.getInt(0);
                maxZoom = cursor.getInt(1);
            }
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to open MBTiles file: " + mbTilesPath, e);
        }
    }

    public Bitmap getTile(int zoom, int tileX, int tileY) {
        int tmsTileY = (1 << zoom) - 1 - tileY;
        String query = "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?";
        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(zoom), String.valueOf(tileX), String.valueOf(tmsTileY)});
        Bitmap bitmap = null;
        if (cursor.moveToFirst()) {
            byte[] tileData = cursor.getBlob(0);
            bitmap = BitmapFactory.decodeByteArray(tileData, 0, tileData.length);
        }
        cursor.close();
        return bitmap;
    }

    public void close() {
        if (database != null && database.isOpen()) {
            database.close();
        }
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    public int getMinZoom() {
        return minZoom;
    }
}