package com.tanaka42.webremotevolumecontrol;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class WRVCApplication extends Application {

    private static final String TAG = "WRVC_Application";
    private static final String LOG_FILE_NAME = "crash_log.txt";
    private static final long MAX_LOG_SIZE = 64 * 1024; // 64 KB

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate. Setting up crash handler.");
        setupUncaughtExceptionHandler();
    }

    private void setupUncaughtExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Log.e(TAG, "Uncaught exception caught", ex);
            logCrashToFile(ex);

            // Chain to the default handler to allow the system to handle the crash (e.g., show dialog)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            }
        });
    }

    private void logCrashToFile(Throwable throwable) {
        try {
            File logFile = new File(getFilesDir(), LOG_FILE_NAME);

            // Check if the file needs to be cleared
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                Log.d(TAG, "Log file exceeds max size. Clearing it.");
                logFile.delete();
            }

            FileOutputStream fos = new FileOutputStream(logFile, true); // True for append
            OutputStreamWriter osw = new OutputStreamWriter(fos);

            osw.write("========================================\n");
            osw.write("Crash Detected: " + new Date().toString() + "\n");
            osw.write("========================================\n");

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString(); // stack trace as a string
            pw.close();
            sw.close();

            osw.write(stackTrace);
            osw.write("\n\n");

            osw.flush();
            osw.close();

            Log.d(TAG, "Successfully wrote crash to " + logFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to log crash to file", e);
        }
    }
}
