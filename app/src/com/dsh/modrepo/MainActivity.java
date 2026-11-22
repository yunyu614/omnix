package com.dsh.modrepo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    static final int REQ_IMPORT = 1001;
    static final int REQ_FLASH = 1002;
    String pendingDev = null;
    WebView web;
    Handler ui = new Handler();
    volatile int depth = 0;   // JS 上报的页面层级：0=顶层，>0=有上一级

    @Override public void onBackPressed() {
        if (depth > 0) {
            web.post(new Runnable() { public void run() {
                web.evaluateJavascript("window.__back && window.__back()", null);
            }});
            return;
        }
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestPermissions(new String[]{
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE"}, 1);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);
        WebView.setWebContentsDebuggingEnabled(true);
        web.setBackgroundColor(0xFF081720);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                android.util.Log.i("ModRepoJS", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FLASH && res == RESULT_OK && data != null && pendingDev != null) {
            final String dev = pendingDev;
            pendingDev = null;
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                final File cache = new File(getCacheDir(), "flash.img");
                FileOutputStream out = new FileOutputStream(cache);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
                new Thread(new Runnable() { public void run() {
                    String r = SysInfo.flashPart(dev, cache.getAbsolutePath());
                    try {
                        org.json.JSONObject o = new org.json.JSONObject();
                        o.put("kind", "flash");
                        o.put("ok", r.startsWith("ok"));
                        o.put("path", dev);
                        o.put("msg", r.substring(r.indexOf('\u0001') + 1));
                        push("opdone", o.toString());
                    } catch (Exception ignored) {}
                }}).start();
            } catch (Exception e) { toast("读取镜像失败：" + e.getMessage()); }
            return;
        }
        if (req == REQ_IMPORT && res == RESULT_OK && data != null) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                final String txt = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                ui.post(new Runnable() { public void run() {
                    web.loadUrl("javascript:window.onNativeEvent&&window.onNativeEvent('backup','" + Js.e(txt) + "')");
                }});
            } catch (Exception e) {
                toast("读取失败：" + e.getMessage());
            }
        }
    }

    void push(String ev, String json) {
        final String js = "javascript:window.onNativeEvent&&window.onNativeEvent('" + ev + "','" + Js.e(json) + "')";
        ui.post(new Runnable() { public void run() { web.loadUrl(js); } });
    }

    void toast(final String m) {
        ui.post(new Runnable() { public void run() {
            web.loadUrl("javascript:window.onNativeEvent&&window.onNativeEvent('toast','" + Js.e(m) + "')");
        }});
    }

    class Bridge {
        // 设备 + root 环境（异步回调 env 事件）
        @JavascriptInterface public void env() {
            new Thread(new Runnable() { public void run() {
                final String json = Env.snapshot(MainActivity.this);
                ui.post(new Runnable() { public void run() {
                    web.loadUrl("javascript:window.onNativeEvent&&window.onNativeEvent('env','" + Js.e(json) + "')");
                }});
            }}).start();
        }

        // 设置读写
        @JavascriptInterface public void cfgSet(String k, String v) { Cfg.set(getApplicationContext(), k, v); }
        @JavascriptInterface public String cfgGet(String k, String d) { return Cfg.get(getApplicationContext(), k, d); }

        // 备份导出：写入 Download/ModRepo/，返回完整路径
        @JavascriptInterface public String exportBackup(String json) {
            try {
                String dir = "/storage/emulated/0/Download/ModRepo";
                new File(dir).mkdirs();
                String name = "ModRepo-backup-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(new java.util.Date()) + ".json";
                File f = new File(dir, name);
                FileOutputStream out = new FileOutputStream(f);
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.close();
                return "ok\u0001" + f.getAbsolutePath();
            } catch (Exception e) { return "fail\u0001" + e.getMessage(); }
        }

        // 备份导入：系统文件选择器
        @JavascriptInterface public void importBackup() {
            ui.post(new Runnable() { public void run() {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { toast("无法打开文件选择器"); }
            }});
        }

        @JavascriptInterface public void openUrl(String url) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
        }

        /** 状态栏 / 导航栏 / WebView 底色跟随主题，避免顶部出现黑条 */
        @JavascriptInterface public void bars(final String bgHex, final boolean darkIcons) {
            ui.post(new Runnable() { public void run() {
                try {
                    int c = android.graphics.Color.parseColor(bgHex);
                    getWindow().setStatusBarColor(c);
                    getWindow().setNavigationBarColor(c);
                    int f = getWindow().getDecorView().getSystemUiVisibility();
                    if (darkIcons)
                        f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    else
                        f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    getWindow().getDecorView().setSystemUiVisibility(f);
                    web.setBackgroundColor(c);
                    getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(c));
                } catch (Exception ignored) {}
            }});
        }

        /** 跟随主题切换桌面图标（activity-alias） */
        @JavascriptInterface public void setIcon(final String theme) {
            ui.post(new Runnable() { public void run() {
                if (theme == null || theme.length() == 0) return;
                if (theme.equals(Cfg.get(getApplicationContext(), "iconTheme", ""))) return;
                android.content.pm.PackageManager pm = getPackageManager();
                String[] all = { "mist", "celadon", "violet", "sakura", "amber", "mocha", "graphite", "ocean" };
                for (String t : all) {
                    android.content.ComponentName cn =
                            new android.content.ComponentName(MainActivity.this,
                                    getPackageName() + ".Icon" + t.substring(0, 1).toUpperCase() + t.substring(1));
                    int state = t.equals(theme)
                            ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    try { pm.setComponentEnabledSetting(cn, state,
                            android.content.pm.PackageManager.DONT_KILL_APP); } catch (Exception ignored) {}
                }
                Cfg.set(getApplicationContext(), "iconTheme", theme);
                android.util.Log.i("ModRepoJS", "ICON -> " + theme);
            }});
        }

        /* ========== 真实数据 ========== */
        private final java.util.Map<String, String> bodies = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> codes = new java.util.HashMap<>();

        /** 异步 GET：完成后推 http 事件，正文用 body(id) 同步取（避免超长 JS 串） */
        @JavascriptInterface public void fetch(final String id, final String url) {
            new Thread(new Runnable() { public void run() {
                String r = Net.get(url);
                int sep = r.indexOf('\u0001');
                String code = sep > 0 ? r.substring(0, sep) : "0";
                String body = sep > 0 ? r.substring(sep + 1) : r;
                bodies.put(id, body);
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", id);
                    o.put("code", Integer.parseInt(code));
                    o.put("len", body.length());
                    push("http", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }

        /** 异步 POST（翻译等），完成后同样推 http 事件 */
        @JavascriptInterface public void post(final String id, final String url, final String payload, final String ctype) {
            new Thread(new Runnable() { public void run() {
                String r = Net.post(url, payload, ctype);
                int sep = r.indexOf('\u0001');
                String code = sep > 0 ? r.substring(0, sep) : "0";
                String body = sep > 0 ? r.substring(sep + 1) : r;
                bodies.put(id, body);
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", id); o.put("code", Integer.parseInt(code)); o.put("len", body.length());
                    push("http", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }

        @JavascriptInterface public String body(String id) {
            String b = bodies.remove(id);
            return b == null ? "" : b;
        }

        /** 异步下载到 Download/ModRepo/name，进度推 dl 事件 */
        @JavascriptInterface public void fetchFile(final String id, final String url, final String name) {
            new Thread(new Runnable() { public void run() {
                final File dest = new File("/storage/emulated/0/Download/ModRepo", name);
                String r = Net.download(url, dest, new Net.Progress() {
                    public void on(long done, long total) {
                        try {
                            org.json.JSONObject o = new org.json.JSONObject();
                            o.put("id", id); o.put("done", done); o.put("total", total);
                            push("dlp", o.toString());
                        } catch (Exception ignored) {}
                    }
                });
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", id);
                    o.put("ok", r.startsWith("ok"));
                    String[] p = r.split("\u0001");
                    if (r.startsWith("ok")) { o.put("path", p[1]); o.put("size", Long.parseLong(p[2])); }
                    else o.put("err", p.length > 1 ? p[1] : "下载失败");
                    push("dl", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }

        /** 已安装管理器版本：{pkg: versionName} */
        @JavascriptInterface public String installed() {
            org.json.JSONObject o = new org.json.JSONObject();
            String[] pkgs = { "topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu",
                    "com.rifsxd.ksunext", "com.sukisu.ultra", "com.blink.apatch", "org.lsposed.manager", "moe.shizuku.privileged.api", "com.termux", "bin.mt.plus", "com.byyoung.setting" };
            String[] full = { "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu",
                    "com.rifsxd.ksunext", "com.sukisu.ultra", "com.blink.apatch", "org.lsposed.manager", "moe.shizuku.privileged.api", "com.termux", "bin.mt.plus", "com.byyoung.setting" };
            for (String p : full) {
                try {
                    o.put(p, getPackageManager().getPackageInfo(p, 0).versionName);
                } catch (Exception ignored) {}
            }
            return o.toString();
        }

        /** 本地已下载文件（Download/ModRepo） */
        @JavascriptInterface public String localFiles() {
            org.json.JSONArray arr = new org.json.JSONArray();
            File dir = new File("/storage/emulated/0/Download/ModRepo");
            File[] fs = dir.listFiles();
            if (fs != null) for (File f : fs) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase();
                if (!n.endsWith(".apk") && !n.endsWith(".zip")) continue;
                try {
                    arr.put(new org.json.JSONObject().put("name", f.getName())
                            .put("size", f.length()).put("mtime", f.lastModified()));
                } catch (Exception ignored) {}
            }
            return arr.toString();
        }

        /* ===== 系统信息 / 多功能 ===== */
        @JavascriptInterface public void detail() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "detail");
                    o.put("data", new org.json.JSONObject(SysInfo.detail(MainActivity.this)));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void apps() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "apps");
                    o.put("data", new org.json.JSONArray(SysInfo.apps(MainActivity.this)));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void battery() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "battery");
                    o.put("data", new org.json.JSONObject(SysInfo.battery(MainActivity.this)));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void parts() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "parts");
                    o.put("data", new org.json.JSONArray(SysInfo.partitions()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void freezePkg(final String pkg, final boolean disable) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.freeze(pkg, disable);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("pkg", pkg);
                    o.put("disable", disable);
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("frozen", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }

        /* ===== 安装 / 刷入 ===== */
        @JavascriptInterface public void installApk(final String path) {
            new Thread(new Runnable() { public void run() {
                String out = SysInfo.rootExec("pm install -r '" + path + "' 2>&1");
                pushInstall(path, "apk", out);
            }}).start();
        }
        @JavascriptInterface public void installZip(final String path) {
            new Thread(new Runnable() { public void run() {
                String out = SysInfo.rootExec(
                    "B=$(which ksud); M=$(which magisk); P=$(which apd); " +
                    "if [ -n \"$B\" ]; then $B module install '" + path + "' 2>&1; " +
                    "elif [ -n \"$M\" ]; then $M --install-module '" + path + "' 2>&1; " +
                    "elif [ -n \"$P\" ]; then $P module install '" + path + "' 2>&1; " +
                    "else echo '未找到 ksud / magisk / apd'; fi");
                pushInstall(path, "zip", out);
            }}).start();
        }
        private void pushInstall(String path, String kind, String out) {
            try {
                String low = out.toLowerCase();
                boolean ok = kind.equals("apk")
                        ? (low.contains("success") || low.contains("成功"))
                        : !(low.contains("failed") || low.contains("error") || low.contains("未找到"));
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("path", path);
                o.put("kind", kind);
                o.put("ok", ok);
                o.put("msg", out.trim());
                push("installed", o.toString());
            } catch (Exception ignored) {}
        }

        /* ===== 分区与字库 ===== */
        @JavascriptInterface public void exportPart(final String name, final String dev) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.exportPart(name, dev);
                pushResult("export", r);
            }}).start();
        }
        /** 选好镜像后刷入：先取文件，再 dd */
        @JavascriptInterface public void flashPart(final String name, final String dev) {
            ui.post(new Runnable() { public void run() {
                pendingDev = dev;
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(i, REQ_FLASH); } catch (Exception e) { toast("无法打开文件选择器"); }
            }});
        }
        @JavascriptInterface public void slot() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "slot");
                    o.put("data", new org.json.JSONObject(SysInfo.slotInfo()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void fontBackup(final String mode) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.fontBackup(mode == null ? "key" : mode);
                pushResult("font", r);
            }}).start();
        }
        @JavascriptInterface public void fontEstimate(final String mode) {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "fontest");
                    o.put("mode", mode);
                    o.put("data", new org.json.JSONObject(SysInfo.fontEstimate(mode == null ? "key" : mode)));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void modules() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "modules");
                    org.json.JSONArray all = new org.json.JSONArray(SysInfo.modules());
                    org.json.JSONArray xp = new org.json.JSONArray(SysInfo.xposedModules(MainActivity.this));
                    for (int i = 0; i < xp.length(); i++) all.put(xp.getJSONObject(i));
                    o.put("data", all);
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void setModuleEnabled(final String id, final boolean enabled) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.setModuleEnabled(id, enabled);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "toggle");
                    o.put("ok", r.startsWith("ok"));
                    o.put("id", id);
                    o.put("enabled", enabled);
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void moduleAction(final String id) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.moduleAction(id);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "action");
                    o.put("ok", r.startsWith("ok"));
                    o.put("id", id);
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void uninstallModule(final String id, final String kind) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.uninstallModule(id, kind);
                pushResult("uninstall", r);
            }}).start();
        }
        private void pushResult(String kind, String raw) {
            int i = raw.indexOf('\u0001');
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("kind", kind);
                o.put("ok", raw.startsWith("ok"));
                String rest = i > 0 ? raw.substring(i + 1) : raw;
                String[] parts = rest.split("\u0001");
                o.put("path", parts.length > 0 ? parts[0] : "");
                o.put("msg", parts.length > 1 ? parts[1] : rest);
                push("opdone", o.toString());
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public void copyText(final String t) {
            ui.post(new Runnable() { public void run() {
                try {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ModRepo", t));
                } catch (Exception ignored) {}
            }});
        }
        @JavascriptInterface public boolean deletePath(String path) {
            try {
                java.io.File f = new java.io.File(path);
                if (!f.exists()) return true;
                if (f.isDirectory()) {
                    java.io.File[] kids = f.listFiles();
                    if (kids != null) for (java.io.File k : kids) deletePath(k.getAbsolutePath());
                }
                return f.delete();
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean exists(String path) {
            try { return new java.io.File(path).exists(); } catch (Exception e) { return false; }
        }
        @JavascriptInterface public long fileSize(String path) {
            try { return new java.io.File(path).length(); } catch (Exception e) { return 0; }
        }
        @JavascriptInterface public void openManager() {
            ui.post(new Runnable() { public void run() {
                String[] pkgs = { "me.weishu.kernelsu", "com.rifsxd.ksunext", "com.sukisu.ultra",
                        "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "com.blink.apatch",
                        "org.lsposed.manager" };
                for (String p : pkgs) {
                    try {
                        android.content.Intent i = getPackageManager().getLaunchIntentForPackage(p);
                        if (i != null) { i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
                    } catch (Exception ignored) {}
                }
                toast("未找到已安装的管理器");
            }});
        }
        @JavascriptInterface public void exportApps(final String pkgsCsv) {
            new Thread(new Runnable() { public void run() {
                String r = SysInfo.exportApps(MainActivity.this, pkgsCsv == null ? "" : pkgsCsv);
                pushResult("appbackup", r);
            }}).start();
        }
        /** 任意包名查版本，空串表示未安装 */
        @JavascriptInterface public String pkgVer(String pkg) {
            try {
                return getPackageManager().getPackageInfo(pkg, 0).versionName;
            } catch (Exception e) { return ""; }
        }
        /* ===== 温控 / 调度 / 充电 / 耗电 ===== */
        /* ===== 机型伪装 ===== */
        @JavascriptInterface public void spoofProps() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "spoofprops");
                    o.put("masked", Spoof.masked());
                    o.put("data", new org.json.JSONObject(Spoof.props()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void spoofApply(final String json) {
            new Thread(new Runnable() { public void run() {
                String r = Spoof.apply(json);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "spoof");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void spoofRestore() {
            new Thread(new Runnable() { public void run() {
                String r = Spoof.restore();
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "spoof");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }

        /* ===== 内置 LSPosed ===== */
        @JavascriptInterface public void lspBundle() {
            new Thread(new Runnable() { public void run() {
                try {
                    java.io.File out = new java.io.File(getFilesDir(), "lsposed-bundle.zip");
                    java.io.InputStream in = getAssets().open("bundle/lsposed.zip");
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                    fo.close(); in.close();
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "lspbundle");
                    o.put("path", out.getAbsolutePath());
                    o.put("size", out.length());
                    // 读 zip 内 module.prop 的版本
                    java.util.zip.ZipFile zf = new java.util.zip.ZipFile(out);
                    java.util.zip.ZipEntry en = zf.getEntry("module.prop");
                    String ver = "", name = "LSPosed", vc = "";
                    if (en != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(zf.getInputStream(en)));
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("version=")) ver = line.substring(8);
                            else if (line.startsWith("versionCode=")) vc = line.substring(12);
                            else if (line.startsWith("name=")) name = line.substring(5);
                        }
                        br.close();
                    }
                    zf.close();
                    o.put("ver", ver);
                    o.put("verCode", vc);
                    o.put("name", name);
                    push("sys", o.toString());
                } catch (Exception e) {
                    try {
                        org.json.JSONObject o = new org.json.JSONObject();
                        o.put("id", "lspbundle");
                        o.put("error", String.valueOf(e.getMessage()));
                        push("sys", o.toString());
                    } catch (Exception ignored) {}
                }
            }}).start();
        }

        @JavascriptInterface public String systemDark() {
            try {
                int ui = android.content.res.Resources.getSystem().getConfiguration().uiMode;
                return ((ui & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? "1" : "0";
            } catch (Exception e) { return "0"; }
        }
        @JavascriptInterface public String sample() {
            try { return SysTune.sample(MainActivity.this); } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void chargeProbe() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "chargeprobe");
                    o.put("data", new org.json.JSONObject(SysTune.chargeProbe()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void chargeNodes() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "chargenodes");
                    o.put("data", new org.json.JSONObject(SysTune.chargeNodes()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void chargeStop(final boolean stop) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.chargeStop(stop);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "chgstop");
                    o.put("ok", true);
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void guardStart(final int cap, final int temp) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.guardStart(cap, temp, 0, 16);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "guard");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void guardStop() {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.guardStop();
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "guard");
                    o.put("ok", true);
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void guardStatus() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "guardstatus");
                    o.put("data", new org.json.JSONObject(SysTune.guardStatus()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void chargeBoost() {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.chargeBoost();
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "boost");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void chargeRestoreBoost() {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.chargeRestore();
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "boost");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void chargeState() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "chargestate");
                    o.put("data", new org.json.JSONObject(SysTune.chargeState()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void setChargeLevel(final int level) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.setChargeLevel(level);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "charge");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void setChargeMa(final String node, final int ma) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.setChargeMa(node, ma);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "charge");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneDaemon(final int hi, final int lo, final int ratio, final boolean start) {
            new Thread(new Runnable() { public void run() {
                String r = start ? SysTune.daemonStart(hi, lo, ratio) : SysTune.daemonStop();
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "daemon");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneBoot(final int hi, final int lo, final int ratio, final boolean enable) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.daemonBoot(hi, lo, ratio, enable);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "boot");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneStatus() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "tunestatus");
                    o.put("data", new org.json.JSONObject(SysTune.daemonStatus()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneCpu() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "cpu");
                    o.put("data", new org.json.JSONArray(SysTune.cpu()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneThermal() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "thermal");
                    o.put("data", new org.json.JSONArray(SysTune.thermal()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void setCpu(final String path, final String field, final String value) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.setCpu(path, field, value);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "cpu");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void tuneCharge() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "charge");
                    o.put("data", new org.json.JSONArray(SysTune.charge()));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void setCharge(final String node, final String ua) {
            new Thread(new Runnable() { public void run() {
                String r = SysTune.setCharge(node, ua);
                int i = r.indexOf('\u0001');
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("kind", "charge");
                    o.put("ok", r.startsWith("ok"));
                    o.put("msg", i > 0 ? r.substring(i + 1) : r);
                    push("opdone", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public void usageStats() {
            new Thread(new Runnable() { public void run() {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", "usage");
                    o.put("data", new org.json.JSONObject(SysTune.usage(MainActivity.this)));
                    push("sys", o.toString());
                } catch (Exception ignored) {}
            }}).start();
        }
        @JavascriptInterface public String pkgIcons(String csv) {
            try { return SysTune.icons(MainActivity.this, csv == null ? "" : csv); }
            catch (Exception e) { return "{}"; }
        }
        /* ===== 权限管理 ===== */
        @JavascriptInterface public String permStatus() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                o.put("usage", SysTune.hasUsageAccess(MainActivity.this));
                o.put("storage", MainActivity.this.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                        || android.os.Build.VERSION.SDK_INT >= 30);
                android.os.PowerManager pm = (android.os.PowerManager) MainActivity.this.getSystemService(POWER_SERVICE);
                o.put("battery", pm != null && pm.isIgnoringBatteryOptimizations(MainActivity.this.getPackageName()));
                o.put("root", Env.hasRoot());
                o.put("su", Env.suExists());
            } catch (Exception e) {
                try { o.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
            }
            return o.toString();
        }

        @JavascriptInterface public void openPerm(final String kind) {
            ui.post(new Runnable() { public void run() {
                try {
                    android.content.Intent i;
                    if ("storage".equals(kind)) {
                        MainActivity.this.requestPermissions(new String[]{
                                "android.permission.WRITE_EXTERNAL_STORAGE",
                                "android.permission.READ_EXTERNAL_STORAGE"}, 1);
                        return;
                    } else if ("root".equals(kind)) {
                        new Thread(new Runnable() { public void run() {
                            String r = SysTune.root("id -u");
                            toast("root".equals(r.trim()) || "0".equals(r.trim())
                                    ? "已获得 root 授权" : "请在管理器里允许 Omnix 的 root 请求");
                        }}).start();
                        return;
                    } else {
                        i = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(android.net.Uri.parse("package:" + MainActivity.this.getPackageName()));
                    }
                    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainActivity.this.startActivity(i);
                } catch (Exception e) {
                    try {
                        android.content.Intent fallback = new android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        fallback.setData(android.net.Uri.parse("package:" + MainActivity.this.getPackageName()));
                        fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainActivity.this.startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            }});
        }

        @JavascriptInterface public void setDepth(int d) { depth = d; }
        @JavascriptInterface public void finishApp() { finish(); }
        @JavascriptInterface public void toast(String m) { MainActivity.this.toast(m); }
        @JavascriptInterface public void log(String m) { android.util.Log.i("ModRepoJS", String.valueOf(m)); }
        @JavascriptInterface public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) { return "1.9.0"; }
        }
    }

    static class Js {
        public static String e(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '\\': sb.append("\\\\"); break;
                    case '\'': sb.append("\\'"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append(""); break;
                    case '"': sb.append("\\\""); break;
                    default: sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
