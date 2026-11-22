package com.dsh.modrepo;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import java.io.*;
import org.json.JSONObject;

/** 设备 + Root 环境快照（首页顶部卡片） */
class Env {

    static String sh(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) {
                if (sb.length() > 4000) break;
                sb.append(l).append('\n');
            }
            r.close();
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) { return ""; }
    }

    /** su 可执行且能拿到 uid=0；超时 4s 避免管理器授权弹窗卡住 */
    static boolean hasRoot() {
        final StringBuilder sb = new StringBuilder();
        Thread t = new Thread(new Runnable() { public void run() {
            try {
                Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String l;
                while ((l = r.readLine()) != null) { sb.append(l); break; }
                r.close(); p.waitFor();
            } catch (Exception ignored) {}
        }});
        t.setDaemon(true);
        t.start();
        try { t.join(4000); } catch (InterruptedException ignored) {}
        return sb.toString().contains("uid=0");
    }

    static boolean suExists() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/system/bin/ksud"};
        for (String p : paths) if (new File(p).exists()) return true;
        return !sh("which su 2>/dev/null").isEmpty();
    }

    static String ver(Activity a, String pkg) {
        try {
            return a.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) { return ""; }
    }

    static JSONObject safe() { return new JSONObject(); }

    static String snapshot(Activity a) {
        JSONObject o = new JSONObject();
        try {
            // 设备
            o.put("brand", Build.BRAND == null ? "" : cap(Build.BRAND));
            o.put("model", Build.MODEL == null ? "" : Build.MODEL);
            o.put("device", Build.DEVICE == null ? "" : Build.DEVICE);
            o.put("android", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
            o.put("sdk", String.valueOf(Build.VERSION.SDK_INT));
            o.put("rom", (Build.DISPLAY == null ? "" : Build.DISPLAY));
            o.put("arch", System.getProperty("os.arch", ""));
            o.put("abi", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            o.put("kernel", sh("uname -r"));

            DisplayMetrics dm = a.getResources().getDisplayMetrics();
            o.put("screen", dm.widthPixels + " × " + dm.heightPixels);
            o.put("density", String.valueOf(dm.densityDpi));

            String mem = sh("grep MemTotal /proc/meminfo");
            String kb = mem.replaceAll("[^0-9]", "");
            if (kb.length() > 0) {
                long mb = Long.parseLong(kb) / 1024;
                o.put("ram", (mb / 1024.0 >= 1 ? String.format(java.util.Locale.US, "%.0f GB", mb / 1024.0) : mb + " MB"));
            }
            o.put("cpu", cpuName());

            // root
            boolean root = hasRoot();
            o.put("root", root);
            o.put("su", suExists());

            // 管理器探测
            String[][] mgrs = {
                {"KernelSU", "me.weishu.kernelsu"},
                {"KernelSU-Next", "com.rifsxd.ksunext"},
                {"SukiSU Ultra", "com.sukisu.ultra"},
                {"Magisk", "com.topjohnwu.magisk"},
                {"Magisk Alpha", "io.github.vvb2060.magisk"},
                {"APatch", "com.blink.apatch"},
            };
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String[] m : mgrs) {
                String v = ver(a, m[1]);
                if (v != null && v.length() > 0) {
                    arr.put(new JSONObject().put("name", m[0]).put("pkg", m[1]).put("ver", v)
                            .put("icon", appIcon(a, m[1])));
                }
            }
            if (arr.length() == 0 && root) {
                // 无管理器 App 时看 root 实现目录
                if (!sh("ls /data/adb/ksu 2>/dev/null").isEmpty()) arr.put(new JSONObject().put("name", "KernelSU").put("pkg", "").put("ver", "已加载"));
                else if (!sh("ls /data/adb/ap 2>/dev/null").isEmpty()) arr.put(new JSONObject().put("name", "APatch").put("pkg", "").put("ver", "已加载"));
                else if (!sh("ls /data/adb/magisk 2>/dev/null").isEmpty()) arr.put(new JSONObject().put("name", "Magisk").put("pkg", "").put("ver", "已加载"));
            }
            o.put("managers", arr);
            o.put("selinux", sh("getenforce"));
            try {
                int ui = android.content.res.Resources.getSystem().getConfiguration().uiMode;
                o.put("dark", (ui & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            } catch (Exception ignored) {}
            o.put("ksu", sh("cat /data/adb/ksu/version 2>/dev/null || true"));
        } catch (Exception e) {
            try { o.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return o.toString();
    }

    static String cpuName() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String l;
            String hw = "";
            while ((l = r.readLine()) != null) {
                if (l.startsWith("Hardware")) hw = l.substring(l.indexOf(':') + 1).trim();
                if (l.startsWith("model name")) hw = l.substring(l.indexOf(':') + 1).trim();
            }
            r.close();
            if (hw.length() > 0) return hw;
        } catch (Exception ignored) {}
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            return abis != null && abis.length > 0 ? abis[0] : "";
        } catch (Exception e) { return ""; }
    }

    /** 取应用图标转 base64 PNG，用于首页展示当前管理器图标 */
    static String appIcon(Activity a, String pkg) {
        try {
            android.graphics.drawable.Drawable d = a.getPackageManager().getApplicationIcon(pkg);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    96, 96, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, 96, 96);
            d.draw(c);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, bo);
            return android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    static String cap(String s) {
        if (s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
