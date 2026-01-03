package ca.fuwafuwa.gaku.Network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface JpdbApi {

    @POST("/api/v1/parse")
    Call<JpdbDTOs.ParseResponse> parse(
            @Header("Authorization") String token,
            @Body JpdbDTOs.ParseRequest request);

    @POST("/api/v1/deck/add-vocabulary")
    Call<Void> addVocabulary(
            @Header("Authorization") String token,
            @Body JpdbDTOs.ModifyDeckRequest request);

    @POST("/api/v1/deck/remove-vocabulary")
    Call<Void> removeVocabulary(
            @Header("Authorization") String token,
            @Body JpdbDTOs.ModifyDeckRequest request);
}
