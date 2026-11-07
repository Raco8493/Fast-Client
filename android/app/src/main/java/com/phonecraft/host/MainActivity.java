package com.phonecraft.host;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
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
                // Never silently block the EULA link. Open Minecraft's actual
                // terms in the external browser, where the user can read them.
                String url=request.getUrl().toString();
                if(url.equals("https://www.minecraft.net/en-us/eula")) {
                    startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
                }
                return !HOME.equals(url);
            }
        });
        view.addJavascriptInterface(new Bridge(), "Hostphone");
        setContentView(view);
        view.loadUrl(HOME);
        ui.postDelayed(telemetry, 900);
        // First open unpacks the already-bundled ARM64 Java engine. Once EULA
        // was accepted on a previous launch, missing server JARs auto-download.
        startHostService(HostService.PREPARE);
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

    private String startHostService(String action){
        try {
            Intent intent=new Intent(this,HostService.class);
            intent.setAction(action);
            startForegroundService(intent);
            return "بدأت العملية على الموبايل؛ تابع الحالة في صفحة السيرفر";
        }catch(Exception e){return "تعذر بدء الخدمة: "+e.getMessage();}
    }

    private int defaultRamMb(){
        ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();am.getMemoryInfo(mem);
        return (int)Math.max(512,Math.min(2048,mem.totalMem/1048576L-2048));
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
                j.put("ramMb", s.getInt("ramMb", defaultRamMb()));
                j.put("eulaAccepted",s.getBoolean("eulaAccepted",false));
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
            if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("eulaAccepted",false))
                startHostService(HostService.INSTALL);
            return "تم حفظ الإعدادات"+(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("eulaAccepted",false)?"، وبدأ التحميل التلقائي لإصدار السيرفر":" — وافق على EULA ليبدأ تحميل Minecraft تلقائيًا");
        }

        @JavascriptInterface public String setResources(int ramMb, int cpuCores) {
            int cores = Runtime.getRuntime().availableProcessors();
            if (ramMb < 512 || ramMb > 32768 || cpuCores < 1 || cpuCores > cores)
                return "اختيار موارد غير صحيح";
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putInt("ramMb",ramMb).putInt("cpuCores",cpuCores).apply();
            return "تم ضبط الحد الأقصى الحقيقي لرام Java إلى " + ramMb + "MB (Xmx). يبدأ التطبيق به عند التشغيل القادم، إذا الذاكرة متاحة";
        }

        @JavascriptInterface public String acceptEula(boolean accepted) {
            if (!accepted) return "اقرأ Minecraft EULA وعلّم على مربع الموافقة الأول";
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("eulaAccepted",true).apply();
            return "تم تسجيل موافقتك. " + startHostService(HostService.INSTALL);
        }

        @JavascriptInterface public void openEula(){
            runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.minecraft.net/en-us/eula"))));
        }

        @JavascriptInterface public String serverState() { return HostService.json(); }

        @JavascriptInterface public String installServer(boolean acceptedEula) {
            if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("eulaAccepted",false)){
                if(!acceptedEula) return "الموافقة على EULA مطلوبة — افتح الشروط وأكّد الموافقة";
                return acceptEula(true);
            }
            return startHostService(HostService.INSTALL);
        }
        @JavascriptInterface public String startServer() {
            if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("eulaAccepted",false))
                return "لازم توافق على اتفاقية Minecraft EULA قبل تشغيل السيرفر";
            return startHostService(HostService.START);
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
