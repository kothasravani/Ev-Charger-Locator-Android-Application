    package com.example.evchargerlocator_androidapplication;

    import android.Manifest;
    import android.content.Intent;
    import android.content.SharedPreferences;
    import android.content.pm.PackageManager;
    import android.location.Address;
    import android.location.Geocoder;
    import android.location.Location;
    import android.os.Bundle;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.*;
    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AlertDialog;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.ContextCompat;
    import com.example.evchargerlocator_androidapplication.StationDetailsActivity;


    import com.android.volley.Request;
    import com.android.volley.RequestQueue;
    import com.android.volley.toolbox.JsonObjectRequest;
    import com.android.volley.toolbox.Volley;
    import com.google.android.gms.maps.*;
    import com.google.android.gms.maps.model.*;
    import com.google.firebase.database.*;
    import com.google.maps.android.PolyUtil;
    import com.google.maps.android.SphericalUtil;

    import org.json.JSONArray;
    import org.json.JSONException;
    import org.json.JSONObject;

    import java.util.*;

    public class HomePageActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

        private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

        private GoogleMap myMap;
        private RequestQueue requestQueue;
        private DatabaseReference databaseReference;

        private LatLng startLocation, endLocation;
        private double distanceFilter = 5.0;
        private Polyline routePolyline;
        private List<LatLng> routePoints;
        private List<ChargingStation> selectedStations = new ArrayList<>();

        private TextView distanceStat, timeStat, stationStat;
        private boolean filterLevel1 = false, filterLevel2 = false, filterLevel3 = false;
        private Set<String> filterConnectors = new HashSet<>();
        private Set<String> filterNetworks = new HashSet<>();


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_home_page);

            TextView backArrowText = findViewById(R.id.backArrowText);
            backArrowText.setOnClickListener(v -> finish());
            ImageButton saveTripButton = findViewById(R.id.saveTripButton);
            saveTripButton.setOnClickListener(v -> {
                Log.d("SaveTrip", "Save trip button clicked");
                showSaveTripDialog();
            });
            ImageButton filterBtn = findViewById(R.id.filter);
            filterBtn.setOnClickListener(v -> {
                Intent intent = new Intent(HomePageActivity.this, FilterActivity.class);
                intent.putExtra("level1", filterLevel1);
                intent.putExtra("level2", filterLevel2);
                intent.putExtra("level3", filterLevel3);
                intent.putExtra("connectorTypes", filterConnectors.toArray(new String[0]));
                intent.putExtra("networkTesla", filterNetworks.contains("Tesla"));
                intent.putExtra("networkChargePoint", filterNetworks.contains("ChargePoint"));
                intent.putExtra("networkEVgo", filterNetworks.contains("EVgo"));
                intent.putExtra("networkElectrify", filterNetworks.contains("Electrify America"));

                startActivityForResult(intent, 101);
            });
            // Read filter values from Intent or SharedPreferences
            SharedPreferences prefs = getSharedPreferences("FilterPrefs", MODE_PRIVATE);

            boolean level1 = getIntent().getBooleanExtra("level1", prefs.getBoolean("level1", true));
            boolean level2 = getIntent().getBooleanExtra("level2", prefs.getBoolean("level2", true));
            boolean level3 = getIntent().getBooleanExtra("level3", prefs.getBoolean("level3", true));

            String[] connectors = getIntent().getStringArrayExtra("connectorTypes");
            Set<String> defaultConnectors = new HashSet<>(Arrays.asList("Type 1", "Type 2", "CCS", "CHAdeMO"));
            Set<String> currentConnectors = new HashSet<>(connectors != null ? Arrays.asList(connectors) : defaultConnectors);

            boolean netTesla = getIntent().getBooleanExtra("networkTesla", prefs.getBoolean("networkTesla", true));
            boolean netChargePoint = getIntent().getBooleanExtra("networkChargePoint", prefs.getBoolean("networkChargePoint", true));
            boolean netEVgo = getIntent().getBooleanExtra("networkEVgo", prefs.getBoolean("networkEVgo", true));
            boolean netElectrify = getIntent().getBooleanExtra("networkElectrify", prefs.getBoolean("networkElectrify", true));

          //  boolean filtersCustomized = !(level1 && level2 && level3 &&
                //    currentConnectors.containsAll(defaultConnectors) && currentConnectors.size() == defaultConnectors.size() &&
                 //   netTesla && netChargePoint && netEVgo && netElectrify);
            Log.d("FilterDebug", "level1: " + level1);
            Log.d("FilterDebug", "level2: " + level2);
            Log.d("FilterDebug", "level3: " + level3);
            Log.d("FilterDebug", "connectors: " + (connectors != null ? Arrays.toString(connectors) : "null"));
            Log.d("FilterDebug", "Tesla: " + netTesla + ", ChargePoint: " + netChargePoint + ", EVgo: " + netEVgo + ", Electrify: " + netElectrify);

            boolean filtersCustomized = !(level1 && level2 && level3 &&
                    currentConnectors.containsAll(defaultConnectors) &&
                    currentConnectors.size() == defaultConnectors.size() &&
                    netTesla && netChargePoint && netEVgo && netElectrify);

            Log.d("FilterDebug", "filtersCustomized = " + filtersCustomized);


            updateFilterUI(filtersCustomized);


            distanceStat = findViewById(R.id.distanceStat);
            timeStat = findViewById(R.id.timeStat);
            stationStat = findViewById(R.id.stationStat);
            requestQueue = Volley.newRequestQueue(this);
            databaseReference = FirebaseDatabase.getInstance().getReference("ChargingStations");
            TextView batteryStat = findViewById(R.id.batteryStat);
            int batteryPercent = getIntent().getIntExtra("batteryPercent", 50); // default 50%

// Choose battery emoji
            String batteryEmoji;
            if (batteryPercent >= 90) batteryEmoji = "🔋";
            else if (batteryPercent >= 60) batteryEmoji = "🔋";
            else if (batteryPercent >= 30) batteryEmoji = "🪫";
            else batteryEmoji = "❗"; // low battery warning

// Set color based on battery level
            int color;
            if (batteryPercent >= 80) {
                color = ContextCompat.getColor(this, android.R.color.holo_green_light);
            } else if (batteryPercent >= 30) {
                color = ContextCompat.getColor(this, android.R.color.holo_orange_light);
            } else {
                color = ContextCompat.getColor(this, android.R.color.holo_red_light);
            }

            batteryStat.setText(batteryEmoji + " " + batteryPercent + "%");
            batteryStat.setTextColor(color);

            Intent intent = getIntent();
            startLocation = getLatLngFromIntent(intent.getStringExtra("startLocation"));
            endLocation = getLatLngFromIntent(intent.getStringExtra("endLocation"));
            distanceFilter = intent.getDoubleExtra("distanceFilter", 5.0);

            // Apply filters if available
            if (intent.hasExtra("level1") || intent.hasExtra("connectorTypes") || intent.hasExtra("networkTesla")) {
                filterLevel1 = intent.getBooleanExtra("level1", false);
                filterLevel2 = intent.getBooleanExtra("level2", false);
                filterLevel3 = intent.getBooleanExtra("level3", false);

                String[] connectorTypes = intent.getStringArrayExtra("connectorTypes");
                if (connectorTypes != null) filterConnectors.addAll(Arrays.asList(connectorTypes));

                if (intent.getBooleanExtra("networkTesla", false)) filterNetworks.add("Tesla");
                if (intent.getBooleanExtra("networkChargePoint", false))
                    filterNetworks.add("ChargePoint");
                if (intent.getBooleanExtra("networkEVgo", false)) filterNetworks.add("EVgo");
                if (intent.getBooleanExtra("networkElectrify", false))
                    filterNetworks.add("Electrify America");
            }
            // After parsing filters from intent
           // updateFilterUI(false);


            // Restore trip
            if (intent.getBooleanExtra("restoreTrip", false)) {
                selectedStations = intent.getParcelableArrayListExtra("stations");
                updateTripBadge();
                updateStationStat();
            }

            if (startLocation != null && endLocation != null) {
                drawRouteAndEVStations();
            }

            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            if (mapFragment != null) mapFragment.getMapAsync(this);

            // Trip badge click
            FrameLayout tripBadge = findViewById(R.id.tripBadge);
            tripBadge.setOnClickListener(v -> {
                if (startLocation == null || endLocation == null) {
                    Toast.makeText(HomePageActivity.this, "Trip locations not set", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent tripIntent = new Intent(HomePageActivity.this, MyTripActivity.class);
                tripIntent.putParcelableArrayListExtra("stations", new ArrayList<>(selectedStations));
                tripIntent.putExtra("startLocation", startLocation.latitude + "," + startLocation.longitude);
                tripIntent.putExtra("endLocation", endLocation.latitude + "," + endLocation.longitude);

                final String[] fromAddrHolder = {""};
                final String[] toAddrHolder = {""};

                fetchAddressFromLatLng(startLocation, fromAddress -> {
                    fromAddrHolder[0] = fromAddress;

                    fetchAddressFromLatLng(endLocation, toAddress -> {
                        toAddrHolder[0] = toAddress;

                        tripIntent.putExtra("fromAddress", fromAddrHolder[0]);
                        tripIntent.putExtra("toAddress", toAddrHolder[0]);

                        Log.d("TripBadge", "Launching MyTripActivity:");
                        Log.d("TripBadge", "From: " + fromAddrHolder[0]);
                        Log.d("TripBadge", "To: " + toAddrHolder[0]);
                        Log.d("TripBadge", "Stations: " + selectedStations.size());

                        startActivity(tripIntent);
                    });
                });
            });


        }




        @Override
        public void onMapReady(@NonNull GoogleMap googleMap) {
            myMap = googleMap;
            myMap.getUiSettings().setZoomControlsEnabled(true);
            myMap.setOnMarkerClickListener(this);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                myMap.setMyLocationEnabled(true);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }

            if (startLocation != null && endLocation != null) {
                drawRouteAndEVStations();

                // ✅ Auto-zoom to fit route and markers
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                boundsBuilder.include(startLocation);
                boundsBuilder.include(endLocation);

                if (selectedStations != null && !selectedStations.isEmpty()) {
                    for (ChargingStation station : selectedStations) {
                        boundsBuilder.include(new LatLng(station.getLatitude(), station.getLongitude()));
                    }
                }

                LatLngBounds bounds = boundsBuilder.build();

                myMap.setOnMapLoadedCallback(() -> {
                    int padding = 100; // padding in pixels
                    myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                });
            }
        }

        private void drawRouteAndEVStations() {
            String url = "https://maps.googleapis.com/maps/api/directions/json?"
                    + "origin=" + startLocation.latitude + "," + startLocation.longitude
                    + "&destination=" + endLocation.latitude + "," + endLocation.longitude
                    + "&key=" + getString(R.string.google_maps_key);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            JSONArray routes = response.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String polyline = route.getJSONObject("overview_polyline").getString("points");
                                routePoints = PolyUtil.decode(polyline);

                                if (routePolyline != null) routePolyline.remove();
                                routePolyline = myMap.addPolyline(new PolylineOptions()
                                        .addAll(routePoints)
                                        .width(10)
                                        .color(ContextCompat.getColor(this, R.color.black)));

                                JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
                                distanceStat.setText("🚗 " + leg.getJSONObject("distance").getString("text"));
                                timeStat.setText("⏱ " + leg.getJSONObject("duration").getString("text"));

                                fetchEVStationsAlongRoute(routePoints);
                            }
                        } catch (JSONException e) {
                            Toast.makeText(this, "Error parsing route", Toast.LENGTH_SHORT).show();
                        }
                    },
                    error -> Toast.makeText(this, "Failed to fetch route", Toast.LENGTH_SHORT).show());

            requestQueue.add(request);
        }

        private void fetchEVStationsAlongRoute(List<LatLng> routePoints) {
            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int count = 0;
                    for (DataSnapshot stationSnap : snapshot.getChildren()) {
                        ChargingStation station = stationSnap.getValue(ChargingStation.class);
                        if (station == null) continue;

                        LatLng loc = new LatLng(station.getLatitude(), station.getLongitude());
                        boolean isOnRoute = PolyUtil.isLocationOnPath(loc, routePoints, true, distanceFilter * 1609.34);

                        if (isOnRoute) {
                            Intent intent = getIntent();
                            String vehicleType = intent.getStringExtra("vehicleType");
                            int batteryPercent = intent.getIntExtra("batteryPercent", 100);
                            VehicleModel vehicle = VehicleData.getVehicleByName(vehicleType);
                            double usableRangeKm = (batteryPercent / 100.0) * vehicle.maxRangeKm;

                            float[] result = new float[1];
                            Location.distanceBetween(startLocation.latitude, startLocation.longitude,
                                    station.getLatitude(), station.getLongitude(), result);
                            float distanceFromStartKm = result[0] / 1000f;

                            if (distanceFromStartKm >= usableRangeKm) {

                                // Apply filters only if intent contains any filter keys
                                boolean shouldApplyFilter = intent.hasExtra("level1") || intent.hasExtra("connectorTypes") || intent.hasExtra("networkTesla");

                                boolean matchesLevel = true;
                                boolean matchesConnector = true;
                                boolean matchesNetwork = true;

                                if (shouldApplyFilter) {
                                    String level = station.getChargingLevel().toLowerCase();
                                    String connector = station.getConnectorType();
                                    String network = station.getNetwork();

                                    matchesLevel =
                                            (filterLevel1 && level.contains("level 1")) ||
                                                    (filterLevel2 && level.contains("level 2")) ||
                                                    (filterLevel3 && level.contains("dc"));

                                    matchesConnector = filterConnectors.isEmpty() || filterConnectors.contains(connector);
                                    matchesNetwork = filterNetworks.isEmpty() || filterNetworks.contains(network);
                                }

                                if (matchesLevel && matchesConnector && matchesNetwork) {
                                    // Set marker color
                                    float markerColor;
                                    String level = station.getChargingLevel().toLowerCase();
                                    switch (level) {
                                        case "level 1":
                                            markerColor = BitmapDescriptorFactory.HUE_YELLOW;
                                            break;
                                        case "level 2":
                                            markerColor = BitmapDescriptorFactory.HUE_GREEN;
                                            break;
                                        case "dc fast":
                                            markerColor = BitmapDescriptorFactory.HUE_BLUE;
                                            break;
                                        default:
                                            markerColor = BitmapDescriptorFactory.HUE_RED;
                                            break;
                                    }

                                    myMap.addMarker(new MarkerOptions()
                                            .position(loc)
                                            .title(station.getName())
                                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

                                    count++;
                                } else {
                                    Log.d("FILTER", "Skipping " + station.getName() + " due to filter mismatch.");
                                }

                            } else {
                                Log.d("SMART_PLAN", "Skipping " + station.getName() + " — " + distanceFromStartKm + " km from start");
                            }
                        }
                    }

                    stationStat.setText("📍 " + count);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(HomePageActivity.this, "Database error", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public boolean onMarkerClick(@NonNull Marker marker) {
            Log.d("MARKER_CLICK", "Marker clicked: " + marker.getTitle());

            if (marker.getTitle() == null) return false;

            LatLng position = marker.getPosition();
            String stationName = marker.getTitle();

            DatabaseReference stationRef = FirebaseDatabase.getInstance().getReference("ChargingStations");

            stationRef.orderByChild("name").equalTo(stationName).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    final ChargingStation[] clickedStationHolder = new ChargingStation[1];  // ✅ Use array to bypass final restriction

                    for (DataSnapshot snap : snapshot.getChildren()) {
                        ChargingStation s = snap.getValue(ChargingStation.class);
                        if (s != null &&
                                s.getLatitude() == position.latitude &&
                                s.getLongitude() == position.longitude) {
                            clickedStationHolder[0] = s;
                            break;
                        }
                    }

                    ChargingStation clickedStation = clickedStationHolder[0];

                    if (clickedStation == null) {
                        Toast.makeText(HomePageActivity.this, "Station data not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Compute distance & time
                    LatLng referencePoint = selectedStations.isEmpty()
                            ? startLocation
                            : new LatLng(
                            selectedStations.get(selectedStations.size() - 1).getLatitude(),
                            selectedStations.get(selectedStations.size() - 1).getLongitude()
                    );

                    double distance = SphericalUtil.computeDistanceBetween(referencePoint, position) / 1609.34;
                    String distanceText = String.format(Locale.getDefault(), "%.2f Mi", distance);
                    String driveTimeText = estimateDriveTime(distance);

                    // Show popup
                    findViewById(R.id.stationPopup).setVisibility(View.VISIBLE);
                    String label = selectedStations.isEmpty() ? "Distance from starting point: " : "Distance from previous point: ";
                    ((TextView) findViewById(R.id.stationDistanceText)).setText(label + distanceText);

                    ((TextView) findViewById(R.id.popupStationDistance)).setText(distanceText);
                    ((TextView) findViewById(R.id.popupStationName)).setText(clickedStation.getName());
                    ((TextView) findViewById(R.id.popupStationDetails)).setText(
                            clickedStation.getConnectorType() + " • " + clickedStation.getNetwork()
                    );

                    // Charging level & pricing
                    ((TextView) findViewById(R.id.popupStationChargingLevel)).setText(
                            "Charging Level: " + (clickedStation.getChargingLevel() != null ? clickedStation.getChargingLevel() : "N/A")
                    );

                    String priceText = "$0.40/kWh";
                    if (clickedStation.getPricing() > 0) {
                        priceText = String.format(Locale.getDefault(), "$%.2f/kWh", clickedStation.getPricing());
                    }
                    ((TextView) findViewById(R.id.popupStationPrice)).setText("Price: " + priceText);

                    // Trip button logic
                    Button tripToggleBtn = findViewById(R.id.addToTripButton);
                    updateTripButtonState(tripToggleBtn, clickedStation);
                    tripToggleBtn.setOnClickListener(v -> {
                        if (isStationInTrip(clickedStation)) {
                            selectedStations.removeIf(s ->
                                    s.getLatitude() == clickedStation.getLatitude() &&
                                            s.getLongitude() == clickedStation.getLongitude() &&
                                            s.getName().equals(clickedStation.getName())
                            );
                            Toast.makeText(HomePageActivity.this, "Removed from trip", Toast.LENGTH_SHORT).show();
                        } else {
                            selectedStations.add(clickedStation);
                            Toast.makeText(HomePageActivity.this, "Added to trip", Toast.LENGTH_SHORT).show();
                        }

                        updateTripBadge();
                        updateStationStat();
                        updateTripButtonState(tripToggleBtn, clickedStation);
                    });

                    // View details button
                    Button detailsBtn = findViewById(R.id.viewStationDetails);
                    detailsBtn.setOnClickListener(v -> {
                        Intent detailsIntent = new Intent(HomePageActivity.this, StationDetailsActivity.class);
                        detailsIntent.putExtra("stationName", clickedStation.getName());
                        detailsIntent.putExtra("paymentMethods", "Google Pay, PayPal");
                        detailsIntent.putExtra("plugType", clickedStation.getConnectorType());
                        detailsIntent.putExtra("plugAvailability", clickedStation.getAvailability());
                        detailsIntent.putExtra("stationLevel", clickedStation.getChargingLevel());
                        detailsIntent.putExtra("price", clickedStation.getPricing());
                        detailsIntent.putExtra("distance", distanceText);
                        detailsIntent.putExtra("duration", driveTimeText);
                        detailsIntent.putExtra("latitude", clickedStation.getLatitude());
                        detailsIntent.putExtra("longitude", clickedStation.getLongitude());
                        startActivity(detailsIntent);
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(HomePageActivity.this, "Failed to load station data", Toast.LENGTH_SHORT).show();
                }
            });

            return true;
        }


        private String estimateDriveTime(double miles) {
            double avgSpeed = 30.0; // mph
            double minutes = (miles / avgSpeed) * 60;
            return String.format(Locale.getDefault(), "%.0f min", minutes);
        }

        private void fetchAddressFromLatLng(LatLng latLng, AddressCallback callback) {
            String apiKey = getString(R.string.google_maps_key);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?latlng="
                    + latLng.latitude + "," + latLng.longitude + "&key=" + apiKey;

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        try {
                            JSONArray results = response.getJSONArray("results");
                            if (results.length() > 0) {
                                String address = results.getJSONObject(0).getString("formatted_address");
                                callback.onAddressFound(address);
                            } else {
                                callback.onAddressFound(latLng.latitude + ", " + latLng.longitude);
                            }
                        } catch (JSONException e) {
                            callback.onAddressFound(latLng.latitude + ", " + latLng.longitude);
                        }
                    },
                    error -> callback.onAddressFound(latLng.latitude + ", " + latLng.longitude)
            );

            requestQueue.add(request);
        }

        interface AddressCallback {
            void onAddressFound(String address);
        }


        private void updateTripBadge() {
            ((TextView) findViewById(R.id.badgeCount)).setText(String.valueOf(selectedStations.size()));
        }

        private void updateFilterUI(boolean filtersCustomized) {
            LinearLayout filterStatusContainer = findViewById(R.id.filterStatusContainer);
            View filterDot = findViewById(R.id.filterDot);

            if (filtersCustomized) {
                if (filterStatusContainer != null) filterStatusContainer.setVisibility(View.VISIBLE);
                if (filterDot != null) filterDot.setVisibility(View.VISIBLE);
            } else {
                if (filterStatusContainer != null) filterStatusContainer.setVisibility(View.GONE);
                if (filterDot != null) filterDot.setVisibility(View.GONE);
            }
        }




        private void updateStationStat() {
            ((TextView) findViewById(R.id.stationStat)).setText("📍 " + selectedStations.size());
        }

        private boolean isStationInTrip(ChargingStation station) {
            for (ChargingStation s : selectedStations) {
                if (s.getLatitude() == station.getLatitude()
                        && s.getLongitude() == station.getLongitude()
                        && s.getName().equals(station.getName())) {
                    return true;
                }
            }
            return false;
        }

        private void updateTripButtonState(Button button, ChargingStation station) {
            if (isStationInTrip(station)) {
                button.setText("Remove from Trip");
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_remove_circle, 0, 0, 0);
            } else {
                button.setText("Add to Trip");
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0);
            }
        }

        private LatLng getLatLngFromIntent(String str) {
            if (str == null || !str.contains(",")) return null;
            String[] parts = str.split(",");
            return new LatLng(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        }

        private void showSaveTripDialog() {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_trip, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView).setCancelable(false).create();

            EditText tripNameInput = dialogView.findViewById(R.id.tripNameInput);
            Button cancelButton = dialogView.findViewById(R.id.cancelButton);
            Button saveButton = dialogView.findViewById(R.id.saveButton);

            cancelButton.setOnClickListener(v -> dialog.dismiss());

            saveButton.setOnClickListener(v -> {
                String tripName = tripNameInput.getText().toString().trim();
                if (tripName.isEmpty()) {
                    tripNameInput.setError("Trip name is required");
                    return;
                }

                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    String fromAddress = getAddressFromLatLng(startLocation, geocoder);
                    String toAddress = getAddressFromLatLng(endLocation, geocoder);

                    SavedTrip trip = new SavedTrip(tripName, fromAddress, toAddress,
                            startLocation.latitude + "," + startLocation.longitude,
                            endLocation.latitude + "," + endLocation.longitude,
                            selectedStations);

                    DatabaseReference tripRef = FirebaseDatabase.getInstance().getReference("SavedTrips").push();
                    trip.setId(tripRef.getKey()); // 🔐 Save ID to the object
                    tripRef.setValue(trip)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Trip saved!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to save trip", Toast.LENGTH_SHORT).show());

                } catch (Exception e) {
                    Toast.makeText(this, "Error saving trip", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        }

        private String getAddressFromLatLng(LatLng latLng, Geocoder geocoder) {
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (!addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
            } catch (Exception ignored) {
            }
            return latLng.latitude + ", " + latLng.longitude;
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
                // Retrieve filter values
                boolean level1 = data.getBooleanExtra("level1", true);
                boolean level2 = data.getBooleanExtra("level2", true);
                boolean level3 = data.getBooleanExtra("level3", true);
                String[] connectors = data.getStringArrayExtra("connectorTypes");

                boolean netTesla = data.getBooleanExtra("networkTesla", true);
                boolean netChargePoint = data.getBooleanExtra("networkChargePoint", true);
                boolean netEVgo = data.getBooleanExtra("networkEVgo", true);
                boolean netElectrify = data.getBooleanExtra("networkElectrify", true);

                // ✅ Determine if filters are customized (i.e., not all default values)
                boolean filtersCustomized = !(level1 && level2 && level3 &&
                        connectors != null && connectors.length == 4 &&  // 4 means all selected
                        Arrays.asList(connectors).containsAll(Arrays.asList("Type 1", "Type 2", "CCS", "CHAdeMO")) &&
                        netTesla && netChargePoint && netEVgo && netElectrify);

                // ✅ Update UI with status and green dot if filters are applied
                updateFilterUI(filtersCustomized);

                // ✅ Relaunch HomePageActivity with filter extras applied
                Intent refreshed = new Intent(HomePageActivity.this, HomePageActivity.class);
                refreshed.putExtra("startLocation", startLocation.latitude + "," + startLocation.longitude);
                refreshed.putExtra("endLocation", endLocation.latitude + "," + endLocation.longitude);
                refreshed.putExtra("vehicleType", getIntent().getStringExtra("vehicleType"));
                refreshed.putExtra("batteryPercent", getIntent().getIntExtra("batteryPercent", 100));
                refreshed.putExtra("distanceFilter", distanceFilter);

                refreshed.putExtra("level1", level1);
                refreshed.putExtra("level2", level2);
                refreshed.putExtra("level3", level3);
                refreshed.putExtra("connectorTypes", connectors);
                refreshed.putExtra("networkTesla", netTesla);
                refreshed.putExtra("networkChargePoint", netChargePoint);
                refreshed.putExtra("networkEVgo", netEVgo);
                refreshed.putExtra("networkElectrify", netElectrify);
                refreshed.putExtra("filtersCustomized", filtersCustomized); // for persistence if needed

                startActivity(refreshed);
                finish();
            }
        }


    }