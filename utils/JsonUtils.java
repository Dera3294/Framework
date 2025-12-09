package framework.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonUtils {

    // 🔹 Configuration améliorée de Gson :
    //    - setPrettyPrinting() : rend le JSON lisible
    //    - serializeNulls() : inclut les valeurs null
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }
}
