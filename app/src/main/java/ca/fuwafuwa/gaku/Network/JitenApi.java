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
