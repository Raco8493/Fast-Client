package com.phonecraft.host;

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
    private static volatile Process child;
    private static volatile long started=0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private ServerEngine engine;
    static final String INSTALL="com.phonecraft.host.INSTALL",START="com.phonecraft.host.START",STOP="com.phonecraft.host.STOP";

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
        if(busy||(child!=null&&child.isAlive())){
            log("سيرفر موجود أو عملية تثبيت شغالة بالفعل");return START_NOT_STICKY;
        }
        busy=true;
        SharedPreferences p=getSharedPreferences("hostphone_settings",MODE_PRIVATE);
        String selected=p.getString("kind","java");
        String target="bedrock".equals(selected)?p.getString("bedrockVersion","26.50"):p.getString("javaVersion","26.3");
        int ram=p.getInt("ramPercent",70),cpu=p.getInt("cpuCores",2);
        worker.execute(()->{
            kind=selected;version=target;error="";
            try{
                if(INSTALL.equals(action)){
                    state="installing";log("تثبيت "+selected+" "+target);
                    engine.install(selected,target,true);
                    state="installed";notification("السيرفر اتثبت — اضغط تشغيل");
                }else if(START.equals(action)){
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
            finally{busy=false;}
        });
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){releaseLocks();worker.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
