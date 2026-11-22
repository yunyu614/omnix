package com.dsh.modrepo;

import android.app.Activity;
import java.io.*;
import java.util.*;
import org.json.*;

/** 机型伪装：读取/写入系统属性（走 root + resetprop），支持备份与还原 */
class Spoof {
    static final String DIR = "/data/adb/omnix";
    static final String BAK = DIR + "/spoof-backup.json";

    static final String[] KEYS = {
        "ro.product.brand", "ro.product.model", "ro.product.device",
        "ro.product.manufacturer", "ro.product.name", "ro.product.board",
        "ro.build.fingerprint", "ro.build.display.id", "ro.build.description",
        "ro.build.version.incremental", "ro.system.build.version.incremental",
        "ro.board.platform", "ro.soc.model", "ro.product.marketname"
    };

    static String root(String cmd) { return SysTune.root(cmd); }

    /** 读取当前属性 */
    static String props() {
        JSONObject o = new JSONObject();
        StringBuilder cmd = new StringBuilder("P=/data/adb/ksu/bin/resetprop; [ -x \"$P\" ] || P=resetprop; ");
        for (String k : KEYS) cmd.append("echo \"").append(k).append("=;$(").append("$P ").append(k).append(" 2>/dev/null)\"; ");
        for (String line : root(cmd.toString()).split("\n")) {
            int i = line.indexOf("=;");
            if (i <= 0) continue;
            try { o.put(line.substring(0, i), line.substring(i + 2)); } catch (Exception ignored) {}
        }
        return o.toString();
    }

    /** 是否处于伪装状态 */
    static boolean masked() {
        return !root("test -f " + BAK + " && echo YES || echo NO").trim().contains("NO");
    }

    /** 应用伪装：先把原始值备份，再逐项写入 */
    static String apply(String json) {
        try {
            JSONObject want = new JSONObject(json);
            JSONObject bak = new JSONObject();
            if (!masked()) {
                bak = new JSONObject(props());          // 只备份首次的原值
                root("mkdir -p " + DIR + " && echo '" + bak.toString().replace("'", "") + "' > " + BAK);
            } else {
                String old = root("cat " + BAK).trim();
                if (!old.isEmpty()) bak = new JSONObject(old);
            }
            StringBuilder cmd = new StringBuilder("P=/data/adb/ksu/bin/resetprop; [ -x \"$P\" ] || P=resetprop; ");
            for (String k : KEYS) {
                if (!want.has(k)) continue;
                String v = want.optString(k, "");
                if (v.isEmpty()) continue;
                cmd.append("$P ").append(k).append(" '").append(v.replace("'", "")).append("'; ");
            }
            root(cmd.toString());
            // 回读校验
            JSONObject now = new JSONObject(props());
            int ok = 0, total = 0;
            for (String k : KEYS) {
                if (!want.has(k) || want.optString(k, "").isEmpty()) continue;
                total++;
                if (want.optString(k).equals(now.optString(k))) ok++;
            }
            return (ok > 0 ? "ok" : "err") + "\u0001" + "已写入 " + ok + "/" + total + " 项属性";
        } catch (Exception e) { return "err\u0001" + e.getMessage(); }
    }

    /** 还原为备份的原始值 */
    static String restore() {
        String raw = root("cat " + BAK).trim();
        if (raw.isEmpty()) return "err\u0001没有找到伪装备份";
        try {
            JSONObject bak = new JSONObject(raw);
            StringBuilder cmd = new StringBuilder("P=/data/adb/ksu/bin/resetprop; [ -x \"$P\" ] || P=resetprop; ");
            for (String k : KEYS) {
                String v = bak.optString(k, "");
                if (v.isEmpty()) cmd.append("$P --delete ").append(k).append(" 2>/dev/null; ");
                else cmd.append("$P ").append(k).append(" '").append(v.replace("'", "")).append("'; ");
            }
            cmd.append("rm -f ").append(BAK).append("; ");
            root(cmd.toString());
            return "ok\u0001已还原原始属性";
        } catch (Exception e) { return "err\u0001备份解析失败"; }
    }
}
