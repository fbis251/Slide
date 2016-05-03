package me.ccrama.redditslide.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.jetbrains.annotations.NotNull;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;

import me.ccrama.redditslide.Activities.FullscreenImage;
import me.ccrama.redditslide.Activities.GifView;
import me.ccrama.redditslide.Activities.MediaView;
import me.ccrama.redditslide.Activities.Website;
import me.ccrama.redditslide.Reddit;
import me.ccrama.redditslide.SecretConstants;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by carlo_000 on 2/1/2016.
 */
public class AlbumUtils {

    // URLS should all have trailing slash
    public static final String IMGUR_GALLERY = "https://imgur.com/gallery/";
    public static final String IMGUR_MASHAPE_BASE = "https://imgur-apiv3.p.mashape.com/3/";
    public static final String IMGUR_MASHAPE_ALBUM = IMGUR_MASHAPE_BASE + "album/";
    public static final String IMGUR_MASHAPE_IMAGE = IMGUR_MASHAPE_BASE + "image/";
    public static final String JSON_SUFFIX = ".json";
    public static final String X_MASHAPE_KEY_HEADER = "X-Mashape-Key";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_VALUE = "Client-ID bef87913eb202e9";

    public static SharedPreferences albumRequests;

    private static String getHash(String url) {
        String next = url.substring(url.lastIndexOf("/"), url.length());
        if (next.length() < 5) {
            return getHash(url.replace(next, ""));
        } else {
            return next;
        }

    }

    private static String cutEnds(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        } else {
            return s;
        }
    }

    public static class GetAlbumJsonFromUrl extends AsyncTask<String, Void, ArrayList<JsonElement>> {

        public boolean gallery;
        public String hash;
        public Activity baseActivity;
        public boolean overrideAlbum;

        public GetAlbumJsonFromUrl(@NotNull String url, @NotNull Activity baseActivity) {
            this.baseActivity = baseActivity;

            String rawDat = cutEnds(url);
            if (rawDat.contains("gallery")) {
                // Imgur gallery URL
                gallery = true;
            }
            if (rawDat.endsWith("/")) {
                // Remove stray slash
                rawDat = rawDat.substring(0, rawDat.length() - 1);
            }
            String rawdat2 = rawDat;
            if (rawdat2.substring(rawDat.lastIndexOf("/"), rawdat2.length()).length() < 4) {
                rawDat = rawDat.replace(rawDat.substring(rawDat.lastIndexOf("/"), rawdat2.length()), "");
            }
            if (rawDat.contains("?")) {
                rawDat = rawDat.substring(0, rawDat.indexOf("?"));
            }

            hash = getHash(rawDat);
        }


        /**
         * Handles Imgur JSON Object data that was downloaded by this class. This method must be
         * implemented in overridden classes.
         *
         * @param data The Imgur Image JsonElements that were downloaded from galleries or albums
         */
        public void doWithData(ArrayList<JsonElement> data) {
            // Implement this method in overridden classes to handle Imgur JSON objects
        }

        public boolean dontClose;

        /**
         * Parses an Imgur Gallery JsonObject and attempts to display it using doWithData()
         *
         * @param galleryJsonObject The JsonObject to attempt to parse as an Imgur Gallery
         */
        public void doGallery(JsonObject galleryJsonObject) {
            if (galleryJsonObject != null && galleryJsonObject.has("data")) {
                LogUtil.v(galleryJsonObject.toString());
                final ArrayList<JsonElement> imagesArrayList = new ArrayList<>();

                if (galleryJsonObject.has("album_images")) {
                    JsonArray imagesArray = galleryJsonObject.getAsJsonObject("data").getAsJsonObject("image").getAsJsonObject("album_images").get("images").getAsJsonArray();
                    if (imagesArray == null || imagesArray.isJsonNull() || imagesArray.size() == 0)
                        return;
                    overrideAlbum = true; // Album with /gallery/ URL

                    for (JsonElement imageElement : imagesArray) {
                        imagesArrayList.add(imageElement);
                    }

                    doWithData(imagesArrayList);

                } else if (galleryJsonObject.has("data") && galleryJsonObject.get("data").getAsJsonObject().get("image").getAsJsonObject().has("album_images")) {
                    JsonArray imagesArray = galleryJsonObject.getAsJsonObject("data").getAsJsonObject("image").getAsJsonObject("album_images").get("images").getAsJsonArray();
                    if (imagesArray != null && !imagesArray.isJsonNull() && imagesArray.size() > 0) {
                        overrideAlbum = true; // Album with /gallery/ URL

                        for (JsonElement imageElement : imagesArray) {
                            imagesArrayList.add(imageElement);
                        }

                        doWithData(imagesArrayList);
                    }
                } else if (galleryJsonObject.has("data") && galleryJsonObject.get("data").getAsJsonObject().has("image")) {
                    // Only a single image was found in the gallery
                    if (dontClose) {
                        imagesArrayList.add(galleryJsonObject.get("data").getAsJsonObject().get("image"));
                        gallery = true;
                        doWithData(imagesArrayList);
                    } else {
                        // Could not parse the JSON data, open the imgur website
                        Intent i = new Intent(baseActivity, MediaView.class);
                        if (galleryJsonObject.getAsJsonObject("data").getAsJsonObject("image").get("mimetype").getAsString().contains("gif")) {
                            i.putExtra(GifView.EXTRA_URL, "http://imgur.com/" + galleryJsonObject.getAsJsonObject("data").getAsJsonObject("image").get("hash").getAsString() + ".gif"); //could be a gif
                        } else {
                            i.putExtra(FullscreenImage.EXTRA_URL, "http://imgur.com/" + galleryJsonObject.getAsJsonObject("data").getAsJsonObject("image").get("hash").getAsString() + ".png"); //could be a gif
                        }
                        baseActivity.startActivity(i);
                        baseActivity.finish();
                    }
                }
            }
        }

        /**
         * Parses an Imgur Album JsonObject and attempts to display it using doWithData()
         *
         * @param albumJsonObject The JsonObject to attempt to parse as an Imgur Album
         */
        public void doAlbum(JsonObject albumJsonObject) {
            if (albumJsonObject == null) return;
            LogUtil.v(albumJsonObject.toString());
            if (!albumJsonObject.has("data")) return;

            final ArrayList<JsonElement> imagesArrayList = new ArrayList<>();
            JsonObject jsonData = albumJsonObject.getAsJsonObject("data");
            if (jsonData == null || jsonData.isJsonNull() || !jsonData.has("images")) return;
            final JsonArray imagesArray = jsonData.get("images").getAsJsonArray();

            for (JsonElement imageElement : imagesArray) {
                imagesArrayList.add(imageElement);
            }

            doWithData(imagesArrayList);
        }

        JsonElement[] target;
        int count;
        int done;

        @Override
        protected ArrayList<JsonElement> doInBackground(final String... sub) {
            OkHttpClient client = new OkHttpClient();

            if (hash.startsWith("/")) {
                // Remove stray forward slash
                hash = hash.substring(1, hash.length());
            }
            if (hash.contains(",")) {
                // URL had a comma in it, attempt to split up the hashes and load each image individually
                target = new JsonElement[hash.split(",").length];
                count = 0;
                done = 0;
                for (String s : hash.split(",")) {
                    final int pos = count++;
                    Request request = new Request.Builder()
                            .url(IMGUR_MASHAPE_IMAGE + s + JSON_SUFFIX)
                            .addHeader(X_MASHAPE_KEY_HEADER, SecretConstants.getImgurApiKey(baseActivity))
                            .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE)
                            .build();
                    client.newCall(request).enqueue(new ImgurCallback() {
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            JsonObject obj = OkHttpJson.getJsonFromResponse(response);
                            if (obj != null && obj.has("data")) {
                                target[pos] = obj.get("data");
                            }
                            done += 1;
                            if (done == target.length) {
                                ArrayList<JsonElement> elementArrayList = new ArrayList<>();
                                for (JsonElement element : target) {
                                    if (element != null)
                                        elementArrayList.add(element);
                                }
                                if (elementArrayList.isEmpty()) {
                                    // The album couldn't be loaded with JSON data, open the imgur site instead
                                    Intent i = new Intent(baseActivity, Website.class);
                                    i.putExtra(Website.EXTRA_URL, "https://imgur.com/" + hash);
                                    baseActivity.startActivity(i);
                                    baseActivity.finish();
                                } else {
                                    doWithData(elementArrayList);
                                }
                            }
                        }
                    });

                }

            } else if (baseActivity != null) {

                if (gallery) {
                    // Handle /gallery/ URLs
                    final String galleryUrl = IMGUR_GALLERY + hash + JSON_SUFFIX;
                    final JsonObject jsonObject = new JsonParser()
                            .parse(albumRequests.getString(galleryUrl, ""))
                            .getAsJsonObject();
                    if (albumRequests.contains(galleryUrl) && jsonObject.has("data")) {
                        // Use the cached gallery data to display the images
                        baseActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                doGallery(jsonObject);
                            }
                        });

                    } else {
                        // No cached gallery data found, make a new request to the Imgur API
                        Request request = new Request.Builder()
                                .url(galleryUrl)
                                .build();
                        client.newCall(request).enqueue(new ImgurCallback() {
                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                final JsonObject result = OkHttpJson.getJsonFromResponse(response);
                                if (result != null && result.has("data")) {
                                    albumRequests.edit().putString(galleryUrl, result.toString()).apply();

                                    baseActivity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            doGallery(result);
                                        }
                                    });
                                } else if (!dontClose) {
                                    gallery = false;
                                    doInBackground(hash);
                                }
                            }
                        });
                    }
                } else if (albumRequests.contains(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX)) {
                    // The response is cached, no need for HTTP call
                    baseActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            doAlbum(new JsonParser().parse(albumRequests.getString(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX, "")).getAsJsonObject());
                        }
                    });
                } else {
                    // The response is not cached, get the data from Imgur API
                    Request request = new Request.Builder()
                            .url(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX)
                            .addHeader(X_MASHAPE_KEY_HEADER, SecretConstants.getImgurApiKey(baseActivity))
                            .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE)
                            .build();
                    client.newCall(request).enqueue(new ImgurCallback() {
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            final JsonObject result = OkHttpJson.getJsonFromResponse(response);
                            if (result != null && !result.isJsonNull()) {
                                albumRequests.edit().putString(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX, result.toString()).apply();
                                baseActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        doAlbum(result);
                                    }
                                });
                            } else if (!dontClose) {
                                Intent i = new Intent(baseActivity, Website.class);
                                i.putExtra(Website.EXTRA_URL, "https://imgur.com/a/" + hash);
                                baseActivity.startActivity(i);
                                baseActivity.finish();
                            }
                        }
                    });
                }
                return null;
            }
            return null;
        }
    }

    public static void saveAlbumToCache(final Activity c, String url) {
        OkHttpClient client = new OkHttpClient();
        boolean gallery = false;

        final String hash;
        String rawDat = cutEnds(url);
        if (rawDat.contains("gallery")) {
            gallery = true;
        }
        if (rawDat.endsWith("/")) {
            rawDat = rawDat.substring(0, rawDat.length() - 1);
        }
        String rawdat2 = rawDat;
        if (rawdat2.substring(rawDat.lastIndexOf("/"), rawdat2.length()).length() < 4) {
            rawDat = rawDat.replace(rawDat.substring(rawDat.lastIndexOf("/"), rawdat2.length()), "");
        }
        {

            hash = getHash(rawDat);

        }
        if (gallery) {
            final String galleryUrl = IMGUR_GALLERY + hash + JSON_SUFFIX;
            if (albumRequests.contains(galleryUrl)) {
                // Use cached gallery data
                preloadImages(c, new JsonParser().parse(albumRequests.getString(galleryUrl, "")).getAsJsonObject(), true);
            } else {
                // Gallery data not cached, make a new HTTP request
                Request request = new Request.Builder()
                        .url(galleryUrl)
                        .build();
                client.newCall(request).enqueue(
                        new ImgurCallback() {
                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                JsonObject result = OkHttpJson.getJsonFromResponse(response);
                                if (result == null || result.isJsonNull())
                                    return; // TODO: Handle null data
                                albumRequests.edit().putString(IMGUR_GALLERY + hash + JSON_SUFFIX, result.toString()).apply();
                                preloadImages(c, result, true);
                            }
                        }
                );
            }
        } else if (albumRequests.contains(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX)) {
            // Use cached album data
            preloadImages(c, new JsonParser().parse(albumRequests.getString(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX, "")).getAsJsonObject(), false);
        } else {
            // Make a new HTTP request for album data to Imgur API
            Request request = new Request.Builder()
                    .url(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX)
                    .addHeader(X_MASHAPE_KEY_HEADER, SecretConstants.getImgurApiKey(c))
                    .addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_VALUE)
                    .build();
            client.newCall(request).enqueue(
                    new ImgurCallback() {
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            JsonObject result = OkHttpJson.getJsonFromResponse(response);
                            if (result == null || result.isJsonNull())
                                return; // TODO: Handle null data
                            albumRequests.edit().putString(IMGUR_MASHAPE_ALBUM + hash + JSON_SUFFIX, result.toString()).apply();
                            preloadImages(c, result, false);
                        }
                    }
            );
        }
    }

    public static void preloadImages(Context c, JsonObject result, boolean gallery) {
        if (gallery && result != null) {

            if (result.has("data") && result.get("data").getAsJsonObject().has("image") && result.get("data").getAsJsonObject().get("image").getAsJsonObject().has("album_images") && result.get("data").getAsJsonObject().get("image").getAsJsonObject().get("album_images").getAsJsonObject().has("images")) {
                JsonArray obj = result.getAsJsonObject("data").getAsJsonObject("image").getAsJsonObject("album_images").get("images").getAsJsonArray();
                if (obj != null && !obj.isJsonNull() && obj.size() > 0) {

                    for (JsonElement o : obj) {
                        ((Reddit) c.getApplicationContext()).getImageLoader().loadImage("https://imgur.com/" + o.getAsJsonObject().get("hash").getAsString() + ".png", new SimpleImageLoadingListener());
                    }

                }
            }

        } else if (result != null) {
            if (result.has("album") && result.get("album").getAsJsonObject().has("images")) {
                JsonObject obj = result.getAsJsonObject("album");
                if (obj != null && !obj.isJsonNull() && obj.has("images")) {

                    final JsonArray jsonAuthorsArray = obj.get("images").getAsJsonArray();

                    for (JsonElement o : jsonAuthorsArray) {
                        ((Reddit) c.getApplicationContext()).getImageLoader().loadImage(o.getAsJsonObject().getAsJsonObject("links").get("original").getAsString(), new SimpleImageLoadingListener());
                    }
                }
            }
        }
    }

    /**
     * Handler for OkHTTP's Callback. Mostly used in order to get a more verbose error message when
     * onFailure() is called. You are expected to override the onResponse() method when using this
     * class.
     */
    static class ImgurCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            if (e == null) return;
            LogUtil.e("Download error " + call.request().url() + " " + e.getLocalizedMessage());
            // TODO: Show UI error
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            // Implement in overridden class
        }
    }
}
