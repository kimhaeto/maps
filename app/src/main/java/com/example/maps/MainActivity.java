package com.example.maps;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private RequestQueue requestQueue;
    private final String apiKey = "AIzaSyCl5EbxtatiDu8EeGZ_7z8P6n_n1Wa2aEo"; // 실제 API 키로 교체

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);

        EditText editTextLocation = findViewById(R.id.editText_location);
        Button buttonSearch = findViewById(R.id.button_search);

        buttonSearch.setOnClickListener(v -> {
            String locationText = editTextLocation.getText().toString();
            if (!locationText.isEmpty()) {
                searchLocation(locationText);
            } else {
                Toast.makeText(this, "지역명을 입력해주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        enableMyLocation();
        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter()); // ✅ 커스텀 InfoWindow 연결
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng myLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 15));
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    private void searchLocation(String locationText) {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                Uri.encode(locationText) + "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");
                        if (results.length() > 0) {
                            JSONObject location = results.getJSONObject(0)
                                    .getJSONObject("geometry")
                                    .getJSONObject("location");
                            double lat = location.getDouble("lat");
                            double lng = location.getDouble("lng");

                            LatLng latLng = new LatLng(lat, lng);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

                            searchNearbyPsychiatryHospitals(lat, lng);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e("GeocodingError", "API 요청 실패: " + error.getMessage())
        );

        requestQueue.add(request);
    }

    private void searchNearbyPsychiatryHospitals(double lat, double lng) {
        String location = lat + "," + lng;
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + location +
                "&radius=3000" +
                "&keyword=정신과" +
                "&type=hospital" +
                "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray results = response.getJSONArray("results");
                        mMap.clear();

                        for (int i = 0; i < results.length(); i++) {
                            JSONObject hospital = results.getJSONObject(i);
                            String placeId = hospital.getString("place_id");
                            String name = hospital.getString("name");
                            double hospLat = hospital.getJSONObject("geometry").getJSONObject("location").getDouble("lat");
                            double hospLng = hospital.getJSONObject("geometry").getJSONObject("location").getDouble("lng");
                            double rating = hospital.optDouble("rating", 0.0);
                            String vicinity = hospital.optString("vicinity", "주소 없음");

                            fetchPlaceDetails(placeId, name, hospLat, hospLng, rating, vicinity);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e("PlacesError", "병원 검색 실패: " + error.getMessage())
        );

        requestQueue.add(request);
    }

    private void fetchPlaceDetails(String placeId, String name, double lat, double lng,
                                   double rating, String vicinity) {
        String url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=" + placeId +
                "&fields=name,formatted_phone_number,formatted_address" +
                "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONObject result = response.getJSONObject("result");
                        String phone = result.optString("formatted_phone_number", "전화번호 없음");
                        String address = result.optString("formatted_address", vicinity);

                        mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lng))
                                .title(name)
                                .snippet("⭐ 평점: " + rating +
                                        "\n📞 전화: " + phone +
                                        "\n📍 주소: " + address));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Log.e("PlaceDetailsError", "상세 정보 가져오기 실패: " + error.getMessage())
        );

        requestQueue.add(request);
    }

    class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        private final View mWindow;

        CustomInfoWindowAdapter() {
            mWindow = getLayoutInflater().inflate(R.layout.custom_info_window, null);
        }

        private void renderWindowText(Marker marker, View view) {
            String title = marker.getTitle();
            String snippet = marker.getSnippet();

            TextView titleView = view.findViewById(R.id.info_title);
            TextView ratingView = view.findViewById(R.id.info_rating);
            TextView phoneView = view.findViewById(R.id.info_phone);
            TextView addressView = view.findViewById(R.id.info_address);

            titleView.setText(title);

            if (snippet != null) {
                String[] lines = snippet.split("\n");
                ratingView.setText(lines.length > 0 ? lines[0] : "");
                phoneView.setText(lines.length > 1 ? lines[1] : "");
                addressView.setText(lines.length > 2 ? lines[2] : "");
            }
        }

        @Override
        public View getInfoWindow(Marker marker) {
            renderWindowText(marker, mWindow);
            return mWindow;
        }

        @Override
        public View getInfoContents(Marker marker) {
            return null;
        }
    }
}
