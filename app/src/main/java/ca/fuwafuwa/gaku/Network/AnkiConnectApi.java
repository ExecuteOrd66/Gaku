package ca.fuwafuwa.gaku.Network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AnkiConnectApi {
    @POST("/")
    Call<AnkiConnectDTOs.Response<Object>> request(@Body AnkiConnectDTOs.Request request);
}
