/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.google.maps.dataviz;

import com.example.google.maps.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FragmentActivity {
    private static final int POLYLINE_HUE = 360; // 0-360
    private static final float POLYLINE_SATURATION = 1; // 0-1
    private static final float POLYLINE_VALUE = 1; // 0-1
    private static final int POLYLINE_ALPHA = 198; // 0-255
    private static final float POLYLINE_WIDTH = 8;

    private static final float CAMERA_OBLIQUE_ZOOM = 17;
    private static final float CAMERA_OBLIQUE_TILT = 60;

    private static final String ROUTE = "oeveF|aejV}D`@QyC_AyNe@{HFWx@uAzAeCCUoAeSaA{N_@uF?c@JQJGNDj@t@PNPBb@Cd@QPMHSLKXQn@Ub@a@N[@YC[KYYK_@@c@V";

    private static final int MARKER_HEIGHT = 150;
    private static final int MARKER_WIDTH = 5;
    
    private GoogleMap mMap;
    private List<LatLng> mRoute;
    private Paint mPaint;

    private float mInitZoom;
    private LatLng mInitPosition;
    private ValueAnimator mAnimator;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // The gradient for colouring the markers.
        Shader shader = new LinearGradient(0, 0, 0, MARKER_HEIGHT - 1, new int[] {
                Color.BLUE, Color.RED
        }, null, TileMode.CLAMP);
        mPaint = new Paint();
        mPaint.setShader(shader);
        
        mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMap();

        if (mMap != null) {
            setUpMap();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMap == null) {
            return true;
        }

        switch (item.getItemId()) {
            // Rotate the camera 360 deg.
            case R.id.action_rotate:
                if (mAnimator != null && mAnimator.isRunning()) {
                    mAnimator.cancel();
                } else {
                    mAnimator = ValueAnimator.ofFloat(0, 360);
                    mAnimator.setDuration(3000);
                    mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            float bearing = (Float) animation.getAnimatedValue();
                            CameraPosition pos = CameraPosition.builder(mMap.getCameraPosition())
                                    .bearing(bearing)
                                    .build();
                            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
                        }
                    });
                    mAnimator.start();
                }
                
                return true;
                
            // Chance the camera perspective.
            case R.id.action_perspective:
                CameraPosition currentPosition = mMap.getCameraPosition();
                CameraPosition newPosition;
                if (currentPosition.zoom == CAMERA_OBLIQUE_ZOOM
                        && currentPosition.tilt == CAMERA_OBLIQUE_TILT) {
                    newPosition = new CameraPosition.Builder()
                            .tilt(0).zoom(mInitZoom)
                            .bearing(0)
                            .target(mInitPosition).build();
                } else {
                    newPosition = new CameraPosition.Builder()
                            .tilt(CAMERA_OBLIQUE_TILT)
                            .zoom(CAMERA_OBLIQUE_ZOOM)
                            .bearing(currentPosition.bearing)
                            .target(currentPosition.target).build();
                }
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(newPosition));

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setUpMap() {
        mRoute = PolyUtil.decode(ROUTE);

        // Create a polyline for the route.
        mMap.addPolyline(new PolylineOptions()
                .addAll(mRoute)
                .color(Color.HSVToColor(POLYLINE_ALPHA,
                        new float[] {
                                POLYLINE_HUE, POLYLINE_SATURATION, POLYLINE_VALUE
                        }))
                .width(POLYLINE_WIDTH));

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition position) {
                mMap.setOnCameraChangeListener(null);
                
                // Move the camera to show the entire route.
                LatLngBounds.Builder builder = LatLngBounds.builder();
                for (LatLng coords : mRoute) {
                    builder.include(coords);
                }
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                
                mInitZoom = mMap.getCameraPosition().zoom;
                mInitPosition = mMap.getCameraPosition().target;
            }
        });
        
        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                addDataToMap();
            }
        });
    }

    private void addDataToMap() {
        ArrayList<LatLng> coords = new ArrayList<LatLng>();
        ArrayList<Double> samples = new ArrayList<Double>();
        double minElevation = Integer.MAX_VALUE;
        double maxElevation = Integer.MIN_VALUE;

        try {
            Resources res = getResources();
            InputStream istream = res.openRawResource(R.raw.elevation);
            byte[] buff = new byte[istream.available()];
            istream.read(buff);
            istream.close();

            String json = new String(buff);
            JSONArray jsonArray = new JSONArray(json);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sample = jsonArray.getJSONObject(i);

                double elevation = sample.getDouble("elevation");
                minElevation = elevation < minElevation ? elevation : minElevation;
                maxElevation = elevation > maxElevation ? elevation : maxElevation;

                JSONObject location = sample.getJSONObject("location");
                LatLng position = new LatLng(location.getDouble("lat"), location.getDouble("lng"));

                coords.add(position);
                samples.add(elevation);
            }
        } catch (IOException e) {
            Log.e(this.getClass().getName(), "Error accessing elevation data file.");
            return;
        } catch (JSONException e) {
            Log.e(this.getClass().getName(), "Error processing elevation data (JSON).");
            return;
        }

        for (int i = 0; i < coords.size(); i++) {
            double elevation = samples.get(i);
            int height = (int) (elevation / maxElevation * MARKER_HEIGHT);

            Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = Bitmap.createBitmap(MARKER_WIDTH, height, conf);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawPaint(mPaint);

            // Invert the bitmap (top red, bottom blue).
            Matrix matrix = new Matrix();
            matrix.preScale(1, -1);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

            LatLng position = coords.get(i);
            BitmapDescriptor bitmapDesc = BitmapDescriptorFactory.fromBitmap(bitmap);
            mMap.addMarker(new MarkerOptions().position(position).icon(bitmapDesc)
                    .alpha(.8f).title(new DecimalFormat("#.## meters").format(elevation)));
        }
    }
}
