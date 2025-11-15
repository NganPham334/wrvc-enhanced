package com.tanaka42.webremotevolumecontrol;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class StartupActivity extends Activity {

    private static final String TAG = "WRVC_Startup";
    private static final String PREFS_NAME = "WRVC_Prefs";
    private static final String KEY_SERVICE_ENABLED = "isServiceEnabled";

    private TextView mURLTextView;
    private Button mCloseButton;
    private Button mEnableDisableButton;
    private Button mKillServiceButton;
    private TextView mCloseHintTextView;
    private TextView mHowToTextView;

    private static String mServerURL = "";
    private static String mServerIp = "";
    private static int mServerPort = 0;
    private static boolean mServerIpIsPrivate = true;

    private BroadcastReceiver urlUpdatedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);
        Log.d(TAG, "onCreate");

        getReadyToReceiveURLforUI();

        mCloseHintTextView = findViewById(R.id.textViewCloseWhenReady);
        mHowToTextView = findViewById(R.id.textViewHowTo);
        mURLTextView = findViewById(R.id.textViewURL);
        mEnableDisableButton = findViewById(R.id.buttonEnableDisable);
        mKillServiceButton = findViewById(R.id.buttonKillService);
        mCloseButton = findViewById(R.id.buttonClose);

        mEnableDisableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (HttpServer.isStarted()) {
                    Log.d(TAG, "Stopping remote control service.");
                    stopRemoteControlService();
                } else {
                    Log.d(TAG, "Starting remote control service.");
                    startRemoteControlService();
                }
            }
        });

        mKillServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Kill Service button clicked. Stopping service.");
                stopRemoteControlService();
            }
        });

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true); // Default to true on first launch

        if (isServiceEnabled) {
            Log.d(TAG, "Service is enabled in prefs, starting it.");
            startRemoteControlService();
        } else {
            Log.d(TAG, "Service is disabled in prefs, not starting it.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        updateActivity();
    }

    private void startRemoteControlService() {
        Log.d(TAG, "startRemoteControlService");
        // Save the user's choice to enable the service.
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_SERVICE_ENABLED, true);
        editor.apply();

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(new Intent(this, ForegroundService.class));
        } else {
            startService(new Intent(this, ForegroundService.class));
        }
    }

    private void stopRemoteControlService() {
        Log.d(TAG, "stopRemoteControlService");
        // Save the user's choice to disable the service.
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_SERVICE_ENABLED, false);
        editor.apply();

        stopService(new Intent(this, ForegroundService.class));
    }

    private void updateActivity() {
        Log.d(TAG, "updateActivity");
        if (HttpServer.isStarted()) {
            mEnableDisableButton.setText(R.string.disable_volume_remote_control);
            mHowToTextView.setText(R.string.how_to_enabled);
            mURLTextView.setText(mServerURL);
            mURLTextView.setVisibility(View.VISIBLE);
            mCloseHintTextView.setText(R.string.close_when_ready);
            mCloseHintTextView.setVisibility(View.VISIBLE);
        } else {
            mEnableDisableButton.setText(R.string.enable_volume_remote_control);
            if (mServerIpIsPrivate) {
                mHowToTextView.setText(R.string.how_to_disabled);
                mCloseHintTextView.setVisibility(View.INVISIBLE);
                mURLTextView.setVisibility(View.INVISIBLE);
            } else {
                mCloseHintTextView.setVisibility(View.INVISIBLE);
                mURLTextView.setText(R.string.verify_local_network_connection);
                mURLTextView.setVisibility(View.VISIBLE);
                mCloseHintTextView.setVisibility(View.VISIBLE);
                mCloseHintTextView.setText(R.string.about_private_limitation);
                mHowToTextView.setText(R.string.how_to_unable_to_find_local_address);
            }
        }
    }

    private void getReadyToReceiveURLforUI() {
        urlUpdatedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mServerURL = intent.getStringExtra("url");
                mServerIp = intent.getStringExtra("ip");
                mServerPort = intent.getIntExtra("port", 0);
                mServerIpIsPrivate = intent.getBooleanExtra("is_a_private_ip", true);
                Log.d(TAG, "Received URL update: " + mServerURL);
                updateActivity();
            }
        };
        registerReceiver(urlUpdatedReceiver, new IntentFilter("com.tanaka42.webremotevolumecontrol.urlupdated"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        unregisterReceiver(urlUpdatedReceiver);
    }
}
