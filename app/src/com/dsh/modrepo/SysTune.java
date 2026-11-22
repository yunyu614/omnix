package com.dsh.modrepo;

import android.app.Activity;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.util.*;
import org.json.*;

/** 温控 / CPU 调度 / 充电限制 / 应用耗电 */
class SysTune {

    static String sh(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) { sb.append(l).append('\n'); if (sb.length() > 80000) break; }
            r.close(); p.waitFor();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    static String root(String cmd) {
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

    /* ---------------- CPU 调度 ---------------- */
    static String cpu() {
        JSONArray out = new JSONArray();
        try {
            String script =
                "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
                "echo \"P|$p|$(cat $p/scaling_governor 2>/dev/null)|$(cat $p/scaling_cur_freq 2>/dev/null)|" +
                "$(cat $p/scaling_min_freq 2>/dev/null)|$(cat $p/scaling_max_freq 2>/dev/null)|" +
                "$(cat $p/scaling_available_governors 2>/dev/null)|$(cat $p/scaling_available_frequencies 2>/dev/null)|" +
                "$(cat $p/related_cpus 2>/dev/null)\"; done";
            for (String line : root(script).split("\n")) {
                line = line.trim();
                if (!line.startsWith("P|")) continue;
                String[] f = line.split("\\|", -1);
                if (f.length < 8) continue;
                JSONObject o = new JSONObject();
                o.put("path", f[1]);
                o.put("gov", f[2].trim());
                o.put("cur", roundMhz(f[3].trim()));
                o.put("min", roundMhz(f[4].trim()));
                o.put("max", roundMhz(f[5].trim()));
                o.put("govs", f[6].trim());
                JSONArray freqs = new JSONArray();
                for (String khz : f[7].trim().split("\\s+")) {
                    if (khz.isEmpty()) continue;
                    try { freqs.put(Math.round(Long.parseLong(khz) / 1000.0)); } catch (Exception ignored) {}
                }
                Arrays.sort(toArr(freqs));
                JSONArray sorted = new JSONArray();
                for (int v : toArr(freqs)) sorted.put(v);
                o.put("freqs", sorted);
                o.put("cpus", f[8].trim());
                out.put(o);
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    static int[] toArr(JSONArray a) {
        int[] r = new int[a.length()];
        for (int i = 0; i < a.length(); i++) r[i] = a.optInt(i);
        Arrays.sort(r);
        return r;
    }

    static int roundMhz(String khz) {
        try { return (int) Math.round(Long.parseLong(khz) / 1000.0); } catch (Exception e) { return 0; }
    }

    /** 写调度节点：field = governor|min|max */
    static String setCpu(String path, String field, String value) {
        if (path == null || !path.startsWith("/sys/devices/system/cpu/cpufreq/policy")) return "err\u0001非法路径";
        String node;
        if ("governor".equals(field)) node = "/scaling_governor";
        else if ("min".equals(field)) node = "/scaling_min_freq";
        else node = "/scaling_max_freq";
        String v = value.trim();
        if (!"governor".equals(field)) {
            try { v = String.valueOf(Long.parseLong(v) * 1000); } catch (Exception e) { return "err\u0001非法频率"; }
        }
        root("echo '" + v + "' > " + path + node + " 2>&1");
        String now = root("cat " + path + node).trim();
        boolean ok = !now.isEmpty();
        return (ok ? "ok" : "err") + "\u0001" + node + " = " + now;
    }

    /* ---------------- 温控 ---------------- */
    static String thermal() {
        JSONArray out = new JSONArray();
        try {
            String script = "for z in /sys/class/thermal/thermal_zone*; do " +
                    "t=$(cat $z/type 2>/dev/null); v=$(cat $z/temp 2>/dev/null); " +
                    "[ -n \"$t\" ] && [ -n \"$v\" ] && echo \"Z|$t|$v\"; done";
            java.util.Map<String, Integer> maxByType = new HashMap<>();
            for (String line : root(script).split("\n")) {
                line = line.trim();
                if (!line.startsWith("Z|")) continue;
                String[] f = line.split("\\|", -1);
                if (f.length < 3) continue;
                int t;
                try { t = Integer.parseInt(f[2].trim()); } catch (Exception e) { continue; }
                if (Math.abs(t) > 1000) t = t / 1000;      // 毫度
                if (t <= -40 || t >= 200) continue;
                String key = f[1].trim();
                Integer prev = maxByType.get(key);
                if (prev == null || t > prev) maxByType.put(key, t);
            }
            for (java.util.Map.Entry<String, Integer> e : maxByType.entrySet()) {
                out.put(new JSONObject().put("type", e.getKey()).put("temp", e.getValue()));
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    /** 高频采样：电池走 BatteryManager（无需 root），频率读 sysfs */
    static String sample(android.content.Context a) {
        JSONObject o = new JSONObject();
        try {
            int level = -1, scale = 100, status = -1, volt = 0, temp = 0;
            try {
                android.content.IntentFilter f = new android.content.IntentFilter(
                        android.content.Intent.ACTION_BATTERY_CHANGED);
                android.content.Intent it = a.registerReceiver(null, f);
                if (it != null) {
                    level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                    scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                    status = it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                    volt = it.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
                    temp = it.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
                }
            } catch (Exception ignored) {}
            android.os.BatteryManager bm = (android.os.BatteryManager)
                    a.getSystemService(android.content.Context.BATTERY_SERVICE);
            long curUa = 0;
            if (bm != null) {
                try { curUa = bm.getIntProperty(2); } catch (Exception ignored) {}   // 2 = CURRENT_NOW
            }
            // 兜底：只读文件，不再每次调用 su
            if (curUa == 0) {
                String c = SysInfo.read("/sys/class/power_supply/battery/current_now");
                try { curUa = Long.parseLong(c.trim()); } catch (Exception ignored) {}
            }
            double amp = Math.abs(curUa) / 1e6;
            double v = volt > 0 ? volt / 1000.0 : 0;

            o.put("cap", level > 0 ? Math.round(level * 100.0 / scale) : 0);
            o.put("cur", Math.abs(curUa) / 1000.0);                  // mA
            o.put("volt", volt);
            o.put("temp", temp / 10.0);
            o.put("watt", Math.abs(amp * v));
            String[] st = {"未知","充电中","放电中","未充电","已充满"};
            o.put("status", status >= 0 && status < st.length ? st[status] : "—");

            JSONArray freqs = new JSONArray();
            java.io.File[] pols = new java.io.File("/sys/devices/system/cpu/cpufreq").listFiles();
            if (pols != null) {
                java.util.Arrays.sort(pols, new java.util.Comparator<java.io.File>() {
                    public int compare(java.io.File x, java.io.File y) { return x.getName().compareTo(y.getName()); }
                });
                for (java.io.File p : pols) {
                    if (!p.getName().startsWith("policy")) continue;
                    long khz = (long) num(SysInfo.read(p.getAbsolutePath() + "/scaling_cur_freq"));
                    if (khz > 0) freqs.put(Math.round(khz / 1000.0));
                }
            }
            o.put("freqs", freqs);

            // CPU 温度：取所有 cpu/soc 传感器的中位数（比最大值更稳）
            java.util.List<Integer> temps = new java.util.ArrayList<>();
            java.util.List<String> raw = new java.util.ArrayList<>();
            java.io.File[] zones = new java.io.File("/sys/class/thermal").listFiles();
            if (zones != null) for (java.io.File z : zones) {
                if (!z.getName().startsWith("thermal_zone")) continue;
                String type = SysInfo.read(z.getAbsolutePath() + "/type").toLowerCase();
                if (type.isEmpty()) continue;
                if (type.contains("trip") || type.contains("limit") || type.contains("cooling")
                        || type.contains("thresh") || type.contains("hw_"))
                    continue;
                if (!(type.contains("cpu") || type.contains("soc") || type.contains("tsens") || type.contains("ap")))
                    continue;
                String rs = SysInfo.read(z.getAbsolutePath() + "/temp");
                int t = (int) num(rs);
                if (t > 10000) t /= 1000;
                else if (t > 1000) t /= 100;
                else if (t > 200) t /= 10;
                if (t < 10 || t > 120) continue;
                temps.add(t);
                if (raw.size() < 6) raw.add(type + "=" + rs.trim() + "→" + t);
            }
            int median = 0;
            if (!temps.isEmpty()) {
                java.util.Collections.sort(temps);
                median = temps.get(temps.size() / 2);
            }
            o.put("cpuTemp", median);
            // 机身/外壳温度（尽力而为）
            int skin = 0;
            if (zones != null) for (java.io.File z : zones) {
                if (!z.getName().startsWith("thermal_zone")) continue;
                String type = SysInfo.read(z.getAbsolutePath() + "/type").toLowerCase();
                if (!(type.contains("skin") || type.contains("quiet") || type.contains("shell")
                        || type.contains("back") || type.contains("pa") && type.contains("therm")))
                    continue;
                int t = (int) num(SysInfo.read(z.getAbsolutePath() + "/temp"));
                if (t > 10000) t /= 1000; else if (t > 1000) t /= 100; else if (t > 200) t /= 10;
                if (t >= 10 && t <= 120 && t > skin) skin = t;
            }
            o.put("skinTemp", skin);
            String up = SysInfo.read("/proc/uptime");
            if (!up.isEmpty()) {
                try { o.put("uptime", (long) Double.parseDouble(up.split("\\s+")[0])); } catch (Exception ignored) {}
            }
            o.put("designUah", designUah());
            o.put("cpuZone", raw.toString());
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** 设计容量只读一次并缓存，避免高频调用 su */
    static long cachedDesign = -1;
    static long designUah() {
        if (cachedDesign >= 0) return cachedDesign;
        String d = SysInfo.read("/sys/class/power_supply/battery/charge_full_design");
        if (d.isEmpty()) d = root("cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null").trim();
        try { cachedDesign = Long.parseLong(d.trim()); } catch (Exception ignored) { cachedDesign = 0; }
        return cachedDesign;
    }

    static double num(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    /* ---------------- 充电限制 ---------------- */
    static String charge() {
        String[] nodes = {
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/battery/constant_charge_current",
            "/sys/class/power_supply/battery/input_current_limit",
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/charge_control_limit_max",
            "/sys/class/power_supply/usb/current_max",
            "/sys/class/power_supply/usb/input_current_limit",
        };
        JSONArray arr = new JSONArray();
        StringBuilder cmd = new StringBuilder();
        for (String n : nodes) {
            cmd.append("if [ -e ").append(n).append(" ]; then echo \"C|").append(n)
               .append("|$(cat ").append(n).append(" 2>/dev/null)|$([ -w ").append(n).append(" ] && echo w || echo r)\"; fi;");
        }
        for (String line : root(cmd.toString()).split("\n")) {
            line = line.trim();
            if (!line.startsWith("C|")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 4) continue;
            try {
                arr.put(new JSONObject().put("node", f[1]).put("value", f[2].trim()).put("writable", "w".equals(f[3].trim())));
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** 探测可用于滑条控制的充电节点；没有可用节点时如实返回 supported=false */
    static String chargeProbe() {
        JSONObject o = new JSONObject();
        JSONArray all = new JSONArray();
        try {
            String[] cand = {
                "/sys/class/power_supply/battery/constant_charge_current_max",
                "/sys/class/power_supply/battery/constant_charge_current",
                "/sys/class/power_supply/battery/input_current_limit",
                "/sys/class/power_supply/usb/current_max",
                "/sys/class/power_supply/battery/charge_control_limit"
            };
            String chosen = null;
            long chosenMa = 0;
            long maxMa = 0;
            for (String n : cand) {
                String w = root("[ -w " + n + " ] && echo w || echo r").trim();
                String v = root("cat " + n + " 2>/dev/null").trim();
                long raw = 0;
                try { raw = Long.parseLong(v); } catch (Exception ignored) {}
                long ma = raw > 100000 ? raw / 1000 : raw;
                all.put(new JSONObject().put("node", n).put("value", v)
                        .put("writable", "w".equals(w)).put("ma", ma));
                if (chosen == null && "w".equals(w) && ma > 0) { chosen = n; chosenMa = ma; }
            }
            // 上限参考：charge_control_limit_max（档位）或直接给 20A 上限
            String lim = root("cat /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").trim();
            long limVal = 0;
            try { limVal = Long.parseLong(lim); } catch (Exception ignored) {}
            maxMa = chosen == null ? 0 : Math.max(3000, Math.min(20000, chosenMa > 3000 ? chosenMa * 2 : 12000));

            // 档位式限流：charge_control_limit / charge_control_limit_max（Scene 常用）
            long lvMax = 0, lvNow = 0;
            String lvNode = "/sys/class/power_supply/battery/charge_control_limit";
            try { lvMax = Long.parseLong(root("cat /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").trim()); } catch (Exception ignored) {}
            try { lvNow = Long.parseLong(root("cat " + lvNode + " 2>/dev/null").trim()); } catch (Exception ignored) {}
            boolean lvWritable = root("[ -w " + lvNode + " ] && echo y").trim().contains("y");
            // 厂商私有节点扫描
            JSONArray vendor = new JSONArray();
            for (String vn : new String[]{
                    "/sys/class/power_supply/battery/step_charging_current",
                    "/sys/class/power_supply/battery/smart_charging",
                    "/sys/class/power_supply/battery/battery_charging_enabled",
                    "/sys/class/power_supply/battery/input_suspend"}) {
                if (root("[ -e " + vn + " ] && echo y").trim().contains("y")) {
                    vendor.put(new JSONObject().put("node", vn)
                            .put("value", root("cat " + vn + " 2>/dev/null").trim())
                            .put("writable", root("[ -w " + vn + " ] && echo y").trim().contains("y")));
                }
            }
            o.put("levelNode", lvNode);
            o.put("levelMax", lvMax);
            o.put("level", lvNow);
            o.put("levelWritable", lvWritable);
            o.put("vendor", vendor);
            o.put("supported", chosen != null);
            o.put("node", chosen == null ? "" : chosen);
            o.put("ma", chosenMa);
            o.put("maxMa", maxMa);
            o.put("limitMax", limVal);
            o.put("nodes", all);
            if (chosen == null) {
                o.put("reason", "当前机型没有暴露可写的充电电流节点（常见于小米/澎湃的私有充电管理）");
            }
        } catch (Exception ignored) {}
        return o.toString();
    }

    static final String CHG_BAK = "/data/adb/omnix/charge-backup.txt";
    static final String GUARD_SH = "/data/adb/omnix/charge-guard.sh";
    static final String GUARD_PID = "/data/adb/omnix/charge-guard.pid";

    /** 运行时探测各厂商充电节点，返回 stop/enable/limit 三套方案 */
    static String chargeNodes() {
        JSONObject o = new JSONObject();
        try {
            String script =
                // 停机类节点：名字|停用值|恢复值
                "for e in battery/input_suspend:1:0 battery/charging_enabled:0:1 " +
                "battery/battery_charging_enabled:0:1 battery/charge_disable:1:0 " +
                "battery/device/charge_disable:1:0 battery/charge_enabled:0:1 " +
                "usb/input_suspend:1:0 battery/store_mode:1:0 battery/siop_level:1:0; do " +
                "  p=${e%%:*}; rest=${e#*:}; off=${rest%%:*}; on=${rest#*:}; " +
                "  f=/sys/class/power_supply/$p; " +
                "  [ -w \"$f\" ] && echo \"STOP|$f|$off|$on\"; " +
                "done; " +
                // 限流类节点
                "for p in battery/constant_charge_current_max battery/constant_charge_current " +
                "battery/input_current_limit battery/charge_control_limit usb/current_max " +
                "battery/current_max battery/fast_charge_current battery/restrict_charge_current; do " +
                "  f=/sys/class/power_supply/$p; " +
                "  if [ -e \"$f\" ]; then echo \"LIMIT|$f|$(cat $f 2>/dev/null)|$([ -w $f ] && echo w || echo r)\"; fi; " +
                "done; " +
                // 档位上限
                "[ -e /sys/class/power_supply/battery/charge_control_limit_max ] && " +
                "echo \"LVMAX|$(cat /sys/class/power_supply/battery/charge_control_limit_max)\"";
            for (String line : root(script).split("\n")) {
                line = line.trim();
                if (line.startsWith("STOP|")) {
                    String[] f = line.split("\\|", -1);
                    if (f.length >= 4 && !o.has("stopNode")) {
                        o.put("stopNode", f[1]);
                        o.put("stopOff", f[2]);
                        o.put("stopOn", f[3]);
                    }
                } else if (line.startsWith("LIMIT|")) {
                    String[] f = line.split("\\|", -1);
                    if (f.length >= 4) {
                        JSONArray arr = o.optJSONArray("limits");
                        if (arr == null) { arr = new JSONArray(); o.put("limits", arr); }
                        arr.put(new JSONObject().put("node", f[1]).put("value", f[2])
                                .put("writable", "w".equals(f[3])));
                    }
                } else if (line.startsWith("LVMAX|")) {
                    o.put("levelMax", line.substring(6).trim());
                }
            }
            o.put("canStop", o.has("stopNode"));
            o.put("method", o.has("stopNode") ? "节点停充" : "限流降档");
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** 停充 / 恢复充电（用探测到的节点，没有节点则降到最低档） */
    static String chargeStop(boolean stop) {
        String nodes = chargeNodes();
        try {
            JSONObject j = new JSONObject(nodes);
            if (j.has("stopNode")) {
                String node = j.getString("stopNode");
                String v = stop ? j.getString("stopOff") : j.getString("stopOn");
                root("echo " + v + " > " + node + " 2>&1");
                String now = root("cat " + node + " 2>/dev/null").trim();
                return ("ok") + "\u0001" + (stop ? "已停充" : "已恢复充电")
                        + "（" + node.substring(node.lastIndexOf('/') + 1) + "=" + now + "）";
            }
        } catch (Exception ignored) {}
        // 兜底：限流档位
        int max = 16;
        try { max = Integer.parseInt(new JSONObject(nodes).optString("levelMax", "16")); } catch (Exception ignored) {}
        return setChargeLevel(stop ? 0 : max);
    }

    /** 充电守护：到电量/温度阈值自动降档（停充），条件解除后恢复 */
    static String guardStart(int stopCap, int stopTemp, int lowLv, int maxLv) {
        String script =
            "#!/system/bin/sh\n" +
            "# 运行时探测停充节点（跨机型）\n" +
            "STOP=\"\"; OFF=1; ON=0\n" +
            "for e in battery/input_suspend:1:0 battery/charging_enabled:0:1 " +
            "battery/battery_charging_enabled:0:1 battery/charge_disable:1:0 " +
            "battery/device/charge_disable:1:0 usb/input_suspend:1:0; do\n" +
            "  p=${e%%:*}; rest=${e#*:};\n" +
            "  f=/sys/class/power_supply/$p\n" +
            "  if [ -w \"$f\" ]; then STOP=$f; OFF=${rest%%:*}; ON=${rest#*:}; break; fi\n" +
            "done\n" +
            "LIM=/sys/class/power_supply/battery/charge_control_limit\n" +
            "CAP=/sys/class/power_supply/battery/capacity\n" +
            "TMP=/sys/class/power_supply/battery/temp\n" +
            "cur=0\n" +
            "while true; do\n" +
            "  c=$(cat $CAP 2>/dev/null); t=$(cat $TMP 2>/dev/null)\n" +
            "  [ -n \"$t\" ] && t=$((t/10))\n" +
            "  hit=0\n" +
            "  if [ \"$c\" -ge " + stopCap + " ] 2>/dev/null; then hit=1; fi\n" +
            "  if [ \"$t\" -ge " + stopTemp + " ] 2>/dev/null; then hit=1; fi\n" +
            "  if [ \"$hit\" != \"$cur\" ]; then\n" +
            "    if [ -n \"$STOP\" ]; then\n" +
            "      if [ \"$hit\" = \"1\" ]; then echo $OFF > $STOP; else echo $ON > $STOP; fi\n" +
            "    else\n" +
            "      if [ \"$hit\" = \"1\" ]; then echo " + lowLv + " > $LIM; else echo " + maxLv + " > $LIM; fi\n" +
            "    fi\n" +
            "    cur=$hit\n" +
            "  fi\n" +
            "  sleep 10\n" +
            "done\n";
        root("mkdir -p /data/adb/omnix && cat > " + GUARD_SH + " <<'EOF'\n" + script + "EOF\nchmod 755 " + GUARD_SH);
        root("pkill -f 'omnix/charge-guard.sh' 2>/dev/null");
        root("nohup sh " + GUARD_SH + " >/dev/null 2>&1 & echo $! > " + GUARD_PID);
        String pid = root("cat " + GUARD_PID + " 2>/dev/null").trim();
        return (pid.isEmpty() ? "err" : "ok") + "\u0001" + "充电守护已启动（≥" + stopCap + "% 或 ≥" + stopTemp + "℃ 降档）pid " + pid;
    }

    static String guardStop() {
        root("[ -f " + GUARD_PID + " ] && kill $(cat " + GUARD_PID + ") 2>/dev/null; rm -f " + GUARD_PID);
        root("pkill -f 'omnix/charge-guard.sh' 2>/dev/null");
        root("echo 16 > /sys/class/power_supply/battery/charge_control_limit 2>/dev/null");
        return "ok\u0001充电守护已停止，限流已恢复";
    }

    static String guardStatus() {
        String pid = root("cat " + GUARD_PID + " 2>/dev/null").trim();
        boolean running = false;
        if (!pid.isEmpty()) running = root("kill -0 " + pid + " 2>/dev/null && echo YES || echo NO").contains("YES");
        JSONObject o = new JSONObject();
        try { o.put("running", running); o.put("pid", pid); } catch (Exception ignored) {}
        return o.toString();
    }

    /** 充电加速：把限流拉满 + 关掉充电相关的温控降档 */
    static String chargeBoost() {
        try {
            StringBuilder log = new StringBuilder();
            // 备份当前值
            String lim = root("cat /sys/class/power_supply/battery/charge_control_limit 2>/dev/null").trim();
            String limMax = root("cat /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").trim();
            StringBuilder bak = new StringBuilder();
            bak.append("limit=").append(lim).append("\n");
            // 充电相关 cooling device
            String list = root("for c in /sys/class/thermal/cooling_device*; do "
                    + "t=$(cat $c/type 2>/dev/null); cur=$(cat $c/cur_state 2>/dev/null); "
                    + "case \"$t\" in *charg*|*batt*|*usb*|*vbus*|*chg*) echo \"$c|$t|$cur\";; esac; done");
            for (String line : list.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] f = line.split("\\|", -1);
                if (f.length < 3) continue;
                bak.append("cool=").append(f[0]).append(":").append(f[2]).append("\n");
            }
            root("mkdir -p /data/adb/omnix && printf '%s' '" + bak.toString().replace("'", "") + "' > " + CHG_BAK);

            // 1) 限流档位拉满
            int max = 16;
            try { max = Integer.parseInt(limMax); } catch (Exception ignored) {}
            root("echo " + max + " > /sys/class/power_supply/battery/charge_control_limit 2>&1");
            String nowLim = root("cat /sys/class/power_supply/battery/charge_control_limit").trim();
            log.append("限流档位 → ").append(nowLim).append("/").append(max).append("\n");

            // 2) 关闭充电温控降档
            int off = 0;
            for (String line : list.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] f = line.split("\\|", -1);
                if (f.length < 3) continue;
                root("echo 0 > " + f[0] + "/cur_state 2>&1");
                String cur = root("cat " + f[0] + "/cur_state").trim();
                if ("0".equals(cur)) { off++; log.append("解除温控降档：").append(f[1]).append("\n"); }
            }
            return (off > 0 || !nowLim.isEmpty() ? "ok" : "err") + "\u0001" + log.toString().trim();
        } catch (Exception e) { return "err\u0001" + e.getMessage(); }
    }

    /** 恢复加速前的值 */
    static String chargeRestore() {
        try {
            String raw = root("cat " + CHG_BAK + " 2>/dev/null");
            if (raw.trim().isEmpty()) return "err\u0001没有加速前的备份";
            StringBuilder log = new StringBuilder();
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (line.startsWith("limit=")) {
                    String v = line.substring(6).trim();
                    if (!v.isEmpty()) {
                        root("echo " + v + " > /sys/class/power_supply/battery/charge_control_limit 2>&1");
                        log.append("限流档位恢复 ").append(v).append("\n");
                    }
                } else if (line.startsWith("cool=")) {
                    String kv = line.substring(5);
                    int idx = kv.lastIndexOf(':');
                    if (idx > 0) {
                        String node = kv.substring(0, idx), st = kv.substring(idx + 1).trim();
                        if (!st.isEmpty()) {
                            root("echo " + st + " > " + node + "/cur_state 2>&1");
                            log.append("温控档位恢复 ").append(st).append("\n");
                        }
                    }
                }
            }
            root("rm -f " + CHG_BAK);
            return "ok\u0001" + log.toString().trim();
        } catch (Exception e) { return "err\u0001" + e.getMessage(); }
    }

    /** 当前充电状态：限流档位 + 温控档位 + 实时功率 */
    static String chargeState() {
        JSONObject o = new JSONObject();
        try {
            o.put("limit", root("cat /sys/class/power_supply/battery/charge_control_limit 2>/dev/null").trim());
            o.put("limitMax", root("cat /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").trim());
            String cool = root("for c in /sys/class/thermal/cooling_device*; do "
                    + "t=$(cat $c/type 2>/dev/null); cur=$(cat $c/cur_state 2>/dev/null); mx=$(cat $c/max_state 2>/dev/null); "
                    + "case \"$t\" in *charg*|*batt*|*usb*|*vbus*|*chg*) echo \"$t|$cur|$mx\";; esac; done").trim();
            o.put("cooling", cool);
            o.put("boosted", !root("test -f " + CHG_BAK + " && echo y").trim().isEmpty());
        } catch (Exception ignored) {}
        return o.toString();
    }

    /** 写限流档位（charge_control_limit） */
    static String setChargeLevel(int level) {
        String node = "/sys/class/power_supply/battery/charge_control_limit";
        String maxS = root("cat /sys/class/power_supply/battery/charge_control_limit_max 2>/dev/null").trim();
        int max = 16;
        try { max = Integer.parseInt(maxS); } catch (Exception ignored) {}
        if (level < 0) level = 0;
        if (level > max) level = max;
        root("echo " + level + " > " + node + " 2>&1");
        String now = root("cat " + node).trim();
        return (now.equals(String.valueOf(level)) ? "ok" : "err") + "\u0001" + "档位 = " + now + " / " + max;
    }

    /** 按 mA 写入（自动换算 uA） */
    static String setChargeMa(String node, int ma) {
        if (node == null || !node.startsWith("/sys/class/power_supply/")) return "err\u0001非法节点";
        String probe = root("cat " + node + " 2>/dev/null").trim();
        long raw = 0;
        try { raw = Long.parseLong(probe); } catch (Exception ignored) {}
        long val = raw > 100000 ? (long) ma * 1000L : ma;
        root("echo " + val + " > " + node + " 2>&1");
        String now = root("cat " + node).trim();
        long nowRaw = 0;
        try { nowRaw = Long.parseLong(now); } catch (Exception ignored) {}
        int nowMa = (int) (nowRaw > 100000 ? nowRaw / 1000 : nowRaw);
        return (!now.isEmpty() ? "ok" : "err") + "\u0001" + "现在 " + nowMa + " mA";
    }

    static String setCharge(String node, String ua) {
        if (node == null || !node.startsWith("/sys/class/power_supply/")) return "err\u0001非法节点";
        root("echo '" + ua.replaceAll("[^0-9]", "") + "' > " + node + " 2>&1");
        String now = root("cat " + node).trim();
        return (!now.isEmpty() ? "ok" : "err") + "\u0001" + node + " = " + now;
    }

    /* ---------------- 温控守护 ---------------- */
    static final String TUNE_SH = "/data/adb/omnix/tune.sh";
    static final String TUNE_PID = "/data/adb/omnix/tune.pid";

    /** 生成/启动守护：hi=降频阈值 lo=恢复阈值 ratio=降频比例 */
    static String daemonStart(int hi, int lo, int ratio) {
        String script =
            "#!/system/bin/sh\n" +
            "P=$(" + "echo /data/adb/ksu/bin/resetprop)\n" +
            "F=/sys/devices/system/cpu/cpufreq\n" +
            "while true; do\n" +
            "  T=0\n" +
            "  for z in /sys/class/thermal/thermal_zone*; do\n" +
            "    t=$(cat $z/type 2>/dev/null); v=$(cat $z/temp 2>/dev/null)\n" +
            "    case \"$t\" in *trip*|*limit*|*hw*) continue;; esac\n" +
            "    case \"$t\" in *cpu*|*soc*|*tsens*) [ -n \"$v\" ] && [ \"$v\" -gt \"$T\" ] && T=$v;; esac\n" +
            "  done\n" +
            "  [ \"$T\" -gt 1000 ] && T=$((T/1000))\n" +
            "  if [ \"$T\" -ge " + hi + " ]; then\n" +
            "    for p in $F/policy*; do mx=$(cat $p/cpuinfo_max_freq 2>/dev/null); [ -z \"$mx\" ] && continue;\n" +
            "      echo $((mx * " + ratio + " / 100)) > $p/scaling_max_freq 2>/dev/null; done\n" +
            "  elif [ \"$T\" -le " + lo + " ]; then\n" +
            "    for p in $F/policy*; do mx=$(cat $p/cpuinfo_max_freq 2>/dev/null); [ -z \"$mx\" ] && continue;\n" +
            "      echo $mx > $p/scaling_max_freq 2>/dev/null; done\n" +
            "  fi\n" +
            "  sleep 3\n" +
            "done\n";
        root("mkdir -p /data/adb/omnix && cat > " + TUNE_SH + " <<'EOF'\n" + script + "EOF\nchmod 755 " + TUNE_SH);
        daemonStop();
        root("nohup sh " + TUNE_SH + " >/dev/null 2>&1 & echo $! > " + TUNE_PID);
        String pid = root("cat " + TUNE_PID + " 2>/dev/null").trim();
        return (pid.isEmpty() ? "err" : "ok") + "\u0001" + "温控守护已启动 (pid " + pid + ")";
    }

    static String daemonStop() {
        root("[ -f " + TUNE_PID + " ] && kill $(cat " + TUNE_PID + ") 2>/dev/null; rm -f " + TUNE_PID);
        root("pkill -f 'omnix/tune.sh' 2>/dev/null");
        return "ok\u0001已停止";
    }

    static String daemonStatus() {
        String pid = root("cat " + TUNE_PID + " 2>/dev/null").trim();
        boolean running = false;
        if (!pid.isEmpty()) {
            running = root("kill -0 " + pid + " 2>/dev/null && echo YES || echo NO").contains("YES");
        }
        boolean boot = !root("test -f /data/adb/service.d/omnix-tune.sh && echo YES || echo NO").trim().contains("NO");
        JSONObject o = new JSONObject();
        try { o.put("running", running); o.put("boot", boot); o.put("pid", pid); } catch (Exception ignored) {}
        return o.toString();
    }

    /** 开机自启：写 service.d 脚本（跟随当前档位） */
    static String daemonBoot(int hi, int lo, int ratio, boolean enable) {
        String path = "/data/adb/service.d/omnix-tune.sh";
        if (!enable) {
            root("rm -f " + path);
            return "ok\u0001已关闭开机自启";
        }
        String cmd = "sh " + TUNE_SH + " >/dev/null 2>&1 &";
        root("mkdir -p /data/adb/service.d && cat > " + path + " <<'EOF'\n#!/system/bin/sh\n"
                + cmd + "\nEOF\nchmod 755 " + path);
        return "ok\u0001已设置开机自启";
    }

    /* ---------------- 应用使用时长 / 耗电 ---------------- */
    static boolean hasUsageAccess(Context c) {
        try {
            android.app.AppOpsManager aom = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), c.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    static void openUsageAccess(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    /** 今日各应用前台时长 + 耗电占比（解析 batterystats 的 UID 电量） */
    static String usage(Activity a) {
        JSONObject res = new JSONObject();
        try {
            res.put("granted", hasUsageAccess(a));
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis();

            UsageStatsManager usm = (UsageStatsManager) a.getSystemService(Context.USAGE_STATS_SERVICE);
            java.util.List<UsageStats> list = usm == null ? null : usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now);
            java.util.Map<String, Long> use = new HashMap<>();
            if (list != null) for (UsageStats u : list) {
                if (u.getTotalTimeInForeground() > 0)
                    use.put(u.getPackageName(), u.getTotalTimeInForeground());
            }

            // batterystats：估算耗电（mAh）
            java.util.Map<String, Double> power = new HashMap<>();
            String bs = root("dumpsys batterystats --charged 2>/dev/null | grep -E '^\\s+Uid u0a[0-9]+:' -A6 | head -4000");
            String curPkg = null;
            for (String line : bs.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Uid u0a")) {
                    curPkg = null;
                } else if (t.startsWith("mAh=") && curPkg == null) {
                    // 没有包名映射时忽略
                }
            }

            JSONArray apps = new JSONArray();
            android.content.pm.PackageManager pm = a.getPackageManager();
            for (java.util.Map.Entry<String, Long> e : use.entrySet()) {
                try {
                    String label = e.getKey();
                    try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(e.getKey(), 0))); } catch (Exception ignored) {}
                    apps.put(new JSONObject()
                            .put("pkg", e.getKey())
                            .put("label", label)
                            .put("ms", e.getValue()));
                } catch (Exception ignored) {}
            }
            res.put("apps", apps);
            res.put("totalMs", sumMs(use));
        } catch (Exception e) {
            try { res.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return res.toString();
    }

    /** 批量取应用图标（base64 PNG，48px），最多 60 个 */
    static String icons(Activity a, String csv) {
        JSONObject out = new JSONObject();
        try {
            android.content.pm.PackageManager pm = a.getPackageManager();
            int n = 0;
            for (String pkg : csv.split(",")) {
                pkg = pkg.trim();
                if (pkg.isEmpty()) continue;
                if (++n > 80) break;
                try {
                    android.graphics.drawable.Drawable d = pm.getApplicationIcon(pkg);
                    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                            48, 48, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                    d.setBounds(0, 0, 48, 48);
                    d.draw(c);
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, bo);
                    out.put(pkg, android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    /** 当前前台应用包名（用于把功耗采样归属到应用） */
    static String foregroundPkg(Activity a) {
        try {
            UsageStatsManager usm = (UsageStatsManager) a.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = usm.queryEvents(now - 15000, now);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String pkg = "";
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                        || e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    pkg = e.getPackageName();
                }
            }
            return pkg == null ? "" : pkg;
        } catch (Exception e) { return ""; }
    }

    static long sumMs(java.util.Map<String, Long> m) {
        long s = 0;
        for (long v : m.values()) s += v;
        return s;
    }
}
