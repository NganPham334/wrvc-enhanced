package com.tanaka42.webremotevolumecontrol;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;


public class HttpServer extends Thread {
    private static final String TAG = "WRVC_HttpServer";
    private static volatile ServerSocket serverSocket;
    private AudioManager audioManager;
    private Context context;
    private static String server_ip;
    private static int server_port = 9000;
    private static boolean is_a_private_ip_address = false;
    private static volatile boolean isStart = false;
    private static volatile HttpServer runningThread = null;

    public HttpServer(final AudioManager audio, final Context ctx) {
        try {
            this.audioManager = audio;
            this.context = ctx;
        } catch (Exception er) {
            Log.e(TAG, "HttpServer constructor error", er);
        }
    }

    private boolean isPrivateAddress(String ip) {
        if (ip != null && !ip.isEmpty()) {
            Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)\\.\\d+\\.\\d+");
            Matcher matcher = pattern.matcher(ip);
            if (matcher.find()) {
                if (matcher.groupCount() > 1) {
                    String firstMemberString = matcher.group(1);
                    String secondMemberString = matcher.group(2);
                    if (firstMemberString != null && secondMemberString != null) {
                        int firstMember = parseInt(firstMemberString);
                        int secondMember = parseInt(secondMemberString);
                        return (
                                (firstMember == 10)
                                        || (firstMember == 172 && 16 <= secondMember && secondMember <= 31)
                                        || (firstMember == 192 && secondMember == 168)
                        );
                    }
                }
            }
        }
        return false;
    }

    private void notify_observers() {
        Log.d(TAG, "notify_observers");
        Intent urlUpdatedIntent = new Intent("com.tanaka42.webremotevolumecontrol.urlupdated");
        Bundle extras = new Bundle();
        extras.putString("url", "http://" + server_ip + ":" + server_port);
        extras.putString("ip", server_ip);
        extras.putInt("port", server_port);
        extras.putBoolean("is_a_private_ip", is_a_private_ip_address);
        urlUpdatedIntent.putExtras(extras);
        context.sendBroadcast(urlUpdatedIntent);
    }

    @Override
    public void run() {
        runningThread = this;
        Log.d(TAG, "run");

        try {
            final DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            server_ip = socket.getLocalAddress().getHostAddress();
            socket.disconnect();
            Log.d(TAG, "Server IP: " + server_ip);
        } catch (SocketException | UnknownHostException e) {
            Log.e(TAG, "Error getting server IP", e);
        }

        is_a_private_ip_address = isPrivateAddress(server_ip);
        Log.d(TAG, "Is private IP: " + is_a_private_ip_address);

        if (server_ip != null && is_a_private_ip_address) {
            try {
                InetAddress addr = InetAddress.getByName(server_ip);
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(addr, server_port), 100);
                serverSocket.setSoTimeout(5000);
                isStart = true;
                Log.d(TAG, "Server started and listening.");
                notify_observers(); // Moved here to notify AFTER server is started

                while (isStart) {
                    try {
                        Socket newSocket = serverSocket.accept();
                        Log.d(TAG, "Accepted new client.");
                        Thread newClient = new ClientThread(newSocket);
                        newClient.start();
                    } catch (SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        if (!isStart) {
                            Log.d(TAG, "Server socket was closed as expected.");
                        } else {
                            Log.e(TAG, "IOException in server loop, shutting down.", e);
                        }
                        break;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in server run loop", e);
            }
        }
        isStart = false;
        Log.d(TAG, "Server stopped.");
        notify_observers();
        runningThread = null;
    }

    public class ClientThread extends Thread {
        protected Socket socket;
        private String content_type = "";
        private String status_code;

        public ClientThread(Socket clientSocket) {
            this.socket = clientSocket;
        }

        @Override
        public void run() {
            try {
                DataInputStream in = new DataInputStream(this.socket.getInputStream());
                DataOutputStream out = new DataOutputStream(this.socket.getOutputStream());

                byte[] data = new byte[1500];

                if (in.read(data) != -1) {
                    String recData = new String(data).trim();
                    String[] header = recData.split("\\r?\\n");
                    String[] h1 = header[0].split(" ");

                    String requestedFile = "";

                    if (h1.length > 1) {
                        final String requestLocation = h1[1];

                        status_code = "200";

                        switch (requestLocation) {
                            case "/volume-up":
                                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
                                break;
                            case "/volume-down":
                                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                                break;
                            case "/volume-up.png":
                            case "/volume-down.png":
                                requestedFile = requestLocation.substring(1);
                                content_type = "image/png";
                                break;
                            case "/":
                                requestedFile = "webremotevolumecontrol_spa.html";
                                content_type = "text/html";
                                break;
                            default:
                                status_code = "404";
                                break;
                        }
                    } else {
                        status_code = "404";
                    }

                    byte[] buffer = new byte[0];
                    if (!requestedFile.isEmpty()) {
                        InputStream fileStream = context.getAssets().open(requestedFile, AssetManager.ACCESS_BUFFER);
                        int size = fileStream.available();
                        buffer = new byte[size];
                        fileStream.read(buffer);
                        fileStream.close();
                    }
                    writeResponse(out, buffer.length + "", buffer, status_code, content_type);
                }

            } catch (IOException e) {
                Log.e(TAG, "Error in client thread", e);
            } finally {
                try {
                    socket.close();
                    Log.d(TAG, "Client socket closed.");
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client socket", e);
                }
            }
        }
    }

    protected void printHeader(PrintWriter pw, String key, String value) {
        pw.append(key).append(": ").append(value).append("\r\n");
    }

    private void writeResponse(DataOutputStream output, String size, byte[] data, String status_code, String content_type) {
        try {
            SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output)), false);
            pw.append("HTTP/1.1 ").append(status_code).append(" \r\n");
            if (!content_type.isEmpty()) {
                printHeader(pw, "Content-Type", content_type);
            }
            printHeader(pw, "Date", gmtFrmt.format(new Date()));
            printHeader(pw, "Connection", "close");
            printHeader(pw, "Content-Length", size);
            printHeader(pw, "Server", server_ip);
            pw.append("\r\n");
            pw.flush();
            switch (content_type) {
                case "text/html":
                    pw.append(new String(data));
                    break;
                case "image/png":
                    output.write(data);
                    output.flush();
                    break;
            }
            pw.flush();
        } catch (Exception er) {
            Log.e(TAG, "Error in writeResponse", er);
        }
    }

    public static void stopServer() {
        try {
            Log.d(TAG, "stopServer() called.");
            isStart = false;

            ServerSocket localSocket = serverSocket;
            if (localSocket != null) {
                localSocket.close();
            }

            HttpServer oldThread = runningThread;
            if (oldThread != null && Thread.currentThread() != oldThread) {
                Log.d(TAG, "Waiting for old server thread to die...");
                oldThread.join();
                Log.d(TAG, "Old server thread is dead.");
            }
        } catch (IOException | InterruptedException er) {
            Log.e(TAG, "Error during server stop", er);
        }
    }

    public static boolean isStarted() {
        return isStart;
    }
}