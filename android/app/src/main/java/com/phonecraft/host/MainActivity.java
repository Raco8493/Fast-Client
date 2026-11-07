package com.phonecraft.host;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** Hostphone v1.3: Android foreground-service server management. Java runtime
 * and Minecraft server JARs are managed by ServerEngine and HostService. */
public final class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/launcher.html";
    private static final String PREFS = "hostphone_settings";
    private WebView view;
    private Handler ui = new Handler(Looper.getMainLooper());
    private boolean alive = true;
    private long lastCpuMs = 0;
    private long lastElapsedMs = 0;
    private final Runnable telemetry = new Runnable() {
        @Override public void run() {
            if (alive && view != null) {
                final String payload = metrics().toString();
                view.evaluateJavascript("window.receiveHardware(" + payload + ")", null);
                ui.postDelayed(this, 1600);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF15060B);
        getWindow().setNavigationBarColor(0xFF11070B);
        view = new WebView(this);
        view.setBackgroundColor(0xFF0C080C);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest request) {
                return !HOME.equals(request.getUrl().toString());
            }
        });
        view.addJavascriptInterface(new Bridge(), "Hostphone");
        setContentView(view);
        view.loadUrl(HOME);
        ui.postDelayed(telemetry, 900);
    }

    private JSONObject metrics() {
        JSONObject j = new JSONObject();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        long total = mem.totalMem;
        long free = mem.availMem;
        Debug.MemoryInfo procMem = new Debug.MemoryInfo();
        Debug.getMemoryInfo(procMem);
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long cpuMs = Process.getElapsedCpuTime();
        long elapsedMs = SystemClock.elapsedRealtime();
        double appCpu = 0;
        if (lastElapsedMs > 0 && elapsedMs > lastElapsedMs) {
            // Fraction of *all* logical CPU capacity consumed by this process.
            appCpu = 100.0 * Math.max(0, cpuMs - lastCpuMs) / ((elapsedMs - lastElapsedMs) * cores);
            appCpu = Math.max(0, Math.min(100, appCpu));
        }
        lastCpuMs = cpuMs;
        lastElapsedMs = elapsedMs;
        try {
            j.put("ramTotalMB", total / 1048576L);
            j.put("ramFreeMB", free / 1048576L);
            j.put("ramUsedMB", (total-free) / 1048576L);
            j.put("appRamMB", procMem.getTotalPss() / 1024);
            j.put("appCpuPercent", Math.round(appCpu * 10.0) / 10.0);
            j.put("cores", cores);
            j.put("lowMemory", mem.lowMemory);
            j.put("engine", "arm64-jre25");
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                j.put("thermalStatus", pm.getCurrentThermalStatus());
            } else j.put("thermalStatus", -1);
        } catch (Exception ignored) { }
        return j;
    }

    private static int bound(int n, int a, int b) { return Math.max(a, Math.min(b, n)); }

    public final class Bridge {
        @JavascriptInterface public String initialState() {
            SharedPreferences s = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONObject j = new JSONObject();
            try {
                j.put("kind", s.getString("kind", "java"));
                j.put("name", s.getString("name", "My Hostphone World"));
                j.put("javaVersion", s.getString("javaVersion", "26.3"));
                j.put("bedrockVersion", s.getString("bedrockVersion", "26.50"));
                j.put("ramPercent", s.getInt("ramPercent", 70));
                j.put("cpuCores", s.getInt("cpuCores", Math.max(1,Runtime.getRuntime().availableProcessors()-1)));
            } catch (Exception ignored) { }
            return j.toString();
        }

        @JavascriptInterface public String saveConfig(String kind, String name, String javaVersion, String bedrockVersion) {
            if (!kind.matches("java|bedrock|geyser") || !javaVersion.matches("26\\.3|26\\.2|1\\.21\\.11") ||
                !bedrockVersion.matches("26\\.60|26\\.50") || name == null ||
                name.trim().length() < 1 || name.trim().length() > 36) {
                return "رفض الإعدادات: تأكد من النوع والاسم والإصدار";
            }
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString("kind",kind).putString("name",name.trim())
                .putString("javaVersion",javaVersion).putString("bedrockVersion",bedrockVersion).apply();
            return "تم حفظ إعدادات السيرفر محليًا";
        }

        @JavascriptInterface public String setResources(int ramPercent, int cpuCores) {
            int cores = Runtime.getRuntime().availableProcessors();
            if (ramPercent < 25 || ramPercent > 85 || cpuCores < 1 || cpuCores > cores) return "اختيار موارد غير صحيح";
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putInt("ramPercent",ramPercent).putInt("cpuCores",cpuCores).apply();
            return "تم حفظ الموارد؛ هتتطبق عند التشغيل الجاي حسب الرام المتاحة";
        }

        @JavascriptInterface public String serverState() { return HostService.json(); }

        @JavascriptInterface public String installServer(boolean acceptedEula) {
            if (!acceptedEula) return "وافق على EULA الأول قبل التثبيت";
            if (!android.os.Build.SUPPORTED_ABIS[0].equals("arm64-v8a"))
                return "المحرك ده لأجهزة ARM64 فقط حاليًا";
            Intent intent = new Intent(MainActivity.this, HostService.class);
            intent.setAction(HostService.INSTALL);
            try { startForegroundService(intent);return "بدأ تثبيت المحرك والسيرفر — تابع الحالة والسجل"; }
            catch (Exception e) {return "فشل بدء الخدمة: " + e.getMessage();}
        }
        @JavascriptInterface public String startServer() {
            Intent intent = new Intent(MainActivity.this, HostService.class);
            intent.setAction(HostService.START);
            try {startForegroundService(intent);return "جارٍ بدء سيرفر Minecraft الحقيقي — تابع حالة التشغيل";}
            catch(Exception e){return "فشل التشغيل: " + e.getMessage();}
        }
        @JavascriptInterface public String stopServer() {
            Intent intent = new Intent(MainActivity.this, HostService.class);
            intent.setAction(HostService.STOP);
            try {startForegroundService(intent);return "إرسال طلب إيقاف السيرفر";}
            catch(Exception e){return "خطأ: " + e.getMessage();}
        }
        @JavascriptInterface public String sendCommand(String command) {return HostService.command(command);}

    }

    @Override protected void onDestroy() {
        alive = false;
        ui.removeCallbacksAndMessages(null);
        if (view != null) { view.removeJavascriptInterface("Hostphone"); view.destroy(); }
        super.onDestroy();
    }
}
