package ca.fuwafuwa.gaku.Network;

import java.util.List;

import ca.fuwafuwa.gaku.Database.User.UserWord;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface JitenApi {

    @POST("/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("/user/words")
    Call<List<UserWord>> getWords(@Header("Authorization") String token);

    @POST("/user/words/sync")
    Call<SyncResponse> syncWords(@Header("Authorization") String token, @Body List<UserWord> words);

    @GET("/api/vocabulary/parse")
    Call<List<JitenDTOs.DeckWordDto>> parse(@Header("Authorization") String token,
            @retrofit2.http.Query("text") String text);

    @GET("/api/vocabulary/{wordId}/{readingIndex}")
    Call<JitenDTOs.WordDto> getWordDetails(@Header("Authorization") String token,
            @retrofit2.http.Path("wordId") int wordId, @retrofit2.http.Path("readingIndex") int readingIndex);

    class LoginRequest {
        String username;
        String password;

        public LoginRequest(String u, String p) {
            this.username = u;
            this.password = p;
        }
    }

    class LoginResponse {
        String token;
    }

    class SyncResponse {
        boolean success;
        int updated;
    }
}
