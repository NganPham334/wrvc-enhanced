package com.tanaka42.webremotevolumecontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "WRVC_BootReceiver";
    private static final String PREFS_NAME = "WRVC_Prefs";
    private static final String KEY_SERVICE_ENABLED = "isServiceEnabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed intent received.");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false); // Default to false

            if (isServiceEnabled) {
                Log.d(TAG, "Service was enabled before reboot. Starting service.");
                Intent serviceIntent = new Intent(context, ForegroundService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "Service was not enabled before reboot. Doing nothing.");
            }
        }
    }
}
