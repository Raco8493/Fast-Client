package com.phonecraft.host;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Real on-device Minecraft server engine. No Termux or external server is used.
 * Runtime: Android ARM64 OpenJDK, bundled by the CI build pipeline. */
final class ServerEngine {
    static final String[] JAVA_VERSIONS = {"26.3", "26.2", "1.21.11"};
    private static final long MAX_DOWNLOAD_BYTES = 300L * 1024 * 1024;
    private final Context context;
    interface Logger { void message(String s); }
    private final Logger logger;

    ServerEngine(Context context, Logger logger) {this.context=context;this.logger=logger;}

    File base() {File f=new File(context.getFilesDir(),"hosting");if(!f.exists())f.mkdirs();return f;}
    File runtime() {return new File(base(),"java25");}
    boolean runtimeReady() {return new File(runtime(),".ready").isFile();}
    boolean installed(String kind,String version) {File f=profile(kind,version);return new File(f,".installed").isFile()&&new File(f,"server.jar").isFile();}
    File profile(String kind, String version) {return new File(base(),kind+"-"+version.replaceAll("[^0-9A-Za-z.]","_"));}

    static void copy(InputStream in, OutputStream out, long cap) throws IOException {
        byte[] b=new byte[65536]; int n; long total=0;
        while ((n=in.read(b))!=-1) {total+=n;if(total>cap)throw new IOException("حجم التحميل أكبر من الحد المسموح");out.write(b,0,n);}
    }

    /** The bundled archive is deliberately a ZIP produced by CI, not an untrusted download. */
    void installRuntime() throws Exception {
        File folder=runtime();
        if(new File(folder,".ready").exists())return;
        logger.message("استخراج محرك Java 25 على الموبايل...");
        if(!"arm64-v8a".equals(Build.SUPPORTED_64_BIT_ABIS.length>0?Build.SUPPORTED_64_BIT_ABIS[0]:""))
            throw new IOException("المحرك المدمج حاليًا لأجهزة ARM64 فقط");
        if(!folder.exists()&&!folder.mkdirs())throw new IOException("تعذر إنشاء مجلد المحرك");
        try(InputStream src=context.getAssets().open("runtime25.zip");ZipInputStream z=new ZipInputStream(new BufferedInputStream(src))) {
            ZipEntry e;long written=0;int entries=0;
            String canonicalBase=folder.getCanonicalPath()+File.separator;
            while((e=z.getNextEntry())!=null) {
                if(++entries>35000)throw new IOException("أرشيف Java غير صالح");
                File out=new File(folder,e.getName());
                if(!out.getCanonicalPath().startsWith(canonicalBase))throw new IOException("مسار غير آمن في أرشيف Java");
                if(e.isDirectory()) {out.mkdirs();continue;}
                if(!out.getParentFile().exists()&&!out.getParentFile().mkdirs())throw new IOException("تعذر تجهيز المسار");
                try(FileOutputStream f=new FileOutputStream(out)) {
                    byte[] b=new byte[65536];int n;
                    while((n=z.read(b))!=-1) {written+=n;if(written>550L*1024*1024)throw new IOException("أرشيف Java كبير جدًا");f.write(b,0,n);}
                }
            }
        } catch(Exception e) {new File(folder,".ready").delete();throw e;}
        File executable=new File(context.getApplicationInfo().nativeLibraryDir,"libhostphone_java.so");
        if(!executable.exists())throw new IOException("ملف تشغيل ARM64 غير موجود داخل التطبيق");
        File javaBin=new File(folder,"bin/java");
        if(!javaBin.getParentFile().exists())javaBin.getParentFile().mkdirs();
        if(javaBin.exists()&&!javaBin.delete())throw new IOException("تعذر تحديث Java launcher");
        // Android 10+ disallows executing code from writable app data. A symlink
        // points to the extracted/approved executable within nativeLibraryDir.
        Files.createSymbolicLink(javaBin.toPath(),executable.toPath());
        new File(folder,".ready").createNewFile();
        logger.message("محرك Java ARM64 جاهز");
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(18000);c.setReadTimeout(24000);
        c.setRequestProperty("User-Agent","Hostphone-Android/1.3");
        c.setRequestProperty("Accept","application/json");
        try {
            if(c.getResponseCode()!=200)throw new IOException("خدمة الإصدارات لم تستجب: "+c.getResponseCode());
            try(InputStream in=c.getInputStream()) {java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();copy(in,bytes,4*1024*1024);return new JSONObject(bytes.toString("UTF-8"));}
        } finally {c.disconnect();}
    }
    private static JSONArray getArray(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(18000);c.setReadTimeout(24000);
        c.setRequestProperty("User-Agent","Hostphone-Android/1.3");
        try {if(c.getResponseCode()!=200)throw new IOException("خطأ API: "+c.getResponseCode());
            try(InputStream in=c.getInputStream()){java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();copy(in,b,4*1024*1024);return new JSONArray(b.toString("UTF-8"));}
        } finally {c.disconnect();}
    }

    private void download(String url, File dest, String expectedSha256) throws Exception {
        logger.message("تحميل "+dest.getName()+"...");
        if(!url.startsWith("https://"))throw new IOException("روابط HTTPS فقط");
        if(!dest.getParentFile().exists()&&!dest.getParentFile().mkdirs())throw new IOException("تعذر إنشاء المجلد");
        File temp=new File(dest.getAbsolutePath()+".part");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(30000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Hostphone-Android/1.3");
        try {
            if(c.getResponseCode()!=200)throw new IOException("تحميل فشل: HTTP "+c.getResponseCode());
            if(c.getContentLengthLong()>MAX_DOWNLOAD_BYTES)throw new IOException("ملف أكبر من الحد");
            final String algorithm = expectedSha256!=null && expectedSha256.length()==40 ? "SHA-1" : "SHA-256";
            MessageDigest digest=MessageDigest.getInstance(algorithm);
            try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                byte[] b=new byte[65536];int n;long size=0;
                while((n=in.read(b))!=-1){size+=n;if(size>MAX_DOWNLOAD_BYTES)throw new IOException("ملف كبير جدًا");digest.update(b,0,n);out.write(b,0,n);}
                if(size<10000)throw new IOException("الملف المحمل صغير بشكل غير متوقع");
            }
            if(expectedSha256!=null&&!expectedSha256.isEmpty()){
                StringBuilder hex=new StringBuilder();for(byte x:digest.digest())hex.append(String.format(Locale.US,"%02x",x & 255));
                if(!hex.toString().equalsIgnoreCase(expectedSha256))throw new IOException("فشل فحص "+algorithm+" لملف السيرفر");
            }
            try(FileInputStream in=new FileInputStream(temp)){if(in.read()!=0x50||in.read()!=0x4b)throw new IOException("التحميل مش JAR صالح");}
            if(dest.exists()&&!dest.delete())throw new IOException("تعذر استبدال الملف");
            if(!temp.renameTo(dest))throw new IOException("تعذر نقل الملف بعد التحميل");
        } finally {c.disconnect();if(temp.exists())temp.delete();}
    }

    /** Install a playable Java or Bedrock server JAR and Geyser bridge when requested. */
    void install(String kind,String version,boolean eula) throws Exception {
        if(!eula)throw new IOException("لازم توافق على EULA بوضوح قبل تنزيل وتشغيل السيرفر");
        if(installed(kind,version)){logger.message("ملفات سيرفر Minecraft موجودة بالفعل: "+kind+" "+version);return;}
        installRuntime();
        File dir=profile(kind,version);if(!dir.exists()&&!dir.mkdirs())throw new IOException("تعذر إنشاء السيرفر");
        File jar=new File(dir,"server.jar");
        if("bedrock".equals(kind)) {
            String tag="26.60".equals(version)?"snapshot":"3.0.5";
            JSONObject rel=getJson("https://api.github.com/repos/PowerNukkitX/PowerNukkitX/releases/tags/"+tag);
            JSONArray assets=rel.getJSONArray("assets");String url=null;
            for(int i=0;i<assets.length();i++){
                JSONObject a=assets.getJSONObject(i);String name=a.optString("name");
                if(name.equalsIgnoreCase("powernukkitx.jar")){url=a.getString("browser_download_url");break;}
            }
            if(url==null)throw new IOException("ملف PowerNukkitX مفقود من الإصدار");
            download(url,jar,null);
            logger.message("PowerNukkitX جاهز. المنفذ الافتراضي 19132/UDP");
        }else if("java".equals(kind)){
            // The actual official Mojang/Microsoft Vanilla Minecraft Java server.
            // Fetches directly from Mojang only after the user explicitly accepts
            // Minecraft EULA; the proprietary jar is not redistributed in the APK.
            JSONObject manifest=getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            JSONArray versions=manifest.getJSONArray("versions");String metadataUrl=null;
            for(int i=0;i<versions.length();i++){
                JSONObject item=versions.getJSONObject(i);
                if(version.equals(item.optString("id")) && "release".equals(item.optString("type"))){
                    metadataUrl=item.getString("url");break;
                }
            }
            if(metadataUrl==null || !metadataUrl.startsWith("https://piston-meta.mojang.com/"))
                throw new IOException("الإصدار المطلوب مش موجود في قائمة Mojang الرسمية");
            JSONObject info=getJson(metadataUrl).getJSONObject("downloads").getJSONObject("server");
            String jarUrl=info.getString("url");
            if(!jarUrl.startsWith("https://piston-data.mojang.com/"))
                throw new IOException("الرابط مش من سيرفرات Minecraft الرسمية");
            download(jarUrl,jar,info.getString("sha1"));
            logger.message("تم تنزيل Minecraft Java Vanilla الأصلي من Mojang (SHA-1 verified)");
            File eulaFile=new File(dir,"eula.txt");
            try(FileOutputStream out=new FileOutputStream(eulaFile)){out.write("eula=true\n".getBytes(StandardCharsets.UTF_8));}
        }else{
            JSONArray builds=getArray("https://fill.papermc.io/v3/projects/paper/versions/"+version+"/builds");
            JSONObject build=null;
            for(int i=0;i<builds.length();i++){JSONObject b=builds.getJSONObject(i);if("STABLE".equals(b.optString("channel"))){build=b;break;}}
            if(build==null)throw new IOException("الإصدار ده مالوش Paper مستقر");
            JSONObject archive=build.getJSONObject("downloads").getJSONObject("server:default");
            download(archive.getString("url"),jar,archive.optJSONObject("checksums")!=null?archive.getJSONObject("checksums").optString("sha256",null):null);
            if("geyser".equals(kind)){
                File plugins=new File(dir,"plugins");plugins.mkdirs();
                download("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot",new File(plugins,"Geyser-Spigot.jar"),null);
                download("https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot",new File(plugins,"floodgate-spigot.jar"),null);
            }
            File eulaFile=new File(dir,"eula.txt");
            try(FileOutputStream out=new FileOutputStream(eulaFile)){out.write("eula=true\n".getBytes(StandardCharsets.UTF_8));}
        }
        File props=new File(dir,"server.properties");
        if(!props.exists()){
            try(FileOutputStream out=new FileOutputStream(props)){
                String settings="motd=Hosted by Hostphone\nserver-port="+("bedrock".equals(kind)?"19132":"25565")+"\nonline-mode=true\n";
                out.write(settings.getBytes(StandardCharsets.UTF_8));
            }
        }
        new File(dir,".installed").createNewFile();
        logger.message("تم تثبيت السيرفر الحقيقي: "+kind+" "+version);
    }

    private void tuneFloodgate(File dir) throws IOException {
        File config=new File(dir,"plugins/Geyser-Spigot/config.yml");
        if(!config.exists())return; // Geyser creates the file on its first launch.
        byte[] raw=Files.readAllBytes(config.toPath());
        if(raw.length>2*1024*1024)throw new IOException("ملف إعدادات Geyser كبير");
        String old=new String(raw,StandardCharsets.UTF_8);
        // Valid when Geyser config includes auth-type under remote; leave other settings intact.
        String updated=old.replaceFirst("(?m)^(\\s*auth-type:\\s*)[^\\r\\n#]+", "$1floodgate");
        if(!updated.equals(old)){
            try(FileOutputStream out=new FileOutputStream(config)){out.write(updated.getBytes(StandardCharsets.UTF_8));}
            logger.message("تم ضبط Geyser auth-type على floodgate");
        } else logger.message("افحص إعداد auth-type في ملف Geyser بعد أول تشغيل");
    }

    Process start(String kind,String version,int ramMb,int cores) throws Exception {
        File jar=new File(profile(kind,version),"server.jar");
        if(!new File(profile(kind,version),".installed").exists()||!jar.exists())throw new IOException("لازم تثبت المحرك والسيرفر الأول");
        File java=new File(runtime(),"bin/java");
        if(!java.exists())throw new IOException("محرك Java مش موجود");
        if("geyser".equals(kind)) tuneFloodgate(profile(kind,version));
        ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);
        long total=mi.totalMem/1048576;long available=mi.availMem/1048576;
        // An actual JVM heap limit; never claim a 9 GiB allocation if the phone
        // cannot spare 9 GiB + native JVM / Android headroom at launch time.
        if(ramMb<512||ramMb>32768)throw new IOException("حجم الرام لازم يكون بين 512 و32768 ميجابايت");
        long reserve=Math.max(1024L,total/10);
        if(ramMb+reserve>total)throw new IOException("الجهاز فيه "+total+"MB RAM فقط. لطلب "+ramMb+"MB لازم تسيب "+reserve+"MB للنظام على الأقل");
        if(ramMb+reserve>available)throw new IOException("الرام المتاحة الآن "+available+"MB؛ تشغيل سيرفر بحجم "+ramMb+"MB يحتاج "+(ramMb+reserve)+"MB تقريبًا. اقفل البرامج أو قلل الرام");
        long heap=ramMb;
        int cpuCount=Math.max(1,Math.min(cores,Runtime.getRuntime().availableProcessors()));
        List<String> argv=new ArrayList<>();
        argv.add(java.getAbsolutePath());argv.add("-Xms256M");argv.add("-Xmx"+heap+"M");
        argv.add("-XX:ActiveProcessorCount="+cpuCount);
        argv.add("-Djava.io.tmpdir="+context.getCacheDir().getAbsolutePath());
        argv.add("-Djava.awt.headless=true");
        if("bedrock".equals(kind)){
            argv.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            argv.add("--add-opens=java.base/java.io=ALL-UNNAMED");
        }
        argv.add("-jar");argv.add(jar.getAbsolutePath());
        if(!"bedrock".equals(kind))argv.add("--nogui");
        ProcessBuilder builder=new ProcessBuilder(argv).directory(jar.getParentFile()).redirectErrorStream(true);
        java.util.Map<String,String> env=builder.environment();
        String libs=context.getApplicationInfo().nativeLibraryDir+":"+runtime().getAbsolutePath()+"/lib:"+runtime().getAbsolutePath()+"/lib/server:"+runtime().getAbsolutePath()+"/lib/jli";
        String previous=env.get("LD_LIBRARY_PATH");if(previous!=null)libs+=":"+previous;
        env.put("LD_LIBRARY_PATH",libs);env.put("JAVA_HOME",runtime().getAbsolutePath());
        env.put("HOME",base().getAbsolutePath());env.put("TMPDIR",context.getCacheDir().getAbsolutePath());
        logger.message("تشغيل سيرفر Minecraft بذاكرة JVM قصوى "+heap+"MB / "+cpuCount+" أنوية معالِج (الحد مش استهلاك إجباري)");
        return builder.start();
    }
}
