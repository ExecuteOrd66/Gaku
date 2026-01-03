package ca.fuwafuwa.gaku.Network;

import android.content.Context;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class JpdbApiClient {

    private static JpdbApiClient instance;
    private JpdbApi api;
    private String authToken;
    private Context context;

    private JpdbApiClient(Context context) {
        this.context = context.getApplicationContext();
        refreshSettings();
    }

    public static synchronized JpdbApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new JpdbApiClient(context);
        }
        return instance;
    }

    private void refreshSettings() {
        authToken = PreferenceManager.getDefaultSharedPreferences(context).getString("jpdb_api_key", "");
        if (!authToken.isEmpty() && !authToken.startsWith("Bearer ")) {
            authToken = "Bearer " + authToken;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Gson gson = new GsonBuilder().create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://jpdb.io")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        api = retrofit.create(JpdbApi.class);
    }

    public JpdbDTOs.ParseResponse parse(String text) throws IOException {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            return null;
        }

        JpdbDTOs.ParseRequest request = new JpdbDTOs.ParseRequest(
                Collections.singletonList(text),
                Arrays.asList(JpdbDTOs.TOKEN_FIELDS),
                Arrays.asList(JpdbDTOs.VOCAB_FIELDS_REQUEST));

        retrofit2.Response<JpdbDTOs.ParseResponse> response = api.parse(authToken, request).execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        return null;
    }
}
