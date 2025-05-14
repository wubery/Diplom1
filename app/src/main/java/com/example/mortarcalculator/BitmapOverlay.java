package com.example.mortarcalculator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.io.IOException;
import java.io.InputStream;

public class BitmapOverlay extends Overlay {
    private static final String TAG = "BitmapOverlay";
    private final Bitmap bitmap;
    private final BoundingBox boundingBox;

    public BitmapOverlay(Context context, MapView mapView) {
        super();
        Bitmap tempBitmap = null;
        try (InputStream inputStream = context.getAssets().open("al_basrah/map.png")) {
            // Decode bitmap with scaling
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.reset();
            options.inSampleSize = calculateInSampleSize(options, 2048, 2048);
            options.inJustDecodeBounds = false;
            tempBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (tempBitmap == null) {
                throw new IOException("BitmapFactory returned null");
            }
            Log.d(TAG, "Bitmap loaded: " + tempBitmap.getWidth() + "x" + tempBitmap.getHeight());
        } catch (IOException e) {
            Log.e(TAG, "Failed to load map.png: " + e.getMessage());
            throw new RuntimeException("Cannot load map.png", e);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OutOfMemoryError loading map.png: " + e.getMessage());
            throw new RuntimeException("OutOfMemoryError loading map.png", e);
        }
        this.bitmap = tempBitmap;

        // Map boundaries for Al Basrah
        double north = 31.0;
        double south = 30.0;
        double east = 48.0;
        double west = 47.0;
        boundingBox = new BoundingBox(north, east, south, west);
        Log.d(TAG, "BoundingBox set: " + boundingBox.toString());
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow || bitmap == null) {
            Log.w(TAG, "Skipping draw: shadow=" + shadow + ", bitmap=" + (bitmap == null));
            return;
        }

        // Get map projection
        Rect screenRect = new Rect();
        canvas.getClipBounds(screenRect);
        Log.d(TAG, "Screen rect: " + screenRect.toString());

        // Convert geographic coordinates to pixels
        GeoPoint topLeftGeo = new GeoPoint(boundingBox.getLatNorth(), boundingBox.getLonWest());
        GeoPoint bottomRightGeo = new GeoPoint(boundingBox.getLatSouth(), boundingBox.getLonEast());
        Point topLeft = mapView.getProjection().toPixels(topLeftGeo, null);
        Point bottomRight = mapView.getProjection().toPixels(bottomRightGeo, null);

        if (topLeft == null || bottomRight == null) {
            Log.w(TAG, "Projection returned null coordinates");
            return;
        }

        // Define drawing area
        Rect destRect = new Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y);
        Log.d(TAG, "Drawing bitmap to destRect: " + destRect.toString());

        // Draw the bitmap
        try {
            canvas.drawBitmap(bitmap, null, destRect, null);
            Log.d(TAG, "Bitmap drawn successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error drawing bitmap: " + e.getMessage());
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}