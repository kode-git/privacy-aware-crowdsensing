package com.example.crowdsensingapp;

import static android.provider.CallLog.Locations.LATITUDE;
import static java.lang.Math.log10;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.io.UnsupportedEncodingException;
import java.lang.*;
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.crowdsensingapp.databinding.ActivityMapsBinding;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.JsonParser;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * MapsActivity is the class to define the main UI of the Android application with displaying a map with a current location marker,
 * two buttons for settings and send records activities and a regular time location sending
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,SettingsView.SettingsAdderViewListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private Location actuaLocation = null;
    private MediaRecorder  mRecorder;
    private Button recordButton;
    private Button settingsButton;
    private SettingData actualSettings;
    private SharedPreferences pref;
    private TextView meanDb;
    private Marker myPosition;
    private ImageButton playButton;
    private Boolean startRec = false;
    private UUID id;
    private Timestamp timestamp;
    private int toastCounter=0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        id = UUID.randomUUID();
        actualSettings = new SettingData(1,1000,60,true,false,50);

        //restoring privacy preferences
        pref = getPreferences(Context.MODE_PRIVATE);
        Map <String, ?> privacySett= (Map<String, Integer>) pref.getAll();
        for (Map.Entry<String, ?> entry : privacySett.entrySet()) {

            if(entry.getKey().equals("nNeighbour")) {
                actualSettings.setnNeighbour((int) entry.getValue());
            }else if (entry.getKey().equals("range")){
                actualSettings.setRange((int) entry.getValue());
            }else if(entry.getKey().equals("time")){
                actualSettings.setMinutesTime((int) entry.getValue());
            }else if(entry.getKey().equals("prv")){
                actualSettings.setPrivacyOnOff((boolean) entry.getValue());
            }else if(entry.getKey().equals("def")){
                actualSettings.setDefaultOnOff((boolean) entry.getValue());
                if(actualSettings.isDefaultOnOff()){
                    //updateSettings();
                }
            }else if(entry.getKey().equals("to")){
                actualSettings.setTo((int)entry.getValue());
            }
        }



        //binding the map fragment
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        recordButton = (Button) findViewById(R.id.record_btn);
        settingsButton = (Button) findViewById(R.id.settings);
        meanDb = (TextView) findViewById(R.id.mean);
        playButton = (ImageButton) findViewById(R.id.rec);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (startRec){
                    startRec=false;
                    playButton.setImageDrawable(getResources().getDrawable(R.drawable.rec));
                }else{
                    startRec=true;
                    playButton.setImageDrawable(getResources().getDrawable(R.drawable.stop));
                }

            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSettings();

            }
        });

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    double powerDb = 10 * log10(getAmplitude());
                    Log.i("Actual value", String.valueOf(powerDb));
                    if(powerDb != Double.NEGATIVE_INFINITY) {
                        sendRecord(powerDb);
                        Context context = getApplicationContext();
                        CharSequence text = "Location sent with success!";
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }else {
                        Context context = getApplicationContext();
                        CharSequence text = "Troubles with microphone, retry";
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                }catch ( Exception e){
                    System.out.println(e + " A");
                }




            }
        });


        //permission request if the permissions are denied
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }else{
            //using a localization based service
            FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                actuaLocation = location;
                                if (mMap!=null){
                                    LatLng latLngLoc = new LatLng( actuaLocation.getLatitude(),actuaLocation.getLongitude());
                                    myPosition = mMap.addMarker(new MarkerOptions().position(latLngLoc).title("Your position"));
                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLngLoc));
                                }

                            }
                        }

                    });

            //setting the location request
            LocationRequest mLocationRequest = LocationRequest.create();
            mLocationRequest.setInterval(15000);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);



        //my location callback
           LocationCallback mLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                            actuaLocation=location;
                        LatLng latLngLoc = new LatLng( actuaLocation.getLatitude(),actuaLocation.getLongitude());
                        myPosition.setPosition(latLngLoc);
                            getMeanDb();
                        if (startRec){
                            double powerDb = 10 * log10(getAmplitude());
                            Log.i("Actual value", String.valueOf(powerDb));
                            if(powerDb != Double.NEGATIVE_INFINITY) {
                                sendRecord(powerDb);
                            }else {
                                //toast notification
                                Context context = getApplicationContext();
                                CharSequence text = "We are getting troubles with your mic";
                                int duration = Toast.LENGTH_SHORT;

                                Toast toast = Toast.makeText(context, text, duration);
                                if(toastCounter<1){
                                    toast.show();
                                    toastCounter++;
                                }
                            }
                        }
                    }
                } };
        //setting the location updates
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback,
                    Looper.getMainLooper());
        }



        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }else{
           mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();


        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * getAmplitude() gets the amplitude in decibels when mRecorder is in "online" status
     * @return amplitude of the noise in decibels
     */
    public double getAmplitude(){
        if (mRecorder != null)
            return  (mRecorder.getMaxAmplitude());
        else
            return 0;
    }

    /**
     * getMeanDb() send periodically a request to the backend server to ask the mean noise amplitude in decibels around
     * an area of 3 kilometers from the current app user location.
     */
    public void getMeanDb(){
        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = "http://10.0.2.2:4000/getMeanDb";
            Feature pointFeature = Feature.fromGeometry((Point.fromLngLat(Utils.obfuscate(actuaLocation.getLongitude(),3), Utils.obfuscate(actuaLocation.getLatitude(),3))));
           // pointFeature.addNumberProperty("Range", actualSettings.getRange());


            final String requestBody = pointFeature.toJson();
            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    JSONObject json = null;
                    try {
                    json = new JSONObject(response);
                    double myMean = Double.parseDouble(json.getString("mean"));
                        Log.i("Mean", myMean + " dB");
                        if (myMean!=0) {
                            // round the double value on 2 decimal digits
                            myMean = Utils.round(myMean, 2);
                            meanDb.setText("Mean: " + myMean + " dB");
                        }else {
                            meanDb.setText( "Mean: NaN dB");
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("Error Log", error.toString());
                    meanDb.setText( "Mean: NaN dB");
                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {

                        try {
                            responseString = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };

            requestQueue.add(stringRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * openSettings() modify the view of the app with the opening of the settings table
     */
    public void openSettings(){
        toastCounter=0;
        SettingsView settingsView = new SettingsView();
        Bundle bundle = new Bundle();
        bundle.putInt("range",actualSettings.getRange());
        bundle.putInt("neigh",actualSettings.getnNeighbour());
        bundle.putInt("time",actualSettings.getMinutesTime());
        bundle.putBoolean("prv", actualSettings.isPrivacyOnOff());
        bundle.putBoolean("def", actualSettings.isDefaultOnOff());
        bundle.putInt("to", actualSettings.getTo());
        settingsView.setArguments(bundle);
        settingsView.show(getSupportFragmentManager(), "settings");
    }


    /**
     * ApplyAdder is the UI dialogs between fragments of the settigView in the MapsActivity
     * @param s is the setting data passed during the fragment transactions
     */
    public void applyAdder(SettingData s){
        SharedPreferences.Editor editor = pref.edit();
        actualSettings.setnNeighbour(s.getnNeighbour());
        actualSettings.setRange(s.getRange());
        actualSettings.setMinutesTime(s.getMinutesTime());
        actualSettings.setPrivacyOnOff(s.isPrivacyOnOff());
        actualSettings.setDefaultOnOff(s.isDefaultOnOff());
        actualSettings.setTo(s.getTo());
        editor.putInt("nNeighbour", s.getnNeighbour());
        editor.putInt("range", s.getRange());
        editor.putInt("time", s.getMinutesTime());
        editor.putBoolean("prv", s.isPrivacyOnOff());
        editor.putBoolean("def", s.isDefaultOnOff());
        editor.putInt("to", s.getTo());
        editor.commit();
       // if(s.isDefaultOnOff())updateSettings();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.mMap = googleMap;

    }

    /**
     * sendRecord makes a request to the trusted server and call an API to create a location based on the spatial
     * clock method.
     * @param myDB is the decibel associated to the location sent to the trusted server
     */
    public void sendRecord(double myDB){

        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = "http://10.0.2.2:3000/createLocation";

            // Update timestamp
            timestamp = new Timestamp(System.currentTimeMillis());
            Feature pointFeature = Feature.fromGeometry(Point.fromLngLat(actuaLocation.getLongitude(), actuaLocation.getLatitude()));

            pointFeature.addNumberProperty("neighbour", actualSettings.getnNeighbour());
            pointFeature.addNumberProperty("range", actualSettings.getRange());
            pointFeature.addNumberProperty("minutesTime", actualSettings.getMinutesTime());
            pointFeature.addBooleanProperty("privacyOnOff", actualSettings.isPrivacyOnOff());
            pointFeature.addBooleanProperty("automatic", actualSettings.isDefaultOnOff());
            pointFeature.addStringProperty("timestamp", timestamp.toString());
            pointFeature.addStringProperty("userId", id.toString());
            pointFeature.addNumberProperty("db", myDB);
            pointFeature.addNumberProperty("alpha", (((double)actualSettings.getTo())/100));

            final String requestBody = pointFeature.toJson();

            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.i("Response", response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("Error Log", error.toString());
                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        // can get more details such as response.headers
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };

            requestQueue.add(stringRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }



    }

}