package com.example.unzi.findalert.ui;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.unzi.findalert.R;
import com.example.unzi.findalert.data.Alert;
import com.example.unzi.findalert.data.DatabaseHelper;
import com.example.unzi.findalert.gcm.GcmScheduler;
import com.example.unzi.offlinemaps.TilesProvider;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;


public class AlertActivity extends FragmentActivity  {
    private final String TAG = AlertActivity.class.getSimpleName();
    private GoogleMap mMap;
    private Alert mAlert;
    private boolean mIsInside;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        // set layout to fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        //offline maps
        mMap = mapFragment.getMap();

        startTileProvider(mMap);

        mAlert = (Alert) getIntent().getSerializableExtra("Alert");
        mIsInside =  getIntent().getBooleanExtra("isInside",false);

        if(mAlert==null)
            mAlert = getActiveAlert();
        if(mAlert!=null)
            setAlertBounds();

        //check if location is known, if not prompt the user to tell us
        if(!getIntent().getBooleanExtra("knownLocation",false)){
            findViewById(R.id.noLocationDialog).setVisibility(View.VISIBLE);
        }else{
            findViewById(R.id.alertDetails).setVisibility(View.VISIBLE);
            if(mAlert!=null)
                setAlertParameters();
            if(!mIsInside){
                cancelNotification();
            }
            //send alert received
            RegisterInFind.sharedInstance(this).receivedAlert(mAlert, mIsInside);
        }

        Button b = (Button) findViewById(R.id.button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                outsideAlert(v);
            }
        });

        Button b2 = (Button) findViewById(R.id.button2);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(AlertActivity.this)
                        .setTitle("Alert")
                        .setMessage("Please enable your location service (GPS)")
                        .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent gpsOptionsIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(gpsOptionsIntent);
                            }
                        }).setCancelable(false).show();
                insideAlert(v);
            }
        });
    }

    //get our tile database provider
    private void startTileProvider(GoogleMap googleMap) {
        String path = getFilesDir()+ "/mapapp/world.sqlitedb";
        TilesProvider tilesProvider = new TilesProvider(mMap, path);
    }

    public void cancelNotification(){
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(1);
    }
    public void outsideAlert(View v){
        setAlertParameters();
        findViewById(R.id.noLocationDialog).setVisibility(View.GONE);
        findViewById(R.id.alertDetails).setVisibility(View.VISIBLE);
        cancelNotification();
        RegisterInFind.sharedInstance(this).receivedAlert(mAlert, false);
    }

    public void insideAlert(View v){
        cancelNotification();
        setAlertParameters();
        findViewById(R.id.noLocationDialog).setVisibility(View.GONE);
        findViewById(R.id.alertDetails).setVisibility(View.VISIBLE);
        GcmScheduler.getInstance(getApplicationContext()).scheduleAlarm(getApplicationContext(),mAlert);
        RegisterInFind.sharedInstance(this).receivedAlert(mAlert, true);
    }

    public void setAlertParameters( ) {
        TextView alertName = (TextView) findViewById(R.id.alertName);
        TextView alertType = (TextView) findViewById(R.id.alertType);
        TextView alertDescription = (TextView) findViewById(R.id.alertDescription);
        TextView alertDate = (TextView) findViewById(R.id.alertStartDate);

        alertName.setText(mAlert.getName()+ " -");
        alertDescription.setText(mAlert.getDescription());
        alertType.setText( mAlert.getType());
        alertDate.setText(mAlert.getDate()+"");
    }

    public void setAlertBounds(){
        Log.d("test", "teste");
        PolygonOptions rectOptions = new PolygonOptions()
                .add(new LatLng(mAlert.getLatStart(), mAlert.getLonStart()))
                .add(new LatLng(mAlert.getLatEnd(), mAlert.getLonStart()))
                .add(new LatLng(mAlert.getLatEnd(), mAlert.getLonEnd()))
                .add(new LatLng(mAlert.getLatStart(), mAlert.getLonEnd()))
                .add(new LatLng(mAlert.getLatStart(), mAlert.getLonStart())).fillColor(R.color.transparent_gray);
        Log.d(TAG, "Start Coords" + mAlert.getLatStart() + " " + mAlert.getLonStart());
        Log.d(TAG, "End Coords" + mAlert.getLatEnd() + " " + mAlert.getLonEnd());

        LatLng focus = midPoint(mAlert.getLatStart(),  mAlert.getLonStart(),mAlert.getLatEnd(), mAlert.getLonEnd() );
        LatLngBounds lngBounds= new LatLngBounds(new LatLng(mAlert.getLatEnd(), mAlert.getLonEnd()),new LatLng(mAlert.getLatStart(), mAlert.getLonStart()));
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(lngBounds, 400,600,0));

        /*mMap.moveCamera(CameraUpdateFactory.newLatLng(focus));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));*/

        // Get back the mutable Polyline
        Polygon polyline = mMap.addPolygon(rectOptions);
        polyline.setZIndex(100);
    }

    public Alert getActiveAlert() {
        Cursor cursor = Alert.Store.fetchAlerts(
                DatabaseHelper.getInstance(getApplicationContext()).getReadableDatabase(),
                Alert.STATUS.ONGOING);

        if (!cursor.moveToFirst()) {
            // no current ongoing alerts, stop SensorsService
            cursor.close();
            return null;
        }
        // check whether we are inside the alert area
        return Alert.fromCursor(cursor);
    }

    public LatLng midPoint(double lat1,double lon1,double lat2,double lon2){

        double dLon = Math.toRadians(lon2 - lon1);

        //convert to radians
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        lon1 = Math.toRadians(lon1);

        double Bx = Math.cos(lat2) * Math.cos(dLon);
        double By = Math.cos(lat2) * Math.sin(dLon);
        double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2), Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx) + By * By));
        double lon3 = lon1 + Math.atan2(By, Math.cos(lat1) + Bx);

        return new LatLng(Math.toDegrees(lat3), Math.toDegrees(lon3));
    }
}
