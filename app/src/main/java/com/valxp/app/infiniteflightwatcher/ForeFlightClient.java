package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Created by ValXp on 2/20/15.
 */
public class ForeFlightClient extends Thread {
    public static final int FOREFLIGHT_PORT = 49002;
    public static final int PACKET_SIZE = 512;
    private Context mContext;
    private boolean mKeeprunning;
    private GPSListener mListener;

    public static interface GPSListener {
        public void OnGPSFixReceived(GPSData data);
    }

    public ForeFlightClient(Context context, GPSListener listener) {
        super();
        mContext = context;
        mListener = listener;
    }

    public void stopClient() {
        mKeeprunning = false;
    }

    @Override
    public void run() {
        mKeeprunning = true;
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(FOREFLIGHT_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            while (mKeeprunning) {
                socket.receive(packet);
                if (mListener != null) {
                    GPSData data = parseData(packet);
                    if (data != null)
                        mListener.OnGPSFixReceived(data);
                }
            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ATTData {
        public double yaw; // heading
        public double pitch;
        public double roll;
    }
    public static class GPSData {
        public double lat;
        public double lon;
        public double altitude; // meters
        public double heading;
        public double groundSpeed; // meters per second
        public long timestamp;
        public String ip;
    }

    private GPSData parseData(DatagramPacket packet) {
        String data = new String(packet.getData()).substring(0, packet.getLength());

        if (data == null || data.length() < 4)
            return null;
        String[] dataList = data.split(",");
        if (data.startsWith("XATT")) {
            ATTData att = new ATTData();
            int counter = 0;
            for (String str : dataList) {
                //Log.d("ATTParse", "Counter["+counter+"] = " + str);
                switch (counter) {
                    case 1:
                        att.yaw = Double.parseDouble(str);
                    break;
                    case 2:
                        att.pitch = Double.parseDouble(str);
                    break;
                    case 3:
                        att.roll = Double.parseDouble(str);
                    break;
                }
                ++counter;
            }
            return null;

        } else if (data.startsWith("XGPS")) {
            GPSData gps = new GPSData();
            gps.ip = packet.getAddress().getHostAddress();
            gps.timestamp = (((TimeProvider.getTime() / 1000) + 11644473600l) * 10000000);
            int counter = 0;
            for (String str : dataList) {

                //Log.d("GPSParse", "Counter["+counter+"] = " + str);
                switch (counter) {
                    case 1:
                        gps.lat = Double.parseDouble(str);
                        Log.d("XGPS", "Lat: " + gps.lat);
                    break;
                    case 2:
                        gps.lon = Double.parseDouble(str);
                        Log.d("XGPS", "Lon: " + gps.lon);
                    break;
                    case 3:
                        gps.altitude = Double.parseDouble(str) * 3.28084;
                    break;
                    case 4:
                        gps.heading = Double.parseDouble(str);
                    break;
                    case 5:
                        gps.groundSpeed = Double.parseDouble(str) * 1.943844;
                    break;
                }
                ++counter;
            }
            return gps;
        }
        return null;
    }
}
