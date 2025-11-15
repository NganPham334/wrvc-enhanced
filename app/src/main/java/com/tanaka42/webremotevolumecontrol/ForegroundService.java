package com.tanaka42.webremotevolumecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class ForegroundService extends Service {

    private static final String TAG = "WRVC_ForegroundService";
    private static final String PREFS_NAME = "WRVC_Prefs";
    private static final String KEY_SERVICE_ENABLED = "isServiceEnabled";
    private final Object serverLock = new Object();

    private final BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            if (isConnected && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                Log.d(TAG, "Network receiver triggered: WiFi connected. Ensuring server is running.");
                restartServer();
            }
        }
    };

    public ForegroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        String channelId = getString(R.string.app_name);
        createNotificationChannel(channelId);

        Intent notificationIntent = new Intent(getApplicationContext(), StartupActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setOngoing(true)
                .setContentTitle(getString(R.string.ongoing_notification_title))
                .setContentText(getString(R.string.ongoing_notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setPriority(NotificationCompat.PRIORITY_MIN) // Keep notification unobtrusive
                .setContentIntent(pendingIntent)
                .build();

        startForeground(42, notification);

        Log.d(TAG, "Registering network change receiver.");
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChangeReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true);

        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled in prefs. Stopping self.");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "onStartCommand received.");
        restartServer();
        return START_STICKY; // Changed back to START_STICKY for reliable restarts
    }

    private void restartServer() {
        synchronized (serverLock) {
            Log.d(TAG, "Restarting server...");

            HttpServer.stopServer(); // This is a synchronous call

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            HttpServer httpServer = new HttpServer(audioManager, getApplicationContext());
            httpServer.start();
        }
    }

    private void createNotificationChannel(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use IMPORTANCE_MIN to make the notification as unobtrusive as possible
            NotificationChannel channel = new NotificationChannel(channelId, getString(R.string.running_indicator), NotificationManager.IMPORTANCE_MIN);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        Log.d(TAG, "Unregistering network change receiver.");
        unregisterReceiver(networkChangeReceiver);
        HttpServer.stopServer();
    }
}
