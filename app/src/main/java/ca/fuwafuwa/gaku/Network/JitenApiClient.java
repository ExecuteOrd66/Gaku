package ca.fuwafuwa.gaku.Network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.SQLException;
import java.util.List;

import androidx.preference.PreferenceManager;

import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Callback;
import retrofit2.Response;

public class JitenApiClient {

    private static final String TAG = "JitenApiClient";
    private static JitenApiClient instance;

    private JitenApi api;
    private String authToken;
    private String baseUrl;
    private UserDatabaseHelper dbHelper;
    private Context mContext;

    public interface SyncCallback {
        void onSyncComplete(boolean success, int updatedCount);
    }

    private JitenApiClient(Context context) {
        mContext = context.getApplicationContext();
        baseUrl = PreferenceManager.getDefaultSharedPreferences(mContext).getString("jiten_api_url",
                "https://api.jiten.moe");
        authToken = PreferenceManager.getDefaultSharedPreferences(mContext).getString("jiten_api_key", "");

        if (!authToken.isEmpty() && !authToken.startsWith("Bearer ")) {
            authToken = "Bearer " + authToken;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Gson gson = new GsonBuilder().setLenient().create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        api = retrofit.create(JitenApi.class);
        dbHelper = UserDatabaseHelper.instance(mContext);
    }

    public void setVocabularyState(int wordId, int readingIndex, String state) {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            return;
        }

        JitenDTOs.SetVocabularyStateRequest request = new JitenDTOs.SetVocabularyStateRequest();
        request.wordId = wordId;
        request.readingIndex = readingIndex;
        request.state = state;

        api.setVocabularyState(authToken, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Jiten state updated: " + state);
                } else {
                    Log.e(TAG, "Failed to update Jiten state: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Jiten state update error", t);
            }
        });
    }

    public void rateWord(int wordId, int readingIndex, int rating) {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            return;
        }

        JitenDTOs.SrsReviewRequest request = new JitenDTOs.SrsReviewRequest();
        request.wordId = wordId;
        request.readingIndex = readingIndex;
        request.rating = rating;

        api.review(authToken, request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Jiten SRS rating sent: " + rating);
                } else {
                    Log.e(TAG, "Failed to send Jiten SRS rating: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Jiten SRS rating error", t);
            }
        });
    }

    public static synchronized JitenApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new JitenApiClient(context);
        }
        return instance;
    }

    private void refreshSettings() {
        String newBaseUrl = PreferenceManager.getDefaultSharedPreferences(mContext).getString("jiten_api_url",
                "https://api.jiten.moe");
        String newAuthToken = PreferenceManager.getDefaultSharedPreferences(mContext).getString("jiten_api_key", "");

        if (!newAuthToken.isEmpty() && !newAuthToken.startsWith("Bearer ")) {
            newAuthToken = "Bearer " + newAuthToken;
        }

        if (!newBaseUrl.equals(baseUrl)) {
            baseUrl = newBaseUrl;
            OkHttpClient client = new OkHttpClient.Builder().build();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                    .build();
            api = retrofit.create(JitenApi.class);
        }

        authToken = newAuthToken;
    }

    public void login(String username, String password, SyncCallback callback) {
        refreshSettings();
        api.login(new JitenApi.LoginRequest(username, password))
                .enqueue(new retrofit2.Callback<JitenApi.LoginResponse>() {
                    @Override
                    public void onResponse(Call<JitenApi.LoginResponse> call,
                            retrofit2.Response<JitenApi.LoginResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            authToken = "Bearer " + response.body().token;
                            // Save this token to preferences automatically
                            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                                    .putString("jiten_api_key", response.body().token).apply();
                            if (callback != null)
                                callback.onSyncComplete(true, 0);
                        } else {
                            if (callback != null)
                                callback.onSyncComplete(false, 0);
                        }
                    }

                    @Override
                    public void onFailure(Call<JitenApi.LoginResponse> call, Throwable t) {
                        if (callback != null)
                            callback.onSyncComplete(false, 0);
                    }
                });
    }

    public void sync(SyncCallback callback) {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            if (callback != null)
                callback.onSyncComplete(false, 0);
            return;
        }

        try {
            List<UserWord> localWords = dbHelper.getUserWordDao().queryForAll();
            api.syncWords(authToken, localWords).enqueue(new retrofit2.Callback<JitenApi.SyncResponse>() {
                @Override
                public void onResponse(Call<JitenApi.SyncResponse> call,
                        retrofit2.Response<JitenApi.SyncResponse> response) {
                    if (response.isSuccessful()) {
                        fetchWordsFromServer(callback);
                    } else {
                        if (callback != null)
                            callback.onSyncComplete(false, 0);
                    }
                }

                @Override
                public void onFailure(Call<JitenApi.SyncResponse> call, Throwable t) {
                    if (callback != null)
                        callback.onSyncComplete(false, 0);
                }
            });
        } catch (SQLException e) {
            if (callback != null)
                callback.onSyncComplete(false, 0);
        }
    }

    private void fetchWordsFromServer(SyncCallback callback) {
        api.getWords(authToken).enqueue(new retrofit2.Callback<List<UserWord>>() {
            @Override
            public void onResponse(Call<List<UserWord>> call, retrofit2.Response<List<UserWord>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateLocalDb(response.body());
                    if (callback != null)
                        callback.onSyncComplete(true, response.body().size());
                } else {
                    if (callback != null)
                        callback.onSyncComplete(false, 0);
                }
            }

            @Override
            public void onFailure(Call<List<UserWord>> call, Throwable t) {
                if (callback != null)
                    callback.onSyncComplete(false, 0);
            }
        });
    }

    private void updateLocalDb(List<UserWord> serverWords) {
        try {
            for (UserWord sw : serverWords) {
                UserWord lw = dbHelper.getUserWordDao().queryBuilder()
                        .where().eq("text", sw.getText()).and().eq("reading", sw.getReading()).queryForFirst();
                if (lw == null) {
                    dbHelper.getUserWordDao().create(sw);
                } else if (sw.getTimestamp() > lw.getTimestamp()) {
                    lw.setStatus(sw.getStatus());
                    lw.setTimestamp(sw.getTimestamp());
                    dbHelper.getUserWordDao().update(lw);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Update local DB failed", e);
        }
    }

    public List<JitenDTOs.DeckWordDto> parse(String text) throws java.io.IOException {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            return null;
        }

        // Wrap the raw string into the DTO required by the new POST endpoint
        JitenDTOs.ReaderParseRequest request = new JitenDTOs.ReaderParseRequest(text);

        // Pass the request object instead of the raw text string
        retrofit2.Response<List<JitenDTOs.DeckWordDto>> response = api.parse(authToken, request).execute();

        if (response.isSuccessful()) {
            return response.body();
        }
        return null;
    }

    public JitenDTOs.WordDto getWordDetails(int wordId, int readingIndex) throws java.io.IOException {
        refreshSettings();
        if (authToken == null || authToken.isEmpty()) {
            return null;
        }
        retrofit2.Response<JitenDTOs.WordDto> response = api.getWordDetails(authToken, wordId, readingIndex).execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        return null;
    }
}
