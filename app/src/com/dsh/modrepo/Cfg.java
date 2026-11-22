package com.dsh.modrepo;
import android.content.Context;
class Cfg {
    public static void set(android.content.Context c, String k, String v) {
        c.getSharedPreferences("modrepo", 0).edit().putString(k, v).apply();
    }
    public static String get(android.content.Context c, String k, String d) {
        return c.getSharedPreferences("modrepo", 0).getString(k, d);
    }
}

