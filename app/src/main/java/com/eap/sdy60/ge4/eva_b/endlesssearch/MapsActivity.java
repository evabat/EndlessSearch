package com.eap.sdy60.ge4.eva_b.endlesssearch;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public static ArrayList<LatLng> mRandomList = new ArrayList<LatLng>();
    public static ArrayList<LatLng> mSnapList = new ArrayList<LatLng>();
    public ProgressDialog mLoadItemsDialog;
    TinyDB tinydb;
    GoogleMap mMap;
    SupportMapFragment mapFragment;
    LocationRequest mLocationRequest;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    FusedLocationProviderClient mFusedLocationClient;
    String mKeyEntry;
    ArrayList<Endless> endlessList = createEntities(new ArrayList<Endless>());
    ArrayList<Marker> markerList = new ArrayList<Marker>();
    // Dialogs
    AlertDialog endlessDialog;
    AlertDialog storyDialog;
    // Firebase database
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference mDb = database.getReference().child("adventures");
    // Buttons
    Button startBtn;
    Button searchBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        startBtn = findViewById(R.id.btnStart);
        searchBtn = findViewById(R.id.btnSearch);
        searchBtn.setVisibility(View.GONE);
        startBtn.setVisibility(View.GONE);
        mSnapList.clear();
        mRandomList.clear();

        final Context context = this;
        tinydb = new TinyDB(this);
        FirebaseApp.initializeApp(this);

        // Click event listener for start button
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mLoadItemsDialog = new ProgressDialog(context);
                mLoadItemsDialog.setTitle("Fetching the realm");
                mLoadItemsDialog.setMessage("Please wait!");
                mLoadItemsDialog.setCancelable(false); // disable dismiss by tapping outside of the dialog
                mLoadItemsDialog.setIndeterminate(true);
                mLoadItemsDialog.show();
                while (mRandomList.size() < 13) {
                    LatLng randomPos = getRandomLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude(), 300);
                    mRandomList.add(randomPos);
                }
                new DownloadTask().execute(getHttpUrl());
            }
        });

        // Click event listener for search button
        searchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mLastLocation != null && mKeyEntry != null) {
                    DatabaseReference ref = mDb.child(mKeyEntry);
                    ref.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // Set an initial value for points
                            // It is minus 20 because we do not want the points of the starting object
                            int points = -20;
                            // Set an initial value for defining the order
                            // It is minus 2 because we do not want the value of the starting object
                            int position = -1;
                            // An initial loop for deciding the acquired objects.
                            // A counter 'j' is used as a helper
                            // We need that initial loop in order to get a sum of the acquired objects
                            int j = 0;
                            for (DataSnapshot datsn : dataSnapshot.getChildren()) {
                                if (!Boolean.parseBoolean(datsn.child("acquired").getValue().toString())) {
                                    j++;
                                } else {
                                    position += 1;
                                    points += 20;
                                }
                            }
                            // Define current location
                            Location userLoc = new Location(LocationManager.GPS_PROVIDER);
                            userLoc.setLatitude(mLastLocation.getLatitude());
                            userLoc.setLongitude(mLastLocation.getLongitude());
                            Boolean somethingFound = false;
                            Boolean allFound = false;
                            // Another loop for
                            for (DataSnapshot ds : dataSnapshot.getChildren()) {
                                Location objLoc = new Location(LocationManager.GPS_PROVIDER);
                                objLoc.setLatitude(Double.parseDouble(ds.child("latLng").child("latitude").getValue().toString()));
                                objLoc.setLongitude(Double.parseDouble(ds.child("latLng").child("longitude").getValue().toString()));

                                float distance = userLoc.distanceTo(objLoc);

                                if (distance < 11 && !Boolean.parseBoolean(ds.child("acquired").getValue().toString()) && ds.child("type").getValue().toString().equals("clue")) {
                                    somethingFound = true;
                                    String currKey = ds.getKey().toString();
                                    ds.child("acquired").getRef().setValue(true);
                                    ds.child("date").getRef().setValue(ServerValue.TIMESTAMP);
                                    ds.child("order").getRef().setValue(position + 1);
                                    String name = ds.child("object").getValue().toString();
                                    String font = new String();
                                    points += 20;
                                    if (j - 1 == 0) {
                                        allFound = true;
                                    } else {
                                        allFound = false;
                                    }
                                    // Get the appropriate variables from the static "Endless List"
                                    for (Endless endless : endlessList) {
                                        if (endless.getProperty("name").equals(name)) {
                                            font = endless.getProperty("font");
                                        }
                                    }
                                    // If something is found, show the according dialog
                                    showEndlessDialog(name, font, allFound, points);
                                    for (Marker marker : markerList) {
                                        if (marker.getTag() != null && marker.getTag().toString().equals(currKey)) {
                                            marker.remove();
                                        }
                                    }
                                }
                            }
                            //Log.d("markerlist", markerList.toString());

                            if (!somethingFound && !allFound) {
                                Toast toast = Toast.makeText(getApplicationContext(), "Nothing found (yet)!", Toast.LENGTH_LONG);
                                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                                toast.show();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            //handle databaseError
                        }
                    });

                }
            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        mKeyEntry = tinydb.getString("dbKey");
        if (mKeyEntry != null) {
            putMarkers(tinydb.getString("dbKey"));
        }
    }

    public void onResume() {
        super.onResume();
        mKeyEntry = tinydb.getString("dbKey");
        if (mKeyEntry != null) {
            putMarkers(tinydb.getString("dbKey"));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mKeyEntry = tinydb.getString("dbKey");
        if (mKeyEntry != null) {
            putMarkers(mKeyEntry);
        }
        //Stop location updates when Activity is no longer active
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        if (endlessDialog != null && endlessDialog.isShowing()) {
            endlessDialog.dismiss();
        }
        if (storyDialog != null && storyDialog.isShowing()) {
            storyDialog.dismiss();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (endlessDialog != null && endlessDialog.isShowing()) {
            endlessDialog.dismiss();
        }
        if (storyDialog != null && storyDialog.isShowing()) {
            storyDialog.dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mKeyEntry = null;
        mMap.clear();
        if (endlessDialog != null && endlessDialog.isShowing()) {
            endlessDialog.dismiss();
        }
        if (storyDialog != null && storyDialog.isShowing()) {
            storyDialog.dismiss();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(120000); // two minutes interval
        mLocationRequest.setFastestInterval(60000); // one minutes fast interval
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //Location Permission already granted
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                mMap.setMyLocationEnabled(true);
            } else {
                //Request Location Permission
                checkLocationPermission();
            }
        } else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mMap.setMyLocationEnabled(true);
        }
    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        // After getting the latest location. save it and re-zoom the map
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                Log.i("MapsActivity", "Location: " + location.getLatitude() + " " + location.getLongitude());
                mLastLocation = location;
                if (mCurrLocationMarker != null) {
                    mCurrLocationMarker.remove();
                }
                //Place current location marker
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                //move map camera
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
            }
        }
    };

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Location Permission Needed");
                alert.setMessage("This app needs the Location permission, please accept to use location functionality");
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Prompt the user once explanation has been shown
                        ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
                    }
                }).create().show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }

    }


    // Get a random location within a radius of 300m from the starting point
    public static LatLng getRandomLocation(double y0, double x0, int radius) {
        Random random = new Random();

        // Convert radius from meters to degrees
        double radiusInDegrees = radius / 111000f;

        double u = random.nextDouble();
        double v = random.nextDouble();
        double w = radiusInDegrees * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        double x = w * Math.cos(t);
        double y = w * Math.sin(t);

        // Adjust the x-coordinate for the shrinking of the east-west distances
        double new_x = x / Math.cos(y0);

        double foundLongitude = new_x + x0;
        double foundLatitude = y + y0;
        return new LatLng(foundLatitude, foundLongitude);
    }

    // The actual http call to google maps' "Snap to Road" API
    public static String getHttpUrl() {

        return "https://roads.googleapis.com/v1/snapToRoads?path=" + mRandomList.get(0).latitude + "," + mRandomList.get(0).longitude + "|" + mRandomList.get(1).latitude + "," + mRandomList.get(1).longitude + "|" + mRandomList.get(2).latitude + "," + mRandomList.get(2).longitude + "|" + mRandomList.get(3).latitude + "," + mRandomList.get(3).longitude + "|" + mRandomList.get(4).latitude + "," + mRandomList.get(4).longitude + "|" + mRandomList.get(5).latitude + "," + mRandomList.get(5).longitude + "|" + mRandomList.get(6).latitude + "," + mRandomList.get(6).longitude + "&interpolate=false&key=AIzaSyA6hZezhWsf1X-VnCHj-M3pkkKWCPUOs6w";
    }

    public void addRandomMarkers(final ArrayList<LatLng> snapList) {
        mDb.push().setValue("adventure", new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                mKeyEntry = databaseReference.getKey();
                markerList.clear();
                Map mAdventure = new HashMap();
                mAdventure.put("object", "compass");
                mAdventure.put("date", ServerValue.TIMESTAMP);
                mAdventure.put("order", 0);
                mAdventure.put("latLng", new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                mAdventure.put("type", "startingPoint");
                mAdventure.put("acquired", true);
                mDb.child(mKeyEntry).push().setValue(mAdventure);
                tinydb.putString("dbKey", mKeyEntry);
                // Iterate the list with the snapped points
                for (int i = 0; i < snapList.size(); i++) {
                    Endless entity = endlessList.get(i);
                    String colorStr = entity.getProperty("color");
                    float clr = Float.valueOf(colorStr);
                    final MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(snapList.get(i));
                    markerOptions.title("Clue " + String.valueOf(i + 1));
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(clr));

                    // Put in the map the objects that are to be found
                    // Some values are stored in a predefined array
                    mAdventure.put("object", entity.getProperty("name"));
                    mAdventure.put("date", "");
                    mAdventure.put("order", 0);
                    mAdventure.put("latLng", snapList.get(i));
                    mAdventure.put("type", "clue");
                    mAdventure.put("acquired", false);

                    // Push to the database
                    mDb.child(mKeyEntry).push().setValue(mAdventure, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(DatabaseError databaseErr, DatabaseReference databaseRef) {
                            // Add the db key of the entry as marker tag
                            Marker objMarker = mMap.addMarker(markerOptions);
                            objMarker.setTag(databaseRef.getKey());
                            markerList.add(objMarker);
                        }
                    });
                }
                // Put a marker at the starting point
                if (mLastLocation != null) {
                    if (mCurrLocationMarker != null) {
                        mCurrLocationMarker.remove();
                    }
                    //Place current location marker
                    LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng);
                    markerOptions.title("Starting point");
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                    mCurrLocationMarker = mMap.addMarker(markerOptions);
                }
                // Show search button, hide start button, show start dialog, hide loader
                startBtn.setVisibility(View.GONE);
                searchBtn.setVisibility(View.VISIBLE);
                mLoadItemsDialog.dismiss();
                showStoryDialog("start");
            }
        });
    }

    // Asynchronous function that handles the response from the http call
    private String downloadContent(String myurl) throws IOException {
        InputStream is = null;
        int length = 500;
        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();
            int response = conn.getResponseCode();
            is = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (response == 200) {
                InputStreamReader isw = new InputStreamReader(is);

                int data = isw.read();

                while (data != -1) {
                    char current = (char) data;
                    data = isw.read();
                    sb.append(current);
                }
            }
            return sb.toString();
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Put markers according to the database
    // If an obect is not acquired, a marker will be placed on its point
    public void putMarkers(String key) {
        DatabaseReference ref = mDb.child(key);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() == null) {
                    startBtn.setVisibility(View.VISIBLE);
                    searchBtn.setVisibility(View.GONE);
                } else {
                    startBtn.setVisibility(View.GONE);
                    searchBtn.setVisibility(View.VISIBLE);
                    int i = 0;
                    markerList.clear();
                    Boolean allGathered = true;
                    for (DataSnapshot ds : dataSnapshot.getChildren()) {
                        String lat = ds.child("latLng").child("latitude").getValue().toString();
                        String lon = ds.child("latLng").child("longitude").getValue().toString();
                        if (!Boolean.parseBoolean(ds.child("acquired").getValue().toString())) {
                            allGathered = false;
                            Endless entity = endlessList.get(i - 1);
                            String colorStr = entity.getProperty("color");
                            float clr = Float.valueOf(colorStr);
                            MarkerOptions markerOptions = new MarkerOptions();
                            markerOptions.position(new LatLng(Double.parseDouble(lat), Double.parseDouble(lon)));
                            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(clr));
                            markerOptions.title("Clue " + (i));
                            Marker mark = mMap.addMarker(markerOptions);
                            mark.setTag(ds.getKey());
                            markerList.add(mark);
                        }

                        if (ds.child("object").getValue().toString().equals("compass")) {
                            MarkerOptions startMarkerOptions = new MarkerOptions();
                            startMarkerOptions.title("Starting point");
                            startMarkerOptions.position(new LatLng(Double.parseDouble(lat), Double.parseDouble(lon)));
                            startMarkerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                            Marker startMark = mMap.addMarker(startMarkerOptions);
                            startMark.setTag(ds.getKey());
                        }
                        i++;
                    }
                    if (allGathered) {
                        startBtn.setVisibility(View.VISIBLE);
                        searchBtn.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //handle databaseError
                Toast.makeText(MapsActivity.this, "There was a problem with the database, please try again in a few moments.", Toast.LENGTH_LONG).show();
            }
        });
    }


    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            //do your request in here so that you don't interrupt the UI thread
            try {
                return downloadContent(params[0]);
            } catch (IOException e) {
                return "Unable to retrieve data. URL may be invalid.";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            //Here you are done with the task
            JSONObject object = null;
            try {
                object = new JSONObject(result);
                JSONArray jarray = object.getJSONArray("snappedPoints");
                for (int i = 0; i < 7; i++) {
                    JSONObject jsonobject = jarray.getJSONObject(i);
                    JSONObject loc = jsonobject.getJSONObject("location");
                    mSnapList.add(new LatLng(loc.getDouble("latitude"), loc.getDouble("longitude")));
                    if (i == 6) {
                        addRandomMarkers(mSnapList);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(MapsActivity.this, "There was a problem in gathering the clues, please restart the app :(", Toast.LENGTH_LONG).show();
                mLoadItemsDialog.dismiss();
            }
        }
    }

    // Class for an Endless object
    // This is needed for making an array of objects
    // These objects will contain necessary values for handling the app's cycle
    // (color of markers, font of each alert dialog, names of objects etc)
    public class Endless {

        private Map<String, String> properties = new HashMap<String, String>();
        private Map<String, Callable<Object>> callables = new HashMap<String, Callable<Object>>();

        public String getProperty(String key) {
            return properties.get(key);
        }

        public void setProperty(String key, String value) {
            properties.put(key, value);
        }

        public Object call(String key) throws Exception {
            Callable<Object> callable = callables.get(key);
            if (callable != null) {
                return callable.call();
            }
            return null;
        }

        public void define(String key, Callable<Object> callable) {
            callables.put(key, callable);
        }
    }

    // Populate a list with the distinct objects to be found
    public ArrayList<Endless> createEntities(ArrayList<Endless> list) {
        Endless Desire = new Endless();
        Desire.setProperty("name", "desire");
        Desire.setProperty("color", "0.0f");
        Desire.setProperty("font", "cursive_handwriting_regular.ttf");

        Endless Despair = new Endless();
        Despair.setProperty("name", "despair");
        Despair.setProperty("color", "60.0f");
        Despair.setProperty("font", "handwritten_font.ttf");

        Endless Destruction = new Endless();
        Destruction.setProperty("name", "destruction");
        Destruction.setProperty("color", "30.0f");
        Destruction.setProperty("font", "simply_font.ttf");

        Endless Delirium = new Endless();
        Delirium.setProperty("name", "delirium");
        Delirium.setProperty("color", "180.0f");
        Delirium.setProperty("font", "craziest_deco_regular.ttf");

        Endless Dream = new Endless();
        Dream.setProperty("name", "dream");
        Dream.setProperty("color", "270.0f");
        Dream.setProperty("font", "green_avocado_thin.ttf");

        Endless Destiny = new Endless();
        Destiny.setProperty("name", "destiny");
        Destiny.setProperty("color", "120.0f");
        Destiny.setProperty("font", "caviar_dreams.ttf");

        Endless Death = new Endless();
        Death.setProperty("name", "death");
        Death.setProperty("color", "330.0f");
        Death.setProperty("font", "simplehandwritting_regular.ttf");

        list.add(Desire);
        list.add(Despair);
        list.add(Destruction);
        list.add(Delirium);
        list.add(Dream);
        list.add(Destiny);
        list.add(Death);

        return list;
    }

    /* Dialogs */
    public void showEndlessDialog(String endless, String font, Boolean allFound, int currPoints) {
        final Boolean currAllFound = allFound;
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(getResources().getIdentifier(endless, "layout", getPackageName()), null);
        TextView txtView = (TextView) view.findViewById(getResources().getIdentifier(endless + "Text", "id", getPackageName()));
        TextView txtObjView = (TextView) view.findViewById(getResources().getIdentifier(endless + "ObjectText", "id", getPackageName()));
        TextView txtPointsView = (TextView) view.findViewById(getResources().getIdentifier(endless + "_points", "id", getPackageName()));
        Typeface custom_font = Typeface.createFromAsset(getAssets(), "fonts/" + font);
        final MediaPlayer mp = MediaPlayer.create(getApplicationContext(), getResources().getIdentifier(endless, "raw", getPackageName()));
        txtView.setTypeface(custom_font);
        txtObjView.setTypeface(custom_font);
        txtPointsView.setText("Points: " + Integer.toString(currPoints) + "/140");
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, getResources().getIdentifier(endless + "DialogTheme", "style", getPackageName()));
        alertDialog.setTitle(endless.substring(0, 1).toUpperCase() + endless.substring(1));
        alertDialog.setView(view);
        alertDialog.setPositiveButton("Thank you!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mp.stop();
                if (currAllFound) {
                    startBtn.setVisibility(View.VISIBLE);
                    searchBtn.setVisibility(View.GONE);
                    showStoryDialog("end");
                    mMap.clear();
                }
            }
        });
        endlessDialog = alertDialog.create();
        mp.start();
        endlessDialog.show();
    }

    public void showStoryDialog(String type) {
        final String currType = type;
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(getResources().getIdentifier(type + "_story", "layout", getPackageName()), null);
        TextView txtView = (TextView) view.findViewById(getResources().getIdentifier(type + "Text", "id", getPackageName()));
        Typeface custom_font = Typeface.createFromAsset(getAssets(), "fonts/youngones__regular.ttf");
        final MediaPlayer mp = MediaPlayer.create(getApplicationContext(), getResources().getIdentifier(type, "raw", getPackageName()));
        txtView.setTypeface(custom_font);
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, getResources().getIdentifier(type + "DialogTheme", "style", getPackageName()));
        alertDialog.setTitle(getResources().getIdentifier(type + "_title", "string", getPackageName()));
        alertDialog.setView(view);
        alertDialog.setPositiveButton(type.equals("start") ? "Ok!" : "I know...", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mp.stop();
                if (currType.equals("start")) {
                    // Inform the user in order to start seeking
                    Toast toast = Toast.makeText(getApplicationContext(), "Start seeking the Endless clues!", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                    toast.show();
                }
                if (currType.equals("end")) {
                    // Say goodbye to the user. Her adventure has just ended
                    Toast toast = Toast.makeText(getApplicationContext(), "Goodbye...", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                    toast.show();
                }
            }
        });
        storyDialog = alertDialog.create();
        if (!storyDialog.isShowing()) {
            mp.start();
            storyDialog.show();
        }
    }
}


