package com.tanaka42.webremotevolumecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "WRVC_NetworkReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.d(TAG, "ConnectivityManager is null");
            return;
        }

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        Log.d(TAG, "isConnected: " + isConnected);

        if (isConnected) {
            Log.d(TAG, "Network type: " + activeNetwork.getTypeName());
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                Log.d(TAG, "WiFi is connected, starting service.");
                Intent serviceIntent = new Intent(context, ForegroundService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "Not a WiFi connection, doing nothing.");
            }
        } else {
            Log.d(TAG, "No active network connection.");
        }
    }
}
