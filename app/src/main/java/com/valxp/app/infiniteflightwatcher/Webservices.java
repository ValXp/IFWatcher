package com.valxp.app.infiniteflightwatcher;

import android.util.Log;

import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ValXp on 6/26/14.
 */
public class Webservices {

    public static JSONArray getJSON(APIConstants.APICalls call) {
        return getJSON(call, null, null);
    }

    public static JSONArray getJSON(APIConstants.APICalls call, String get) {
        return getJSON(call, get, null);
    }

    public static JSONArray getJSON(APIConstants.APICalls call, String get, String post) {
        try {
            String benchName = call.name();
            Utils.Benchmark.start(benchName);
            JSONArray arr = new JSONArray(connectionToString(fetch(call, get, post)));
            Utils.Benchmark.stopAndLog(benchName);
            return arr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static XmlPullParser getXML(APIConstants.APICalls call, String get, String post) {
        try {
            String benchName = call.name();
            Utils.Benchmark.start(benchName);
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(fetch(call, get, post), null);
            Utils.Benchmark.stopAndLog(benchName);
            return parser;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private static String connectionToString(InputStream stream) {
        if (stream == null)
            return null;
        BufferedReader streamReader;
        try {
            streamReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        StringBuilder strBuilder = new StringBuilder();
        String inputStr;
        try {
            while ((inputStr = streamReader.readLine()) != null)
                strBuilder.append(inputStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return strBuilder.toString();
    }

    public static String getFile(APIConstants.APICalls call, String remoteFile, String etag, String localFile) {
        try {
            HashMap<String, String> headers = new HashMap<>();
            if (etag != null) {
                headers.put("If-None-Match", etag);
            }
            HttpURLConnection connection = connect(call, remoteFile, null, headers);
            if (connection == null || connection.getResponseCode() != 200) {
                Log.d("Webservice", "Failed to get: " + remoteFile);
                return null;
            }
            InputStream is = connection.getInputStream();
            if (is == null) {
                return null;
            }
            OutputStream os = new FileOutputStream(localFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.close();
            is.close();
            return connection.getHeaderField("Etag");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static HttpURLConnection connect(APIConstants.APICalls call, String get, String post, Map<String, String> headers) {
        URL url;
        String request = call.toString() + (get == null ? "" : get);
        try {
            url = new URL(request);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (post != null) {
                connection.setDoOutput(true);

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(post);
                writer.flush();
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.addRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            return connection;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static InputStream fetch(APIConstants.APICalls call, String get, String post) {
        HttpURLConnection connection = connect(call, get, post, null);
        try {
            return connection == null ? null : connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}
