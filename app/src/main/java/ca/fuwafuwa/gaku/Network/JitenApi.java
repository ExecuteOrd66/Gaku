package ca.fuwafuwa.gaku.Network;

import java.util.List;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface JitenApi {

    @POST("/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    // --- Reader Endpoints ---
    @POST("/api/reader/parse")
    Call<List<JitenDTOs.DeckWordDto>> parse(
            @Header("Authorization") String token, 
            @Body JitenDTOs.ReaderParseRequest request);

    @POST("/api/reader/lookup-vocabulary")
    Call<List<Integer>> lookupVocabulary(
            @Header("Authorization") String token,
            @Body JitenDTOs.LookupVocabularyRequest request);

    // --- Vocabulary Endpoints ---
    @GET("/api/vocabulary/{wordId}/{readingIndex}")
    Call<JitenDTOs.WordDto> getWordDetails(
            @Header("Authorization") String token,
            @Path("wordId") int wordId, 
            @Path("readingIndex") int readingIndex);

    // --- SRS Endpoints ---
    @POST("/api/srs/review")
    Call<Void> review(
            @Header("Authorization") String token,
            @Body JitenDTOs.SrsReviewRequest request);

    @POST("/api/srs/set-vocabulary-state")
    Call<Void> setVocabularyState(
            @Header("Authorization") String token,
            @Body JitenDTOs.SetVocabularyStateRequest request);

    // Legacy sync (optional if you want to keep local DB sync)
    @GET("/user/words")
    Call<List<UserWord>> getWords(@Header("Authorization") String token);

    class LoginRequest {
        String username;
        String password;
        public LoginRequest(String u, String p) { this.username = u; this.password = p; }
    }

    class LoginResponse { String token; }
}