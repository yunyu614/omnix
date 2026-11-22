package com.dsh.modrepo;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** 网络：统一 Chrome UA（绕 Cloudflare），支持进度回调下载 */
public class Net {
    static final String UA = "Mozilla/5.0 (Linux; Android 14; M2101K9C) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    static String slurp(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
            if (bo.size() > limit) break;
        }
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 返回 "code\u0001body" */
    public static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : slurp(in, 6 * 1024 * 1024);
            return code + "\u0001" + body;
        } catch (Exception e) {
            return "0\u0001" + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** POST JSON：返回 "code\u0001body" */
    public static String post(String url, String body, String ctype) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", ctype == null ? "application/json" : ctype);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String resp = in == null ? "" : slurp(in, 2 * 1024 * 1024);
            return code + "\u0001" + resp;
        } catch (Exception e) {
            return "0\u0001" + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public interface Progress { void on(long done, long total); }

    /** 下载到本地文件，返回 "ok\u0001path\u0001size" 或 "fail\u0001原因" */
    public static String download(String url, File dest, Progress cb) {
        HttpURLConnection c = null;
        try {
            dest.getParentFile().mkdirs();
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code >= 400) return "fail\u0001HTTP " + code;
            long total = c.getContentLengthLong();
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            long last = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (cb != null && (done - last > 262144 || done == total)) {
                    last = done;
                    cb.on(done, total);
                }
            }
            out.close();
            in.close();
            return "ok\u0001" + dest.getAbsolutePath() + "\u0001" + done;
        } catch (Exception e) {
            return "fail\u0001" + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
