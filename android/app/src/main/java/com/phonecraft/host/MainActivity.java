package com.phonecraft.host;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The Android APK is the controller. Actual server bytes run inside Termux on this phone. */
public class MainActivity extends Activity {
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String PANEL = "http://127.0.0.1:8765/";
    private static final String HOME = "file:///android_asset/launcher.html";
    private static final String REPO = "https://github.com/Raco8493/Fast-Client";
    private static final String BRANCH = "phonecraft-android-host";
    private static final String SETUP_COMMAND = "mkdir -p ~/.termux && touch ~/.termux/termux.properties && sed -i '/^allow-external-apps=/d' ~/.termux/termux.properties && printf 'allow-external-apps=true\\n' >> ~/.termux/termux.properties && termux-reload-settings";

    private WebView webView;
    private boolean panelShowing = false;
    private boolean active = true;
    private ValueCallback<Uri[]> fileChooser;
    private static final int PICK_FILE = 301;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (active) {
                if (!panelShowing) checkStatus();
                handler.postDelayed(this, 4500);
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF190D18);
        getWindow().setNavigationBarColor(0xFF160A13);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF09090D);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (isTrusted(u)) return false;
                if ("https".equals(u.getScheme())) openUrl(u.toString());
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileChooser != null) fileChooser.onReceiveValue(null);
                fileChooser = cb;
                try {
                    Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    choose.addCategory(Intent.CATEGORY_OPENABLE);
                    choose.setType("*/*");
                    startActivityForResult(choose, PICK_FILE);
                    return true;
                } catch (Exception e) {
                    fileChooser = null;
                    return false;
                }
            }
        });
        setContentView(webView);
        goHome();
        handler.postDelayed(poll, 900);
    }

    private boolean isTrusted(Uri u) {
        return ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort() == 8765)
            || ("file".equals(u.getScheme()) && "/android_asset/launcher.html".equals(u.getPath()));
    }

    private void goHome() {
        panelShowing = false;
        webView.addJavascriptInterface(new Bridge(), "PhoneCraft");
        webView.loadUrl(HOME);
    }

    private void openPanel() { openPanelPath(""); }

    private void openPanelPath(String suffix) {
        panelShowing = true;
        webView.removeJavascriptInterface("PhoneCraft");
        webView.loadUrl(PANEL + suffix);
    }

    private void checkStatus() {
        io.execute(() -> {
            JSONObject result = new JSONObject();
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection)new URL(PANEL + "api/status").openConnection();
                con.setConnectTimeout(1400);
                con.setReadTimeout(1600);
                if (con.getResponseCode() != 200) throw new Exception("Panel unavailable");
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try (InputStream in = con.getInputStream()) {
                    byte[] bytes = new byte[4096];
                    int n;
                    while ((n = in.read(bytes)) != -1) {
                        stream.write(bytes, 0, n);
                        if (stream.size() > 1024 * 1024) throw new Exception("Response too big");
                    }
                }
                result = new JSONObject(stream.toString("UTF-8"));
                result.put("available", true);
            } catch (Exception ignored) {
                try { result.put("available", false); } catch (Exception e) { /* ignore */ }
            } finally { if (con != null) con.disconnect(); }
            final String data = result.toString();
            handler.post(() -> {
                if (active && !panelShowing) webView.evaluateJavascript("window.setPhoneCraftStatus(" + data + ")", null);
            });
        });
    }

    private void openUrl(String uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))); }
        catch (Exception e) { message("مافيش متصفح قادر يفتح الرابط"); }
    }

    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private boolean hasTermux() {
        try { getPackageManager().getPackageInfo("com.termux", 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void requirePermission() {
        if (!hasTermux()) { message("نزّل Termux الأول من F-Droid"); openUrl("https://f-droid.org/packages/com.termux/"); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERMISSION}, 101);
            return;
        }
        message("التصريح موجود. اتأكد كمان إن allow-external-apps=true في Termux.");
    }

    private void runInTermux(String command, boolean foreground, String label) {
        if (!hasTermux()) { message("لازم تثبّت Termux الأول"); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requirePermission();
            message("بعد السماح بالتصريح، اضغط الزر مرة تانية");
            return;
        }
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", !foreground);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            intent.putExtra("com.termux.RUN_COMMAND_LABEL", label);
            startService(intent);
            message(label + " — تابع Termux للنتيجة");
        } catch (SecurityException e) {
            message("افتح إعدادات التطبيق وامنح Run commands in Termux environment");
        } catch (Exception e) {
            message("تعذر تشغيل Termux: " + e.getClass().getSimpleName());
        }
    }

    public class Bridge {
        @JavascriptInterface public void copySetup() {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Termux configuration", SETUP_COMMAND));
                message("الأمر اتنسخ. الصقه في Termux واضغط Enter.");
            });
        }
        @JavascriptInterface public void openTermux() {
            runOnUiThread(() -> {
                if (!hasTermux()) { openUrl("https://f-droid.org/packages/com.termux/"); return; }
                Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
                if (launch != null) startActivity(launch);
                else message("افتح Termux من شاشة التطبيقات");
            });
        }
        @JavascriptInterface public void permission() { runOnUiThread(() -> requirePermission()); }
        @JavascriptInterface public void appSettings() {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            });
        }
        @JavascriptInterface public void install() {
            runOnUiThread(() -> {
                String folder = "$HOME/PhoneCraft-Android-Host";
                String cmd = "set -e; pkg update -y && pkg install -y git && "
                    + "(if [ -d \"" + folder + "/.git\" ]; then git -C \"" + folder + "\" pull --ff-only; "
                    + "else git clone --depth 1 -b " + BRANCH + " " + REPO + ".git \"" + folder + "\"; fi) && "
                    + "bash \"" + folder + "/install-termux.sh\" && "
                    + "echo 'PhoneCraft installed! Return to the PhoneCraft app and tap Start Engine.'";
                runInTermux(cmd, true, "تثبيت محرك PhoneCraft");
            });
        }
        @JavascriptInterface public void start() {
            runOnUiThread(() -> runInTermux("if [ -f \"$HOME/phonecraft-start\" ]; then bash \"$HOME/phonecraft-start\"; else echo 'Install PhoneCraft first.'; fi", true, "تشغيل لوحة PhoneCraft"));
        }
        @JavascriptInterface public void showPanel() { runOnUiThread(() -> openPanel()); }
        @JavascriptInterface public void openCreate(String mode, String version, String name) {
            runOnUiThread(() -> {
                if (!mode.matches("paper|fabric") ||
                    !version.matches("26\\.3|1\\.21\\.(11|10|8|4)|1\\.20\\.6") ||
                    name == null || name.trim().isEmpty() || name.length() > 40) {
                    message("اختيارات السيرفر مش صحيحة"); return;
                }
                try {
                    String url = "?create=1&mode=" + java.net.URLEncoder.encode(mode, "UTF-8")
                      + "&version=" + java.net.URLEncoder.encode(version, "UTF-8")
                      + "&name=" + java.net.URLEncoder.encode(name, "UTF-8");
                    openPanelPath(url);
                } catch (Exception e) { message("تعذر فتح صفحة الإنشاء"); }
            });
        }
        @JavascriptInterface public void refresh() { checkStatus(); }
        @JavascriptInterface public void repo() { runOnUiThread(() -> openUrl(REPO + "/tree/" + BRANCH)); }
        @JavascriptInterface public void termux() { runOnUiThread(() -> openUrl("https://f-droid.org/packages/com.termux/")); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0)
            message(grantResults[0] == PackageManager.PERMISSION_GRANTED ? "تم منح التصريح" : "امنح التصريح من إعدادات التطبيق > الأذونات الإضافية");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileChooser != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                ? new Uri[]{data.getData()} : null;
            fileChooser.onReceiveValue(result);
            fileChooser = null;
        }
    }

    @Override public void onBackPressed() {
        if (panelShowing) goHome();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
