package com.valxp.app.infiniteflightwatcher;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by ValXp on 6/26/14.
 */
public class Webservices {

    public static JSONArray getJSON(APIConstants.APICalls call) {
        return getJSON(call, null);
    }

    public static JSONArray getJSON(APIConstants.APICalls call, String post) {
        try {
            return new JSONArray(connectionToString(fetch(call, null, post)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static XmlPullParser getXML(APIConstants.APICalls call, String get, String post) {
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(fetch(call, get, post), null);
            return parser;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private static String connectionToString(InputStream stream) {
        if (stream == null)
            return null;
        BufferedReader streamReader = null;
        try {
            streamReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
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

    private static InputStream fetch(APIConstants.APICalls call, String get, String post) {
        URL url = null;
        String request = call.toString() + (get == null ? "" : get);
        try {
            url = new URL(request);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            if (post == null) {
                return url.openStream();
            } else {
                URLConnection connection = url.openConnection();
                connection.setDoOutput(true);
                connection.setDoInput(true);

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(post);
                writer.flush();

                return connection.getInputStream();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
