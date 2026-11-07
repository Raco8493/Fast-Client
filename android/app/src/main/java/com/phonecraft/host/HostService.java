package com.phonecraft.host;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A foreground hosting service: status reflects a *real* child JVM process. */
public final class HostService extends Service {
    private static final String CHANNEL="hostphone.server";
    private static final int NOTIF=41313;
    private static final Object LOCK=new Object();
    private static final ArrayDeque<String> LINES=new ArrayDeque<>();
    private static volatile String state="idle", error="", kind="", version="";
    private static volatile boolean busy=false;
    private static volatile String queuedAction=null;
    private static volatile Process child;
    private static volatile long started=0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private ServerEngine engine;
    static final String PREPARE="com.phonecraft.host.PREPARE",INSTALL="com.phonecraft.host.INSTALL",START="com.phonecraft.host.START",STOP="com.phonecraft.host.STOP";

    static void log(String message){
        synchronized(LOCK){
            String s=message.replace('\r',' ').replace('\n',' ');
            if(s.length()>400)s=s.substring(0,400);
            LINES.addLast(s);while(LINES.size()>90)LINES.removeFirst();
        }
    }
    static String json(){
        JSONObject j=new JSONObject();
        try{
            j.put("state",state);j.put("error",error);j.put("kind",kind);j.put("version",version);
            j.put("busy",busy);j.put("startedAt",started);
            j.put("alive",child!=null&&child.isAlive());
            synchronized(LOCK){StringBuilder b=new StringBuilder();for(String line:LINES)b.append(line).append('\n');j.put("log",b.toString());}
        }catch(Exception ignored){}
        return j.toString();
    }
    static String command(String line){
        Process proc=child;
        if(proc==null||!proc.isAlive())return "السيرفر مش شغال";
        String s=line==null?"":line.trim();
        if(s.length()==0||s.length()>240||s.contains("\n")||s.contains("\r"))return "كوماند غير صالح";
        try{
            synchronized(proc){OutputStream stream=proc.getOutputStream();stream.write((s+"\n").getBytes(StandardCharsets.UTF_8));stream.flush();}
            log("> "+s);return "تم إرسال الأمر للسيرفر";
        }catch(Exception e){return "فشل إرسال الأمر: "+e.getMessage();}
    }
    @Override public void onCreate(){super.onCreate();engine=new ServerEngine(this,HostService::log);}

    private void notification(String title){
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Hostphone server",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent tap=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Hostphone")
            .setContentText(title).setOngoing(true).setContentIntent(tap).build();
        startForeground(NOTIF,n);
    }
    private void locks(){
        try{
            PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
            if(wake==null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Hostphone:MinecraftServer");wake.acquire();}
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wm!=null&&wifi==null){wifi=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"HostphoneServer");wifi.acquire();}
        }catch(Exception e){log("تنبيه: تعذّر إبقاء Wi-Fi/CPU نشط: "+e.getMessage());}
    }
    private void releaseLocks(){if(wake!=null){if(wake.isHeld())wake.release();wake=null;}
        if(wifi!=null){if(wifi.isHeld())wifi.release();wifi=null;}}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if(action==null)return START_NOT_STICKY;
        notification("إدارة سيرفر Minecraft على الموبايل");
        if(STOP.equals(action)){
            worker.execute(()->{
                Process p=child;
                if(p!=null&&p.isAlive()){
                    state="stopping";command("stop");
                    try{if(!p.waitFor(12,TimeUnit.SECONDS)){p.destroy();if(!p.waitFor(4,TimeUnit.SECONDS))p.destroyForcibly();}}
                    catch(Exception ignored){p.destroy();}
                }
                state="stopped";child=null;releaseLocks();stopForeground(true);stopSelf();
            });
            return START_NOT_STICKY;
        }
        if(busy){
            if(INSTALL.equals(action)||START.equals(action)){
                queuedAction=START.equals(action)?START:queuedAction==null?INSTALL:queuedAction;
                log("هيتم تنفيذ الطلب تلقائيًا بعد تجهيز المحرك الحالي");
            }
            return START_NOT_STICKY;
        }
        if(child!=null&&child.isAlive()){
            log("السيرفر شغال بالفعل");return START_NOT_STICKY;
        }
        busy=true;
        SharedPreferences p=getSharedPreferences("hostphone_settings",MODE_PRIVATE);
        String selected=p.getString("kind","java");
        String target="bedrock".equals(selected)?p.getString("bedrockVersion","26.50"):p.getString("javaVersion","26.3");
        ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);
        int defaultRam=(int)Math.max(512,Math.min(2048,mi.totalMem/1048576-2048));
        int ram=p.getInt("ramMb",defaultRam),cpu=p.getInt("cpuCores",2);
        boolean eula=p.getBoolean("eulaAccepted",false);
        worker.execute(()->{
            kind=selected;version=target;error="";
            try{
                if(PREPARE.equals(action)){
                    state="preparing";notification("تجهيز محرك Java ARM64 المدمج");
                    engine.installRuntime();
                    SharedPreferences latest=getSharedPreferences("hostphone_settings",MODE_PRIVATE);
                    if(latest.getBoolean("eulaAccepted",false)){
                        String currentKind=latest.getString("kind","java");
                        String currentVersion="bedrock".equals(currentKind)?latest.getString("bedrockVersion","26.50"):latest.getString("javaVersion","26.3");
                        kind=currentKind;version=currentVersion;
                        state="installing";notification("تحميل ملفات سيرفر Minecraft تلقائيًا");
                        engine.install(currentKind,currentVersion,true);
                        state="installed";notification("Hostphone جاهز لتشغيل السيرفر");
                    }else{state="waiting_eula";notification("المحرك جاهز — وافق على اتفاقية Minecraft أولاً");}
                }else if(INSTALL.equals(action)){
                    if(!eula)throw new IllegalStateException("الموافقة على اتفاقية Minecraft مطلوبة");
                    state="installing";notification("تحميل وتثبيت ملفات Minecraft تلقائيًا");
                    engine.install(selected,target,true);
                    state="installed";notification("السيرفر اتثبت — اضغط تشغيل");
                }else if(START.equals(action)){
                    if(!eula)throw new IllegalStateException("وافق على EULA قبل بدء تشغيل السيرفر");
                    if(!engine.installed(selected,target)){
                        state="installing";notification("تحميل ملفات السيرفر قبل أول تشغيل");
                        engine.install(selected,target,true);
                    }
                    state="starting";locks();
                    Process proc=engine.start(selected,target,ram,cpu);
                    child=proc;started=System.currentTimeMillis();
                    notification("السيرفر بيشتغل على جهازك");
                    state="starting";
                    Thread reader=new Thread(()->{
                        try(BufferedReader r=new BufferedReader(new InputStreamReader(proc.getInputStream(),StandardCharsets.UTF_8))){
                            String line;
                            while((line=r.readLine())!=null){
                                log(line);
                                if(line.contains("Done (")||line.contains("Done!")||line.contains("Server started")){
                                    if(proc.isAlive()){state="online";notification("سيرفر Minecraft شغّال على موبايلك");}
                                }
                                if("geyser".equals(selected)&&line.toLowerCase().contains("floodgate")){
                                    // Floodgate/Geyser need a matching auth-type config; log only.
                                }
                            }
                        }catch(Exception e){log("قراءة اللوج توقفت: "+e.getMessage());}
                    },"MinecraftLogReader");reader.setDaemon(true);reader.start();
                    new Thread(()->{
                        try{
                            int exit=proc.waitFor();
                            log("عملية السيرفر انتهت بكود: "+exit);
                            if(exit!=0){state="error";error="السيرفر توقف بخطأ "+exit+" — راجع سجل التشغيل";}
                            else state="stopped";
                        }catch(Exception e){state="error";error=e.toString();}
                        finally{if(child==proc)child=null;releaseLocks();stopForeground(true);stopSelf();}
                    },"MinecraftExitWaiter").start();
                }
            }catch(Exception e){error=e.toString();state="error";log("ERROR: "+error);releaseLocks();notification("خطأ في السيرفر — افتح السجل");}
            finally{
                busy=false;
                String next=queuedAction;queuedAction=null;
                if(next!=null){
                    Intent queued=new Intent(HostService.this,HostService.class);
                    queued.setAction(next);startService(queued);
                }else if(child==null){stopForeground(true);stopSelf();}
            }
        });
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){releaseLocks();worker.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
