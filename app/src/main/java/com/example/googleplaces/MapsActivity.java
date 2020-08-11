package com.example.googleplaces;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.mancj.materialsearchbar.MaterialSearchBar;
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap myGoogleMap;
    private PlacesClient placesClient;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private MaterialSearchBar materialSearchBar;
    private Location lastKnownLocation;
    private LocationCallback locationCallback;
    private static final int LOC_PERMISSION_REQ_CODE = 23;
    private static final int LOC_ENABLED_CHECK_CODE = 24;
    private float DEFAULT_ZOOM = 18;
    private LocationRequest locationRequest;
    private AutocompleteSessionToken autocompleteSessionToken;

    private List<String> suggestionsList;
    private List<AutocompletePrediction> predictionList;

    private Button submitBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        materialSearchBar = findViewById(R.id.search_bar);
        submitBtn = findViewById(R.id.submit_location_tbn);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);
        String api_key = getString(R.string.goole_api_key);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), api_key);
        }
        placesClient = Places.createClient(this);
        autocompleteSessionToken = AutocompleteSessionToken.newInstance();

        setSearchBarListener();
        checkLocationPermissions();
    }


    private void checkLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOC_PERMISSION_REQ_CODE);

            } else {
                initializeMap();
            }
        } else {
            initializeMap();
        }
    }


    private void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        myGoogleMap = googleMap;
        myGoogleMap.setMyLocationEnabled(true);
        myGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
        myGoogleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                if (materialSearchBar.isSuggestionsVisible())
                    materialSearchBar.clearSuggestions();

                if (materialSearchBar.isSearchEnabled())
                    materialSearchBar.disableSearch();

                return false;
            }
        });

        checkLocationSettings();
    }

    private void checkLocationSettings() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder locationSettingsBuilder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        SettingsClient settingsClient = LocationServices.getSettingsClient(MapsActivity.this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(locationSettingsBuilder.build());
        task.addOnSuccessListener(MapsActivity.this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                getDeviceLocation();

            }
        });

        task.addOnFailureListener(MapsActivity.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                    try {
                        resolvableApiException.startResolutionForResult(MapsActivity.this, LOC_ENABLED_CHECK_CODE);
                    } catch (IntentSender.SendIntentException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOC_PERMISSION_REQ_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeMap();
                }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOC_ENABLED_CHECK_CODE) {
            if (resultCode == RESULT_OK) {
                getDeviceLocation();
            }
        }
    }

    private void getDeviceLocation() {
        fusedLocationProviderClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                LatLng newLoc = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                myGoogleMap.addMarker(new MarkerOptions().position(newLoc).title("Marker"));
                                myGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLoc, DEFAULT_ZOOM));

                            } else {
                                locationRequest = LocationRequest.create();
                                locationRequest.setInterval(10000);
                                locationRequest.setFastestInterval(5000);
                                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                                locationCallback = new LocationCallback() {

                                    @Override
                                    public void onLocationResult(LocationResult locationResult) {
                                        super.onLocationResult(locationResult);
                                        if (locationResult != null) {
                                            lastKnownLocation = locationResult.getLastLocation();
                                            Log.d("KNOWNLOCTWO", lastKnownLocation.toString());
                                            LatLng newLoc = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                            myGoogleMap.addMarker(new MarkerOptions().position(newLoc).title("Marker"));
                                            myGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLoc, DEFAULT_ZOOM));
                                            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
                                        }
                                    }
                                };
                                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);

                            }
                        }
                    }
                });
    }

    private void setSearchBarListener() {
        materialSearchBar.setOnSearchActionListener(new MaterialSearchBar.OnSearchActionListener() {
            @Override
            public void onSearchStateChanged(boolean enabled) {

            }

            @Override
            public void onSearchConfirmed(CharSequence text) {
                startSearch(text.toString(), true, null, true);

            }

            @Override
            public void onButtonClicked(int buttonCode) {
                if (buttonCode == MaterialSearchBar.BUTTON_NAVIGATION) {

                } else if (buttonCode == MaterialSearchBar.BUTTON_BACK) {
                    materialSearchBar.disableSearch();

                } else if (buttonCode == MaterialSearchBar.BUTTON_SPEECH) {

                }

            }
        });

        materialSearchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                final FindAutocompletePredictionsRequest predictionRequest = FindAutocompletePredictionsRequest.builder()
                        .setTypeFilter(TypeFilter.ADDRESS)
                        .setSessionToken(autocompleteSessionToken)
                        .setQuery(s.toString())
                        .build();
                placesClient.findAutocompletePredictions(predictionRequest)
                        .addOnCompleteListener(new OnCompleteListener<FindAutocompletePredictionsResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<FindAutocompletePredictionsResponse> task) {
                                if (task.isSuccessful()) {
                                    FindAutocompletePredictionsResponse predictionResponse = task.getResult();
                                    if (predictionResponse != null) {
                                        predictionList = predictionResponse.getAutocompletePredictions();
                                        if (predictionList.isEmpty()) {
                                            Log.d("EMPTY", "PRED");

                                        } else {
                                            suggestionsList = new ArrayList<>();
                                            for (AutocompletePrediction prediction : predictionList) {
                                                suggestionsList.add(prediction.getFullText(null).toString());
                                                Log.d("PRED", prediction.getPlaceId());
                                                Log.d("PRED2", prediction.getFullText(null).toString());

                                            }
                                            runOnUiThread(new Runnable() {

                                                @Override
                                                public void run() {
                                                    materialSearchBar.updateLastSuggestions(suggestionsList);
                                                    if (!materialSearchBar.isSuggestionsVisible()) {
                                                        materialSearchBar.showSuggestionsList();

                                                    }
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        });
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        materialSearchBar.setSuggestionsClickListener(new SuggestionsAdapter.OnItemViewClickListener() {
            @Override
            public void OnItemClickListener(int position, View v) {
                if (position >= predictionList.size()) {
                    return;
                }
                AutocompletePrediction clickedPrediction = predictionList.get(position);
                String selectedSuggestion = materialSearchBar.getLastSuggestions().get(position).toString();
                materialSearchBar.setText(selectedSuggestion);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        materialSearchBar.clearSuggestions();

                    }
                }, 1000);

                clearKeyBoardAndMapPlace(clickedPrediction);
            }

            @Override
            public void OnItemDeleteListener(int position, View v) {

            }
        });
    }

    private void clearKeyBoardAndMapPlace(AutocompletePrediction pred) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(materialSearchBar.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

            String placeId = pred.getPlaceId();
            List<Place.Field> placeField = Arrays.asList(Place.Field.LAT_LNG);
            FetchPlaceRequest fetchPlaceRequest = FetchPlaceRequest.builder(placeId, placeField)
                    .build();
            placesClient.fetchPlace(fetchPlaceRequest).addOnSuccessListener(new OnSuccessListener<FetchPlaceResponse>() {
                @Override
                public void onSuccess(FetchPlaceResponse fetchPlaceResponse) {
                    Place myPlace = fetchPlaceResponse.getPlace();
                    Log.d("MYPLACENAME", myPlace.getName());
                    LatLng placeToMap = myPlace.getLatLng();
                    if (placeToMap != null) {

                        myGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(placeToMap, DEFAULT_ZOOM));
                    }


                }
            })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if (e instanceof ApiException) {
                                ApiException apiException = (ApiException) e;
                                apiException.printStackTrace();
                                int statusCode = apiException.getStatusCode();
                                Log.d("ERRORMSG", e.getMessage());


                            }

                        }
                    });


        }

    }

}
