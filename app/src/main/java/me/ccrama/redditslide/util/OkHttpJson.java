package me.ccrama.redditslide.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.Response;

/**
 * Created by Fernando Barillas on 5/2/16.
 *
 * Helper class to parse OkHTTP responses as JsonObjects
 */
public class OkHttpJson {
    /**
     * Attempts to parse an OkHttp Response as a Gson JsonObject
     *
     * @param response The OkHttp Response to parse
     * @return A JsonObject if the response from the server was a JSON String, null otherwise.
     * @throws IOException Thrown when a a response was not successful
     */
    public static JsonObject getJsonFromResponse(Response response) throws IOException {
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
        String responseString = response.body().string();
        if (responseString.isEmpty()) return null;
        return new Gson().fromJson(responseString, JsonObject.class);
    }
}
