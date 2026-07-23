//Micah Chen & Archie Phyo
package edu.calpoly.csc364.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Json {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private Json() { }
    public static String encode(Object value) { return GSON.toJson(value); }
    public static <T> T decode(String json, Class<T> type) { return GSON.fromJson(json, type); }
}
