package com.phonecraft.host;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.net.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String HOST = "http://127.0.0.1:8765/";
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String REPO = "https://github.com/Raco8493/Fast-Client.git";
    private TextView status;
    private WebView browser;
    private LinearLayout screen;
    private ValueCallback<Uri[]> chooser;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean panel = false;
    private boolean alive = true;
    private static final int PICK_JAR = 302;
    private static final String ENABLE =
        "mkdir -p ~/.termux && touch ~/.termux/termux.properties && sed -i '/^allow-external-apps=/d' ~/.termux/termux.properties && printf 'allow-external-apps=true\\n' >> ~/.termux/termux.properties && termux-reload-settings";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,14,22));
        getWindow().setNavigationBarColor(Color.rgb(10,14,22));
        home();
    }
    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(16));
        g.setStroke(dp(1), stroke); return g;
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE);
        t.setTextSize(size); t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void card(String heading, String description, String[][] actions) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(18),dp(16),dp(18),dp(18));
        c.setBackground(bg(Color.rgb(21,28,40), Color.rgb(44,55,72)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(12); screen.addView(c,cp);
        TextView t=text(heading,18,true); c.addView(t);
        TextView d=text(description,13,false); d.setTextColor(Color.rgb(183,196,207));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(8); p.bottomMargin=dp(12); c.addView(d,p);
        for(String[] action:actions) {
            Button b = new Button(this);
            b.setAllCaps(false); b.setText(action[0]); b.setTextColor(Color.WHITE);
            b.setBackground(bg(action[2].equals("1")?Color.rgb(184,49,74):Color.rgb(39,59,77), Color.rgb(69,86,102)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(49)); bp.topMargin=dp(8); c.addView(b,bp);
            b.setOnClickListener(v -> onAction(action[1]));
        }
    }
    private void home() {
        panel=false;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(10,14,22));
        screen=new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18),dp(23),dp(18),dp(36)); screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(screen);
        TextView h=text("⛏  PhoneCraft",31,true); h.setTextColor(Color.rgb(254,121,139)); screen.addView(h);
        TextView sub=text("Minecraft Java + Bedrock | جهازك هو السيرفر",14,false);
        sub.setTextColor(Color.rgb(194,199,218)); screen.addView(sub);
        status=text("● جاري فحص محرك السيرفر...",14,true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(14); sp.bottomMargin=dp(20); screen.addView(status,sp);
        card("🎮 إدارة السيرفر", "الموبايل هو المستضيف الفعلي. شغّل محرك Termux، وبعدين افتح لوحة التحكم للـOP والـMods والـCommands.",new String[][]{
            {"فتح لوحة التحكم", "panel","1"}, {"تحديث الحالة","refresh","0"}
        });
        card("① ثبّت Termux واضبط الربط", "نزّل Termux المجاني وافتحه مرة. انسخ أمر السماح بالربط والصقه في Termux واضغط Enter.",new String[][]{
            {"تحميل Termux", "download","0"},{"نسخ أمر الربط", "copy","0"},{"فتح Termux", "termux","0"}
        });
        card("② امنح صلاحية التشغيل", "من إعدادات PhoneCraft، اسمح بـ Run commands in Termux environment تحت Additional permissions.",new String[][]{
            {"طلب الإذن","permission","1"},{"إعدادات التطبيق","settings","0"}
        });
        card("③ تثبيت محرك PhoneCraft", "اضغط تثبيت أول مرة. هيحمّل المشروع وJava وPython على نفس الموبايل عبر Termux. استنى نجاح التثبيت في شاشة Termux.",new String[][]{
            {"تثبيت المحرك","install","1"},{"تشغيل المحرك","start","0"}
        });
        card("④ اصنع عالمك", "افتح لوحة التحكم، اختار Paper للـPlugins أو Fabric للمودات، وافق على EULA، ثم ثبّت السيرفر وشغّله.",new String[][]{
            {"لوحة الإدارة","panel","1"},{"كود GitHub","repo","0"}
        });
        TextView warning=text("تنبيه: الـAPK واجهة حقيقية، لكن تشغيل سيرفر Java نفسه يعتمد على Termux. عطّل تحسين البطارية لـTermux. عند السخونة أو قفل أندرويد للخدمة ممكن السيرفر يقف.",12,false);
        warning.setTextColor(Color.rgb(235,177,137)); screen.addView(warning);
        setContentView(scroll);
        refresh();
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void external(String url) { try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("مش قادر أفتح الرابط");} }
    private boolean hasTermux() {
        try { getPackageManager().getPackageInfo("com.termux",0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    private void requestRunPermission() {
        if(!hasTermux()){toast("نزّل Termux الأول"); return;}
        if (checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{PERMISSION},123);
        else toast("الإذن متاح. اتأكد من allow-external-apps=true جوه Termux.");
    }
    private void runTermux(String cmd, String title) {
        if(!hasTermux()){toast("ثبّت Termux الأول من F-Droid"); return;}
        if(checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestRunPermission(); toast("بعد منح الإذن اضغط الزر تاني"); return;}
        try {
            Intent i = new Intent("com.termux.RUN_COMMAND");
            i.setClassName("com.termux","com.termux.app.RunCommandService");
            i.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-lc",cmd});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home");
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND",false);
            i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0");
            i.putExtra("com.termux.RUN_COMMAND_LABEL",title);
            startService(i);
            toast(title+" — تابع شاشة Termux");
        } catch(Exception e){toast("فشل الربط مع Termux: "+e.getClass().getSimpleName());}
    }
    private void onAction(String a) {
        switch(a) {
            case "download":external("https://f-droid.org/packages/com.termux/");break;
            case "copy":
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PhoneCraft",ENABLE));
                toast("تم نسخ الأمر؛ الصقه جوه Termux");break;
            case "termux":
                if(!hasTermux()){external("https://f-droid.org/packages/com.termux/");break;}
                Intent open=getPackageManager().getLaunchIntentForPackage("com.termux");
                if(open!=null)startActivity(open);else toast("افتح Termux من التطبيقات");break;
            case "permission":requestRunPermission();break;
            case "settings":startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));break;
            case "install":
                runTermux("set -e; pkg update -y && pkg install -y git && (if [ -d \"$HOME/PhoneCraft-Android-Host/.git\" ]; then git -C \"$HOME/PhoneCraft-Android-Host\" pull --ff-only; else git clone --depth 1 -b phonecraft-android-host "+REPO+" \"$HOME/PhoneCraft-Android-Host\"; fi) && bash \"$HOME/PhoneCraft-Android-Host/install-termux.sh\" && echo 'PhoneCraft installed - return to APK and tap Start Engine'","تثبيت محرك PhoneCraft");break;
            case "start":
                runTermux("if [ -f \"$HOME/phonecraft-start\" ]; then bash \"$HOME/phonecraft-start\"; else echo 'Install engine first'; fi","تشغيل محرك PhoneCraft");break;
            case "panel":openPanel();break;
            case "refresh":refresh();break;
            case "repo":external("https://github.com/Raco8493/Fast-Client/tree/phonecraft-android-host");break;
        }
    }
    private void refresh() {
        if(panel||status==null)return;
        new Thread(() -> {
            String st="● المحرك مش شغال — اضغط تشغيل المحرك";
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(HOST+"api/status").openConnection();
                c.setConnectTimeout(1500); c.setReadTimeout(1500);
                if(c.getResponseCode()==200){
                    java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                    try(java.io.InputStream in=c.getInputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))>0 && buf.size()<150000)buf.write(block,0,n);}
                    JSONObject obj=new JSONObject(buf.toString("UTF-8"));
                    st=obj.optBoolean("running")?"● سيرفر Minecraft شغال":"● لوحة PhoneCraft شغالة؛ جاهزة للإعداد";
                }
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
            final String message=st;
            runOnUiThread(() -> { if(!panel && status!=null) {status.setText(message);status.setTextColor(message.contains("شغال")?Color.rgb(90,226,155):Color.rgb(244,177,164));}});
        }).start();
    }
    private void openPanel() {
        panel=true;
        browser=new WebView(this);
        browser.setBackgroundColor(Color.rgb(10,14,22));
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                Uri u=request.getUrl();
                if("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort()==8765)return false;
                if("https".equals(u.getScheme()))external(u.toString());
                return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params){
                if(chooser!=null)chooser.onReceiveValue(null);
                chooser=cb;
                try {Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_JAR);return true;}
                catch(Exception e){chooser=null;return false;}
            }
        });
        setContentView(browser);
        browser.loadUrl(HOST);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_JAR && chooser!=null) {chooser.onReceiveValue(resultCode==RESULT_OK && data!=null && data.getData()!=null?new Uri[]{data.getData()}:null);chooser=null;}
    }
    @Override public void onBackPressed() {
        if(panel) {if(browser!=null){browser.destroy();browser=null;} home();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {alive=false;handler.removeCallbacksAndMessages(null);if(browser!=null)browser.destroy();super.onDestroy();}
}
