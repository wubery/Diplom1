package com.example.mortarcalculator;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TouchableImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static final String TAG = "TouchableImageView";
    private MapView mapView;
    private GestureDetector gestureDetector;
    private List<Mortar> mortars = new ArrayList<>();
    private TextView mortarCoordsText;
    private GeoPoint targetPoint;
    private Marker targetMarker;
    private TextView targetAnglesText;
    private Button deleteButton;
    private Mortar selectedMortar;
    private static final double MAX_RANGE = 5000;
    private static final int MAX_MORTARS = 5;

    private static class Mortar {
        GeoPoint point;
        Marker marker;
        Polygon rangeCircle;
        double elevation;

        Mortar(GeoPoint point, Marker marker, Polygon rangeCircle, double elevation) {
            this.point = point;
            this.marker = marker;
            this.rangeCircle = rangeCircle;
            this.elevation = elevation;
        }
    }

    public TouchableImageView(Context context) {
        super(context);
        init(context);
    }

    public TouchableImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TouchableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
        setClickable(true);
        Log.d(TAG, "TouchableImageView initialized");
    }

    void setMapView(MapView mapView) {
        this.mapView = mapView;
        Log.d(TAG, "MapView set: " + (mapView != null));
    }

    public void setMortarCoordsText(TextView textView) {
        this.mortarCoordsText = textView;
        Log.d(TAG, "MortarCoordsText set");
    }

    public void setTargetAnglesText(TextView textView) {
        this.targetAnglesText = textView;
        Log.d(TAG, "TargetAnglesText set");
    }

    public void setDeleteButton(Button button) {
        this.deleteButton = button;
        deleteButton.setVisibility(View.GONE);
        deleteButton.setOnClickListener(v -> {
            if (selectedMortar != null && mapView != null) {
                mapView.getOverlays().remove(selectedMortar.marker);
                mapView.getOverlays().remove(selectedMortar.rangeCircle);
                mortars.remove(selectedMortar);
                updateMortarCoordsText();
                mapView.invalidate();
                Log.d(TAG, "Mortar deleted via button");
                selectedMortar = null;
                deleteButton.setVisibility(View.GONE);
            }
        });
        Log.d(TAG, "DeleteButton set");
    }

    public void reset() {
        if (mapView == null) {
            Log.e(TAG, "Cannot reset: MapView is null");
            return;
        }
        for (Mortar mortar : mortars) {
            mapView.getOverlays().remove(mortar.marker);
            mapView.getOverlays().remove(mortar.rangeCircle);
        }
        mortars.clear();
        if (targetMarker != null) {
            mapView.getOverlays().remove(targetMarker);
            targetMarker = null;
            targetPoint = null;
        }
        updateMortarCoordsText();
        if (targetAnglesText != null) {
            targetAnglesText.setText("Цель: не установлена");
        }
        if (deleteButton != null) {
            deleteButton.setVisibility(View.GONE);
        }
        mapView.invalidate();
        Log.d(TAG, "Reset: all mortars and targets cleared");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "Touch event: action=" + event.getAction() + ", pointerCount=" + event.getPointerCount());
        if (mapView == null) {
            Log.e(TAG, "MapView is null, cannot process touch event");
            return super.onTouchEvent(event);
        }

        // Pass all touch events to MapView first
        boolean mapViewHandled = mapView.onTouchEvent(event);
        Log.d(TAG, "Touch event passed to MapView, handled=" + mapViewHandled);

        // Process gestures
        boolean gestureHandled = gestureDetector.onTouchEvent(event);

        return gestureHandled || mapViewHandled || super.onTouchEvent(event);
    }

    private void handleSingleTap(float touchX, float touchY) {
        if (mapView == null) {
            Log.e(TAG, "MapView not set");
            return;
        }

        GeoPoint geoPoint = (GeoPoint) mapView.getProjection().fromPixels((int) touchX, (int) touchY);
        if (geoPoint == null) {
            Log.e(TAG, "Projection returned null GeoPoint for x=" + touchX + ", y=" + touchY);
            return;
        }
        double lat = geoPoint.getLatitude();
        double lon = geoPoint.getLongitude();
        Log.d(TAG, "Single tap at: x=" + touchX + ", y=" + touchY + ", lat=" + lat + ", lon=" + lon);

        selectedMortar = null;
        for (Mortar mortar : mortars) {
            double distance = mortar.point.distanceToAsDouble(geoPoint);
            if (distance < 100) {
                selectedMortar = mortar;
                if (deleteButton != null) {
                    deleteButton.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Mortar selected for deletion");
                }
                return;
            }
        }

        if (mortars.isEmpty()) {
            Log.e(TAG, "No mortars set");
            return;
        }

        double z;
        try (InputStream tifStream = getContext().getAssets().open("N053E046/N053E046/ALPSMLC30_N053E046.tif")) {
            z = DSMReader.getElevation(lat, lon, tifStream);
            Log.d(TAG, "Target elevation: " + z + " m");
        } catch (IOException e) {
            Log.e(TAG, "DSM read error: " + e.getMessage());
            z = 0.0;
        }

        Mortar closestMortar = null;
        double minDistance = Double.MAX_VALUE;
        double[] localCoords = new double[2];
        for (Mortar mortar : mortars) {
            localCoords = convertToLocalCoordinates(lat, lon, mortar.point);
            double distance = Math.sqrt(localCoords[0] * localCoords[0] + localCoords[1] * localCoords[1]);
            if (distance < minDistance && distance <= MAX_RANGE) {
                minDistance = distance;
                closestMortar = mortar;
            }
        }

        if (closestMortar == null) {
            Log.w(TAG, "Target out of range for all mortars");
            return;
        }

        double azimuth = calculateAzimuth(closestMortar.point, geoPoint);
        Calculator.MortarResult result = Calculator.calculateMortar(localCoords[0], localCoords[1], z);
        Log.d(TAG, "Target: azimuth=" + azimuth + "°, angle=" + result.angle + "°, velocity=" + result.velocity);

        if (targetMarker != null) {
            mapView.getOverlays().remove(targetMarker);
        }
        targetPoint = new GeoPoint(lat, lon);
        targetMarker = new Marker(mapView);
        targetMarker.setPosition(targetPoint);
        targetMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(targetMarker);
        mapView.invalidate();

        if (targetAnglesText != null) {
            targetAnglesText.setText(String.format("Цель: Азимут=%.1f°, Угол=%.1f°, Скорость=%.1f м/с, Дистанция=%.1f м",
                    azimuth, result.angle, result.velocity, minDistance));
        }
    }

    private void handleDoubleTap(float touchX, float touchY) {
        if (mapView == null) {
            Log.e(TAG, "MapView not set");
            return;
        }

        if (mortars.size() >= MAX_MORTARS) {
            Log.w(TAG, "Maximum number of mortars (" + MAX_MORTARS + ") reached");
            return;
        }

        GeoPoint geoPoint = (GeoPoint) mapView.getProjection().fromPixels((int) touchX, (int) touchY);
        if (geoPoint == null) {
            Log.e(TAG, "Projection returned null GeoPoint for x=" + touchX + ", y=" + touchY);
            return;
        }
        double lat = geoPoint.getLatitude();
        double lon = geoPoint.getLongitude();
        Log.d(TAG, "Double tap at: x=" + touchX + ", y=" + touchY + ", lat=" + lat + ", lon=" + lon);

        double elevation;
        try (InputStream tifStream = getContext().getAssets().open("N053E046/N053E046/ALPSMLC30_N053E046.tif")) {
            elevation = DSMReader.getElevation(lat, lon, tifStream);
            Log.d(TAG, "Mortar elevation: " + elevation + " m");
        } catch (IOException e) {
            Log.e(TAG, "DSM read error: " + e.getMessage());
            elevation = 0.0;
        }

        Marker marker = new Marker(mapView);
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(String.format("Высота: %.1f м", elevation));
        mapView.getOverlays().add(marker);

        Polygon rangeCircle = new Polygon();
        rangeCircle.setPoints(Polygon.pointsAsCircle(geoPoint, MAX_RANGE));
        mapView.getOverlays().add(rangeCircle);

        mortars.add(new Mortar(geoPoint, marker, rangeCircle, elevation));
        mapView.invalidate();

        updateMortarCoordsText();

        Log.d(TAG, "Mortar added: lat=" + lat + ", lon=" + lon + ", elevation=" + elevation + " m");
    }

    private void updateMortarCoordsText() {
        if (mortarCoordsText != null) {
            StringBuilder coordsText = new StringBuilder("Миномёты:\n");
            if (mortars.isEmpty()) {
                coordsText.append("не установлены");
            } else {
                for (int i = 0; i < mortars.size(); i++) {
                    Mortar mortar = mortars.get(i);
                    coordsText.append(String.format("Миномёт %d: Lat=%.6f, Lon=%.6f, Высота=%.1f м\n",
                            i + 1, mortar.point.getLatitude(), mortar.point.getLongitude(), mortar.elevation));
                }
            }
            mortarCoordsText.setText(coordsText.toString());
        }
    }

    private double[] convertToLocalCoordinates(double lat, double lon, GeoPoint mortarPoint) {
        double[] coords = new double[2];
        double mortarLat = mortarPoint.getLatitude();
        double mortarLon = mortarPoint.getLongitude();
        coords[0] = (lon - mortarLon) * 111320 * Math.cos(Math.toRadians(mortarLat));
        coords[1] = (lat - mortarLat) * 111320;
        return coords;
    }

    private double calculateAzimuth(GeoPoint mortar, GeoPoint target) {
        double dLon = Math.toRadians(target.getLongitude() - mortar.getLongitude());
        double lat1 = Math.toRadians(mortar.getLatitude());
        double lat2 = Math.toRadians(target.getLatitude());

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double azimuth = Math.toDegrees(Math.atan2(y, x));
        return (azimuth + 360) % 360;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d(TAG, "Single tap confirmed");
            handleSingleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.d(TAG, "Double tap detected");
            handleDoubleTap(e.getX(), e.getY());
            return true;
        }
    }
}