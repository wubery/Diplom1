package com.example.mortarcalculator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatImageView;

public class TouchableImageView extends AppCompatImageView {
    private Matrix matrix;
    private Matrix inverseMatrix;
    private float[] lastEvent = null;
    private float dx = 0f, dy = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float scaleFactor = 1.0f;

    private PointF mortarPosition = null;
    private PointF targetPosition = null;
    private final float maxMortarRange = 1200f;
    private final float mortarKillRadius = 20f;
    private final Paint mortarPaint = new Paint();
    private final Paint targetPaint = new Paint();
    private final Paint textPaint = new Paint();
    private float azimuth = 0f;
    private float elevation = 0f;

    // MBTiles
    private MBTilesReader mapReader;
    private MBTilesReader heightmapReader;
    private int zoomLevel;
    private int tileSize = 256;
    private int mapWidth;
    private int mapHeight;
    private static final float MAX_HEIGHT = 500f;

    public TouchableImageView(Context context) {
        super(context);
        init(context);
    }

    public TouchableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        matrix = new Matrix();
        inverseMatrix = new Matrix();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());

        mortarPaint.setColor(Color.BLUE);
        mortarPaint.setStyle(Paint.Style.STROKE);
        mortarPaint.setStrokeWidth(2f);
        mortarPaint.setAlpha(100);

        targetPaint.setColor(Color.RED);
        targetPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);
    }

    public void setMapAndHeightmap(String mapMbTilesPath, String heightmapMbTilesPath) {
        if (mapReader != null) mapReader.close();
        if (heightmapReader != null) heightmapReader.close();

        mapReader = new MBTilesReader(mapMbTilesPath);
        heightmapReader = new MBTilesReader(heightmapMbTilesPath);

        zoomLevel = mapReader.getMaxZoom();
        int numTiles = 1 << zoomLevel;
        mapWidth = numTiles * tileSize;
        mapHeight = numTiles * tileSize;

        mortarPosition = null;
        targetPosition = null;
        matrix.reset();
        post(() -> {
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float dx = (viewWidth - mapWidth) / 2;
            float dy = (viewHeight - mapHeight) / 2;
            matrix.setTranslate(dx, dy);
            setImageMatrix(matrix);
            invalidate();
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateInverseMatrix();
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastEvent = new float[2];
                lastEvent[0] = event.getX();
                lastEvent[1] = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (lastEvent == null) {
                    lastEvent = new float[2];
                    lastEvent[0] = event.getX();
                    lastEvent[1] = event.getY();
                    break;
                }
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float newX = event.getX();
                    float newY = event.getY();
                    dx = newX - lastEvent[0];
                    dy = newY - lastEvent[1];
                    matrix.postTranslate(dx, dy);
                    lastEvent[0] = newX;
                    lastEvent[1] = newY;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                lastEvent = null;
                break;
        }
        setImageMatrix(matrix);

        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mapReader == null) return;

        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        int numTiles = 1 << zoomLevel;
        int minTileX = Math.max(0, (int) ((-transX) / (tileSize * scale)));
        int maxTileX = Math.min(numTiles - 1, (int) ((-transX + getWidth()) / (tileSize * scale)));
        int minTileY = Math.max(0, (int) ((-transY) / (tileSize * scale)));
        int maxTileY = Math.min(numTiles - 1, (int) ((-transY + getHeight()) / (tileSize * scale)));

        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                Bitmap tile = mapReader.getTile(zoomLevel, tileX, tileY);
                if (tile != null) {
                    float drawX = tileX * tileSize * scale + transX;
                    float drawY = tileY * tileSize * scale + transY;
                    canvas.drawBitmap(tile, drawX, drawY, null);
                    tile.recycle();
                }
            }
        }

        if (mortarPosition != null) {
            float[] mortarScreenPos = new float[]{mortarPosition.x, mortarPosition.y};
            matrix.mapPoints(mortarScreenPos);
            canvas.drawCircle(mortarScreenPos[0], mortarScreenPos[1], maxMortarRange * scaleFactor, mortarPaint);
            canvas.drawCircle(mortarScreenPos[0], mortarScreenPos[1], 10f, targetPaint);

            float mortarHeight = getHeightAtPoint(mortarPosition.x, mortarPosition.y);
            String mortarInfo = String.format("(%.0f, %.0f) высота: %.0f м",
                    mortarPosition.x, mortarPosition.y, mortarHeight);
            canvas.drawText(mortarInfo, mortarScreenPos[0] + 20f, mortarScreenPos[1] - 10f, textPaint);
        }

        if (targetPosition != null) {
            float[] targetScreenPos = new float[]{targetPosition.x, targetPosition.y};
            matrix.mapPoints(targetScreenPos);
            canvas.drawCircle(targetScreenPos[0], targetScreenPos[1], 10f, targetPaint);
            String anglesText = String.format("Азимут: %.1f°, Высота: %.1f мил", azimuth, elevation);
            canvas.drawText(anglesText, targetScreenPos[0] + 20f, targetScreenPos[1] - 10f, textPaint);
        }
    }

    public void setMortarPosition(float mapX, float mapY) {
        if (mapReader == null) return;

        if (mapX >= 0 && mapX < mapWidth && mapY >= 0 && mapY < mapHeight) {
            mortarPosition = new PointF(mapX, mapY);
            targetPosition = null;
            invalidate();
        }
    }

    public void setTargetPosition(float mapX, float mapY) {
        if (mortarPosition != null && mapReader != null) {
            if (mapX >= 0 && mapX < mapWidth && mapY >= 0 && mapY < mapHeight) {
                float distance = (float) Math.sqrt(
                        Math.pow(mapX - mortarPosition.x, 2) +
                                Math.pow(mapY - mortarPosition.y, 2)
                );
                if (distance <= maxMortarRange) {
                    PointF newTargetPosition = new PointF(mapX, mapY);
                    float mortarHeight = getHeightAtPoint(mortarPosition.x, mortarPosition.y);
                    float targetHeight = getHeightAtPoint(mapX, mapY);
                    calculateAngles(distance, mortarHeight, targetHeight);
                    targetPosition = newTargetPosition;
                    invalidate();
                }
            }
        }
    }

    private float getHeightAtPoint(float x, float y) {
        if (heightmapReader == null || x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) return 0f;

        int tileX = (int) (x / tileSize);
        int tileY = (int) (y / tileSize);
        int pixelX = (int) (x % tileSize);
        int pixelY = (int) (y % tileSize);

        Bitmap tile = heightmapReader.getTile(zoomLevel, tileX, tileY);
        if (tile == null) return 0f;

        int pixel = tile.getPixel(pixelX, pixelY);
        tile.recycle();
        int grayValue = Color.red(pixel);
        return (grayValue / 255.0f) * MAX_HEIGHT;
    }

    private void updateInverseMatrix() {
        inverseMatrix.reset();
        matrix.invert(inverseMatrix);
    }

    private PointF screenToMap(float screenX, float screenY) {
        float[] point = new float[]{screenX, screenY};
        inverseMatrix.mapPoints(point);
        return new PointF(point[0], point[1]);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private static final float MIN_SCALE = 0.1f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float oldScale = scaleFactor;
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, 5.0f));

            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float[] focusMapPoint = new float[]{focusX, focusY};
            inverseMatrix.mapPoints(focusMapPoint);
            float mapFocusX = focusMapPoint[0];
            float mapFocusY = focusMapPoint[1];

            matrix.setScale(scaleFactor, scaleFactor, focusX, focusY);
            updateInverseMatrix();

            float[] newFocusScreenPoint = new float[]{mapFocusX, mapFocusY};
            matrix.mapPoints(newFocusScreenPoint);
            float newFocusScreenX = newFocusScreenPoint[0];
            float newFocusScreenY = newFocusScreenPoint[1];

            float dx = focusX - newFocusScreenX;
            float dy = focusY - newFocusScreenY;
            matrix.postTranslate(dx, dy);

            setImageMatrix(matrix);
            adjustMapBounds();
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            PointF mapPos = screenToMap(e.getX(), e.getY());
            setMortarPosition(mapPos.x, mapPos.y);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mortarPosition != null) {
                PointF tapMapPos = screenToMap(e.getX(), e.getY());
                setTargetPosition(tapMapPos.x, tapMapPos.y);
            }
            return true;
        }
    }

    private void adjustMapBounds() {
        if (mapReader == null) return;

        float[] values = new float[9];
        matrix.getValues(values);
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float scale = values[Matrix.MSCALE_X];

        float scaledMapWidth = mapWidth * scale;
        float scaledMapHeight = mapHeight * scale;
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (scaledMapWidth < viewWidth) {
            transX = (viewWidth - scaledMapWidth) / 2;
        } else {
            if (transX > 0) transX = 0;
            if (transX < viewWidth - scaledMapWidth) transX = viewWidth - scaledMapWidth;
        }

        if (scaledMapHeight < viewHeight) {
            transY = (viewHeight - scaledMapHeight) / 2;
        } else {
            if (transY > 0) transY = 0;
            if (transY < viewHeight - scaledMapHeight) transY = viewHeight - scaledMapHeight;
        }

        matrix.setValues(new float[]{
                scale, 0, transX,
                0, scale, transY,
                0, 0, 1
        });
        setImageMatrix(matrix);
    }

    private void calculateAngles(float distance, float mortarHeight, float targetHeight) {
        if (mortarPosition == null || targetPosition == null) return;

        float deltaX = targetPosition.x - mortarPosition.x;
        float deltaY = targetPosition.y - mortarPosition.y;
        azimuth = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
        if (azimuth < 0) azimuth += 360;

        float heightDifference = targetHeight - mortarHeight;
        float adjustedDistance = (float) Math.sqrt(
                Math.pow(distance, 2) + Math.pow(heightDifference, 2)
        );

        float baseElevation = 1500f - (distance / maxMortarRange) * 600f;
        float heightAdjustment = (heightDifference / adjustedDistance) * 200f;
        elevation = baseElevation + heightAdjustment;
        elevation = Math.max(900f, Math.min(elevation, 1500f));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mapReader != null) mapReader.close();
        if (heightmapReader != null) heightmapReader.close();
    }
}