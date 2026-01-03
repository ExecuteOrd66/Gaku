package ca.fuwafuwa.gaku.Network;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class AnkiConnectDTOs {

    public static class Request {
        @SerializedName("action")
        public String action;

        @SerializedName("version")
        public int version = 6;

        @SerializedName("params")
        public Map<String, Object> params;

        public Request(String action, Map<String, Object> params) {
            this.action = action;
            this.params = params;
        }
    }

    public static class Response<T> {
        @SerializedName("result")
        public T result;

        @SerializedName("error")
        public String error;
    }
}
