package com.example.android.sunshine.app.listener;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;


public class SunshineWearListener extends WearableListenerService
        implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{
    
    public static final String PATH = "/wearable/data/sunshine/1726356709";
    public static final String MSG = "go";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(PATH)){
            if(new String(messageEvent.getData()).equals(MSG)){
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            byte[][] byteArrayMsgHolder = getCurrentForecastData();
                            byte[] serializedByteArrayMsgHolder = serialize(byteArrayMsgHolder);
                            sendToWearable(serializedByteArrayMsgHolder);
                        }catch (IOException e){
                        }
                    }
                }.start();
            }
        }else{
            super.onMessageReceived(messageEvent);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int cause) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }

    private byte[][] getCurrentForecastData(){
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return null;
        }
        if (!data.moveToFirst()) {
            data.close();
            return null;
        }

        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        Bitmap forecastBitmap = BitmapFactory.decodeResource(getResources(), weatherArtResourceId);

        forecastBitmap = Bitmap.createScaledBitmap(forecastBitmap, (int) getResources().getDimension(R.dimen.wearbale_icon_dimen),
                (int) getResources().getDimension(R.dimen.wearbale_icon_dimen),
                false);

        byte[] imageByteArray = convertToByte(forecastBitmap);
        byte[] minTempByteArray = formattedMinTemperature.getBytes();
        byte[] maxTempByteArray = formattedMaxTemperature.getBytes();

        return new byte[][]{imageByteArray, minTempByteArray, maxTempByteArray};
    }
    
    private void sendToWearable(final byte[] message){
        if(mGoogleApiClient.isConnected()) {
            NodeApi.GetConnectedNodesResult nodesList =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

            for(Node node : nodesList.getNodes()){
                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient,
                            node.getId(),
                            PATH,
                            message).await();
            }
        }
    }
    
    private byte[] convertToByte(Bitmap bitmap){
        ByteArrayOutputStream byteStream = null;

        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return byteStream.toByteArray();
        }finally {
            try {
                if(byteStream != null) {
                    byteStream.close();
                }
            }catch (IOException e){
            }
        }
    }

    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }
}
