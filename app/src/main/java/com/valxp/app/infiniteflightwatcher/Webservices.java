package com.valxp.app.infiniteflightwatcher;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

    public static JSONArray getJSON(APIConstants.APICalls call, String data) {
        InputStream stream = fetchJson(call, data);
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

        try {
            return new JSONArray(strBuilder.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static InputStream fetchJson(APIConstants.APICalls call, String request) {
        URL url = null;
        try {
            url = new URL(call.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            if (request == null) {
                return url.openStream();
            } else {
                URLConnection connection = url.openConnection();
                connection.setDoOutput(true);
                connection.setDoInput(true);

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(request);
                writer.flush();

                return connection.getInputStream();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
