package ca.fuwafuwa.gaku.Network;

import android.content.Context;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AnkiConnectClient {

    private static final String TAG = AnkiConnectClient.class.getSimpleName();
    private static AnkiConnectClient instance;
    private final Context context;
    private AnkiConnectApi api;
    private String currentUrl;

    private AnkiConnectClient(Context context) {
        this.context = context.getApplicationContext();
        ensureApi();
    }

    public static synchronized AnkiConnectClient getInstance(Context context) {
        if (instance == null) {
            instance = new AnkiConnectClient(context);
        }
        return instance;
    }

    private synchronized void ensureApi() {
        String url = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("anki_connect_url", "http://10.0.2.2:8765");

        if (api == null || !url.equals(currentUrl)) {
            currentUrl = url;
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build();

            // Ensure URL ends with slash
            if (!url.endsWith("/"))
                url += "/";

            try {
                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(url)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                api = retrofit.create(AnkiConnectApi.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create AnkiConnect client", e);
                api = null;
            }
        }
    }

    public void guiBrowse(String query) {
        ensureApi();
        if (api == null)
            return;

        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        api.request(new AnkiConnectDTOs.Request("guiBrowse", params))
                .enqueue(new Callback<AnkiConnectDTOs.Response<Object>>() {
                    @Override
                    public void onResponse(Call<AnkiConnectDTOs.Response<Object>> call,
                            Response<AnkiConnectDTOs.Response<Object>> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "guiBrowse success");
                        } else {
                            Log.e(TAG, "guiBrowse failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<AnkiConnectDTOs.Response<Object>> call, Throwable t) {
                        Log.e(TAG, "guiBrowse error", t);
                    }
                });
    }

    public void findNotes(String query, Callback<AnkiConnectDTOs.Response<Object>> callback) {
        ensureApi();
        if (api == null)
            return;
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        api.request(new AnkiConnectDTOs.Request("findNotes", params)).enqueue(callback);
    }
}
