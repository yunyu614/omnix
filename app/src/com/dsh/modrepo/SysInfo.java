package com.dsh.modrepo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import java.io.*;
import java.util.*;
import org.json.*;

/** 设备详情 / 应用列表 / 电池 / 分区 —— 全部本地读取 */
class SysInfo {

    static String read(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            String l = r.readLine();
            r.close();
            return l == null ? "" : l.trim();
        } catch (Exception e) { return ""; }
    }

    static String sh(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) { sb.append(l).append('\n'); if (sb.length() > 60000) break; }
            r.close(); p.waitFor();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /* ---------------- 设备详情 ---------------- */
    static String detail(Activity a) {
        JSONObject o = new JSONObject();
        try {
            JSONObject vendor = new JSONObject();
            vendor.put("brand", Build.BRAND);
            vendor.put("manufacturer", Build.MANUFACTURER);
            vendor.put("model", Build.MODEL);
            vendor.put("device", Build.DEVICE);
            vendor.put("product", Build.PRODUCT);
            vendor.put("board", Build.BOARD);
            vendor.put("hardware", Build.HARDWARE);
            vendor.put("fingerprint", Build.FINGERPRINT);
            o.put("vendor", vendor);

            DisplayMetrics dm = a.getResources().getDisplayMetrics();
            JSONObject screen = new JSONObject();
            screen.put("res", dm.widthPixels + " × " + dm.heightPixels);
            screen.put("dpi", dm.densityDpi);
            screen.put("density", String.format(Locale.US, "%.2f", dm.density));
            screen.put("refresh", read("/sys/class/graphics/fb0/modes"));
            String refresh = sh("dumpsys display | grep -m1 -oE 'fps=[0-9.]+'").replace("fps=", "").trim();
            screen.put("fps", refresh.isEmpty() ? "" : refresh);
            screen.put("size", sh("wm size | head -1").replace("Physical size: ", "").trim());
            o.put("screen", screen);

            JSONObject net = new JSONObject();
            try {
                ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                net.put("type", ni == null ? "无连接" : ni.getTypeName());
                net.put("state", ni == null ? "—" : ni.getState().toString());
                Network n = cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                if (lp != null) {
                    StringBuilder ips = new StringBuilder();
                    for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                        java.net.InetAddress ia = la.getAddress();
                        if (ia != null && ia.getAddress().length == 4) {
                            if (ips.length() > 0) ips.append(", ");
                            ips.append(ia.getHostAddress());
                        }
                    }
                    net.put("ip", ips.toString());
                    net.put("dns", String.valueOf(lp.getDnsServers()));
                    net.put("iface", lp.getInterfaceName());
                }
            } catch (Exception ignored) {}
            try {
                TelephonyManager tm = (TelephonyManager) a.getSystemService(Context.TELEPHONY_SERVICE);
                net.put("operator", tm == null ? "" : tm.getNetworkOperatorName());
            } catch (Exception ignored) {}
            o.put("net", net);

            JSONObject sys = new JSONObject();
            sys.put("android", Build.VERSION.RELEASE);
            sys.put("sdk", Build.VERSION.SDK_INT);
            sys.put("patch", Build.VERSION.SECURITY_PATCH);
            sys.put("build", Build.DISPLAY);
            sys.put("id", Build.ID);
            sys.put("kernel", sh("uname -r"));
            sys.put("arch", System.getProperty("os.arch", ""));
            sys.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            sys.put("bootloader", Build.BOOTLOADER);
            sys.put("selinux", sh("getenforce"));
            String up = read("/proc/uptime");
            if (!up.isEmpty()) {
                try {
                    long sec = (long) Double.parseDouble(up.split(" ")[0]);
                    sys.put("uptime", (sec / 86400) + " 天 " + (sec % 86400 / 3600) + " 小时");
                } catch (Exception ignored) {}
            }
            o.put("system", sys);

            JSONObject sto = new JSONObject();
            try {
                StatFs data = new StatFs("/data");
                long total = data.getBlockCountLong() * data.getBlockSizeLong();
                long free = data.getAvailableBlocksLong() * data.getBlockSizeLong();
                sto.put("total", gb(total));
                sto.put("free", gb(free));
            } catch (Exception ignored) {}
            String mem = read("/proc/meminfo");
            o.put("storage", sto);
            o.put("meminfo", sh("grep -E 'MemTotal|MemAvailable' /proc/meminfo").replace("\n", " · "));

            o.put("cpu", Env.cpuName());
            o.put("root", Env.hasRoot());
        } catch (Exception e) {
            try { o.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return o.toString();
    }

    static String gb(long b) {
        double g = b / 1024.0 / 1024 / 1024;
        return String.format(Locale.US, "%.1f GB", g);
    }

    /* ---------------- 应用列表 ---------------- */
    static String apps(Activity a) {
        JSONArray arr = new JSONArray();
        try {
            android.content.pm.PackageManager pm = a.getPackageManager();
            Set<String> disabled = new HashSet<>();
            for (String line : rootExec("pm list packages -d 2>/dev/null").split("\n")) {
                if (line.startsWith("package:")) disabled.add(line.substring(8).trim());
            }
            List<android.content.pm.PackageInfo> list = pm.getInstalledPackages(0);
            for (android.content.pm.PackageInfo pi : list) {
                android.content.pm.ApplicationInfo ai = pi.applicationInfo;
                if (ai == null) continue;
                boolean sys = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                JSONObject o = new JSONObject();
                o.put("pkg", pi.packageName);
                o.put("sys", sys);
                o.put("label", String.valueOf(pm.getApplicationLabel(ai)));
                o.put("ver", pi.versionName == null ? "" : pi.versionName);
                o.put("frozen", disabled.contains(pi.packageName));
                o.put("apk", ai.sourceDir == null ? "" : ai.sourceDir);
                o.put("splits", ai.splitSourceDirs == null ? 0 : ai.splitSourceDirs.length);
                arr.put(o);
            }
        } catch (Exception e) {
            try { arr.put(new JSONObject().put("error", String.valueOf(e.getMessage()))); } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** disable=true 冻结，false 启用 */
    static String freeze(String pkg, boolean disable) {
        String cmd = disable ? "pm disable-user --user 0 " : "pm enable ";
        cmd += pkg;
        String out = rootExec(cmd + " 2>&1");
        boolean ok = out.toLowerCase().contains("new state") || out.toLowerCase().contains("disabled-user")
                || out.toLowerCase().contains("enabled") || out.isEmpty();
        return (ok ? "ok" : "err") + "\u0001" + out.trim();
    }

    static String rootExec(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close(); p.waitFor();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /* ---------------- 电池 ---------------- */
    static String battery(Activity a) {
        JSONObject o = new JSONObject();
        String base = "/sys/class/power_supply/battery/";
        try {
            Intent st = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (st != null) {
                int lvl = st.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = st.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                o.put("percent", lvl > 0 ? String.valueOf(lvl * 100 / scale) : read(base + "capacity"));
                int status = st.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String[] stNames = { "未知", "充电中", "放电中", "未充电", "已充满" };
                o.put("status", status >= 0 && status < stNames.length ? stNames[status] : "未知");
                o.put("health", st.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) == 2 ? "良好" : "—");
                int temp = st.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                o.put("temp", String.format(Locale.US, "%.1f", temp / 10.0));
                o.put("voltage", String.valueOf(st.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)));
                o.put("plugged", st.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0 ? "未连接" : "已连接电源");
            }
        } catch (Exception ignored) {}
        try {
            o.put("current", fmtMicro(read(base + "current_now"), 1e6, "A"));
            o.put("power", fmtMicro(read(base + "power_now"), 1e6, "W"));
            o.put("cycles", read(base + "cycle_count"));
            o.put("chargeFull", read(base + "charge_full"));
            o.put("chargeNow", read(base + "charge_counter"));
            o.put("design", read(base + "charge_full_design"));
            o.put("tech", read("/sys/class/power_supply/battery/technology"));
            o.put("healthDetail", read(base + "health"));
            o.put("tempRaw", read(base + "temp"));
        } catch (Exception ignored) {}
        return o.toString();
    }

    static String fmtMicro(String v, double div, String unit) {
        try { return String.format(Locale.US, "%.2f %s", Double.parseDouble(v.trim()) / div, unit); }
        catch (Exception e) { return ""; }
    }

    /* ---------------- 分区 ---------------- */
    static String partitions() {
        JSONArray arr = new JSONArray();
        Map<String, Long> sizes = new HashMap<>();
        for (String line : rootExec("cat /proc/partitions").split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length >= 4 && f[0].matches("\\d+")) {
                try { sizes.put(f[3], Long.parseLong(f[2])); } catch (Exception ignored) {}
            }
        }
        String ls = rootExec("ls -l /dev/block/by-name 2>/dev/null");
        for (String line : ls.split("\n")) {
            int arrow = line.indexOf("->");
            if (arrow < 0) continue;
            String name = line.substring(0, arrow).trim();
            if (name.contains(" ")) name = name.substring(name.lastIndexOf(' ') + 1);
            String target = line.substring(arrow + 2).trim();
            String base = target.substring(target.lastIndexOf('/') + 1);
            Long kb = sizes.get(base);
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                String slot = "";
                String baseName = name;
                if (name.endsWith("_a") || name.endsWith("_b")) {
                    slot = name.substring(name.length() - 1);
                    baseName = name.substring(0, name.length() - 2);
                }
                o.put("slot", slot);
                o.put("base", baseName);
                o.put("dev", target);
                o.put("size", kb == null ? "" : fmtSize(kb));
                arr.put(o);
            } catch (Exception ignored) {}
        }
        if (arr.length() == 0) {
            try { arr.put(new JSONObject().put("name", "（需要 root 读取 /dev/block/by-name）").put("dev", "").put("size", "")); }
            catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** 导出一个分区到 Download/ModRepo/partitions/ */
    static String exportPart(String name, String dev) {
        String dir = "/storage/emulated/0/Download/ModRepo/partitions";
        String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date());
        String out = dir + "/" + name + "-" + ts + ".img";
        String r = rootExec("mkdir -p '" + dir + "' && dd if='" + dev + "' of='" + out + "' bs=4M 2>&1; ls -l '" + out + "'");
        boolean ok = r.contains(out) && !r.toLowerCase().contains("no such");
        return (ok ? "ok" : "err") + "\u0001" + out + "\u0001" + r.trim();
    }

    /** 把镜像刷入分区（危险操作，调用方需二次确认） */
    static String flashPart(String dev, String img) {
        String r = rootExec("dd if='" + img + "' of='" + dev + "' bs=4M 2>&1; sync; echo DONE");
        boolean ok = r.contains("DONE") && !r.toLowerCase().contains("no space")
                && !r.toLowerCase().contains("permission denied");
        return (ok ? "ok" : "err") + "\u0001" + r.trim();
    }

    static final String[] KEY_PARTS = { "persist", "modemst1", "modemst2", "fsg", "fsc",
            "nvram", "nvdata", "nvcfg", "modem", "bluetooth", "misc", "dtbo", "vbmeta" };
    static final String[] FULL_SKIP = { "userdata", "super", "metadata", "cache", "data", "system", "sda", "sdb", "sdc", "sdd", "sde", "sdf", "sdg", "sdh", "sdi", "sdj",
            "vendor", "product", "system_ext", "odm", "my_product", "my_heytap", "my_stock",
            "my_carrier", "my_manifest", "my_bigball", "my_engineering", "my_preload", "my_version" };

    /** 备份清单：mode=key 关键数据，mode=full 完整（除大分区） */
    static java.util.List<String> backupList(String mode) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if ("key".equals(mode)) {
            for (String n : KEY_PARTS) out.add(n);
        } else {
            for (String name : rootExec("ls /dev/block/by-name 2>/dev/null").split("\n")) {
                name = name.trim();
                if (name.isEmpty()) continue;
                String base = name.replaceAll("_[ab]$", "");
                boolean skip = false;
                for (String s : FULL_SKIP) if (s.equals(base)) { skip = true; break; }
                // 跳过整盘节点（sdX / mmcblk0 / dm-N）
                if (!skip && (base.matches("sd[a-z]+") || base.matches("mmcblk\\d+") || base.matches("dm-\\d+"))) skip = true;
                if (!skip) out.add(name);
            }
        }
        return out;
    }

    /** 预估占用：一次 shell 调用解析符号链接，再与 /proc/partitions 对齐 */
    static String fontEstimate(String mode) {
        JSONObject o = new JSONObject();
        JSONArray items = new JSONArray();
        long total = 0;
        try {
            // 设备名 -> 字节数
            Map<String, Long> sizes = new HashMap<>();
            for (String line : rootExec("cat /proc/partitions").split("\n")) {
                String[] f = line.trim().split("\\s+");
                if (f.length >= 4 && f[0].matches("\\d+")) {
                    try { sizes.put(f[3], Long.parseLong(f[2]) * 1024L); } catch (Exception ignored) {}
                }
            }
            java.util.List<String> names = backupList(mode);
            StringBuilder cmd = new StringBuilder("for n in");
            for (String n : names) cmd.append(' ').append(n);
            cmd.append("; do l=$(readlink -f /dev/block/by-name/$n 2>/dev/null); echo \"$n|${l##*/}\"; done");
            for (String line : rootExec(cmd.toString()).split("\n")) {
                line = line.trim();
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                String name = line.substring(0, bar);
                String dev = line.substring(bar + 1).trim();
                Long sz = sizes.get(dev);
                if (sz == null || sz > 4L * 1024 * 1024 * 1024) continue;
                total += sz;
                items.put(new JSONObject().put("name", name).put("bytes", sz).put("size", fmtBytes(sz)));
            }
            o.put("count", items.length());
            o.put("bytes", total);
            o.put("size", fmtBytes(total));
            o.put("items", items);
        } catch (Exception ignored) {}
        return o.toString();
    }

    static String fmtBytes(long b) {
        if (b >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.2f GB", b / 1024.0 / 1024 / 1024);
        if (b >= 1024L * 1024) return String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024);
        return String.format(Locale.US, "%.0f KB", b / 1024.0);
    }

    /** 字库备份：mode=key 关键数据 / full 完整 */
    static String fontBackup(String mode) {
        java.util.List<String> parts = backupList(mode);
        String modeDir = "full".equals(mode) ? "full" : "key";
        String dir = "/storage/emulated/0/Download/ModRepo/font-store/" + modeDir + "/"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date());
        StringBuilder log = new StringBuilder();
        int okCount = 0;
        for (String name : parts) {
            String dev = "/dev/block/by-name/" + name;
            String chk = rootExec("test -e '" + dev + "' && echo YES || echo NO").trim();
            if (!chk.contains("YES")) { log.append(name).append(": 不存在\n"); continue; }
            String r = rootExec("mkdir -p '" + dir + "' && dd if='" + dev + "' of='" + dir + "/" + name + ".img' bs=4M 2>&1 && echo OK");
            if (r.contains("OK")) { okCount++; log.append(name).append(": 已备份\n"); }
            else log.append(name).append(": 失败\n");
        }
        return (okCount > 0 ? "ok" : "err") + "\u0001" + dir + "\u0001" + log.toString().trim();
    }

    /** 已安装 Root 模块（KSU / Magisk / APatch 通用 /data/adb/modules） */
    static String modules() {
        JSONArray arr = new JSONArray();
        String cmd = "for d in /data/adb/modules/*/; do "
            + "id=$(basename \"$d\"); p=\"$d/module.prop\"; "
            + "n=$(sed -n 's/^name=//p' \"$p\" 2>/dev/null | head -1); "
            + "v=$(sed -n 's/^version=//p' \"$p\" 2>/dev/null | head -1); "
            + "vc=$(sed -n 's/^versionCode=//p' \"$p\" 2>/dev/null | head -1); "
            + "a=$(sed -n 's/^author=//p' \"$p\" 2>/dev/null | head -1); "
            + "desc=$(sed -n 's/^description=//p' \"$p\" 2>/dev/null | head -1); "
            + "sz=$(du -sk \"$d\" 2>/dev/null | cut -f1); "
            + "e=1; [ -f \"$d/disable\" ] && e=0; "
            + "r=0; [ -f \"$d/remove\" ] && r=1; "
            + "w=0; [ -f \"$d/webroot/index.html\" ] && w=1; "
            + "ac=0; [ -f \"$d/action.sh\" ] && ac=1; "
            + "echo \"$id|$n|$v|$vc|$a|$e|$sz|$r|$w|$ac|$desc\"; done";
        for (String line : rootExec(cmd).split("\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("|")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 10) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("kind", "root");
                o.put("id", f[0]);
                o.put("name", f[1].isEmpty() ? f[0] : f[1]);
                o.put("ver", f[2]);
                o.put("verCode", f[3]);
                o.put("author", f[4]);
                o.put("enabled", "1".equals(f[5]));
                o.put("size", f[6].isEmpty() ? "" : f[6] + " KB");
                o.put("pending", "1".equals(f[7]));
                o.put("webroot", "1".equals(f[8]));
                o.put("action", "1".equals(f[9]));
                o.put("desc", f.length > 10 ? f[10] : "");
                o.put("dir", "/data/adb/modules/" + f[0]);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** LSPosed 模块：以 lspd 数据库里记录的模块 APK 路径为准 */
    static String xposedModules(Activity a) {
        JSONArray arr = new JSONArray();
        try {
            java.util.Set<String> paths = new java.util.HashSet<>();
            String dump = rootExec("strings /data/adb/lspd/config/modules_config.db 2>/dev/null | grep -oE '/data/(app|priv-app)/[^ ]*\\.apk' | sort -u");
            for (String l : dump.split("\n")) { l = l.trim(); if (!l.isEmpty()) paths.add(l); }

            android.content.pm.PackageManager pm = a.getPackageManager();
            for (android.content.pm.PackageInfo pi : pm.getInstalledPackages(0)) {
                android.content.pm.ApplicationInfo ai = pi.applicationInfo;
                if (ai == null || ai.sourceDir == null) continue;
                boolean hit = paths.contains(ai.sourceDir);
                if (!hit) for (String p : paths) if (p.equals(ai.sourceDir)) { hit = true; break; }
                // 兜底：APK 自身声明了 xposed meta-data 也算
                if (!hit && ai.metaData != null) {
                    for (String k : ai.metaData.keySet()) {
                        if (k.toLowerCase().contains("xposed")) { hit = true; break; }
                    }
                }
                if (!hit) continue;
                JSONObject o = new JSONObject();
                o.put("kind", "xposed");
                o.put("id", pi.packageName);
                o.put("name", String.valueOf(pm.getApplicationLabel(ai)));
                o.put("ver", pi.versionName == null ? "" : pi.versionName);
                o.put("author", "");
                o.put("enabled", true);
                long sz = new java.io.File(ai.sourceDir).length();
                o.put("size", sz > 0 ? (sz / 1024) + " KB" : "");
                o.put("pending", false);
                o.put("webroot", false);
                o.put("action", false);
                o.put("desc", ai.metaData != null && ai.metaData.containsKey("xposeddescription")
                        ? String.valueOf(ai.metaData.get("xposeddescription")) : "");
                o.put("dir", ai.sourceDir);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        return arr.toString();
    }

    /** 导出应用安装包到 Download/ModRepo/appbackup/ ；有 split 时建目录一并导出 */
    static String exportApps(Activity a, String pkgsCsv) {
        String root = "/storage/emulated/0/Download/ModRepo/appbackup";
        StringBuilder log = new StringBuilder();
        int okCount = 0;
        android.content.pm.PackageManager pm = a.getPackageManager();
        for (String pkg : pkgsCsv.split(",")) {
            pkg = pkg.trim();
            if (pkg.isEmpty()) continue;
            try {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, 0);
                android.content.pm.ApplicationInfo ai = pi.applicationInfo;
                String label = String.valueOf(pm.getApplicationLabel(ai)).replaceAll("[\\\\/:*?\"<>|]", "_");
                String ver = pi.versionName == null ? "0" : pi.versionName;
                String base = root + "/" + label + "-" + pkg + "-" + ver;
                java.io.File src = new java.io.File(ai.sourceDir);
                String dest;
                if (ai.splitSourceDirs != null && ai.splitSourceDirs.length > 0) {
                    dest = base + "/base.apk";
                    rootExec("mkdir -p '" + base + "'");
                    rootExec("cat '" + ai.sourceDir + "' > '" + dest + "'");
                    for (String sp : ai.splitSourceDirs) {
                        String nm = sp.substring(sp.lastIndexOf('/') + 1);
                        rootExec("cat '" + sp + "' > '" + base + "/" + nm + "'");
                    }
                    dest = base + "/";
                } else {
                    dest = base + ".apk";
                    rootExec("mkdir -p '" + root + "' && cat '" + ai.sourceDir + "' > '" + dest + "'");
                }
                okCount++;
                log.append(label).append(" → ").append(dest).append("\n");
            } catch (Exception e) {
                log.append(pkg).append(": 失败 ").append(e.getMessage()).append("\n");
            }
        }
        return (okCount > 0 ? "ok" : "err") + "\u0001" + root + "\u0001" + log.toString().trim();
    }

    /** 卸载：root 模块删目录，xposed 模块卸载 APK */
    /** 禁用/启用模块：优先 ksud，其次 Magisk 的 disable 文件 */
    static String setModuleEnabled(String id, boolean enabled) {
        if (id == null || id.contains("/") || id.contains("..")) return "err\u0001非法 ID";
        String out;
        if (!rootExec("which ksud 2>/dev/null").trim().isEmpty()) {
            out = rootExec("ksud module " + (enabled ? "enable " : "disable ") + id + " 2>&1; echo DONE");
        } else {
            String dir = "/data/adb/modules/" + id + "/disable";
            out = rootExec((enabled ? "rm -f '" + dir + "'" : "touch '" + dir + "'") + " && echo DONE");
        }
        return (out.contains("DONE") ? "ok" : "err") + "\u0001" + out.trim();
    }

    /** 运行模块 action.sh */
    static String moduleAction(String id) {
        String out;
        if (!rootExec("which ksud 2>/dev/null").trim().isEmpty())
            out = rootExec("ksud module action " + id + " 2>&1; echo DONE");
        else
            out = rootExec("sh /data/adb/modules/" + id + "/action.sh 2>&1; echo DONE");
        return (out.contains("DONE") ? "ok" : "err") + "\u0001" + out.trim();
    }

    static String uninstallModule(String id, String kind) {
        if (id == null || id.isEmpty() || id.contains("..") || id.contains("/") || id.contains(" "))
            return "err\u0001非法标识";
        String out = "xposed".equals(kind)
                ? rootExec("pm uninstall --user 0 " + id + " 2>&1; echo DONE")
                : rootExec("rm -rf '/data/adb/modules/" + id + "' && echo DONE");
        boolean ok = out.contains("Success") || (out.contains("DONE") && !out.toLowerCase().contains("failure"));
        return (ok ? "ok" : "err") + "\u0001" + out.trim();
    }

    /** A/B 槽位信息 */
    static String slotInfo() {
        JSONObject o = new JSONObject();
        try {
            String suffix = rootExec("getprop ro.boot.slot_suffix").trim();
            String abProp = rootExec("getprop ro.build.ab_update").trim();
            String cnt = rootExec("ls /dev/block/by-name 2>/dev/null | grep -cE '_(a|b)$'").trim();
            int slots = 0;
            try { slots = Integer.parseInt(cnt); } catch (Exception ignored) {}
            o.put("suffix", suffix);
            o.put("slot", suffix.replace("_", ""));
            o.put("ab", !suffix.isEmpty() || "true".equals(abProp) || slots > 0);
            o.put("count", slots);
        } catch (Exception ignored) {}
        return o.toString();
    }

    static String fmtSize(long kb) {
        double b = kb * 1024.0;
        if (b >= 1024.0 * 1024 * 1024) return String.format(Locale.US, "%.2f GB", b / 1024 / 1024 / 1024);
        if (b >= 1024.0 * 1024) return String.format(Locale.US, "%.1f MB", b / 1024 / 1024);
        return String.format(Locale.US, "%.0f KB", b / 1024);
    }
}
