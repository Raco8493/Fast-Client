package com.raco.win10vm;

import android.app.Activity;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Native Android front end to real QEMU full-system PC emulation running in Termux.
 * No mock desktop, no bundled Windows image and no bundled QEMU executable.
 */
public final class MainActivity extends Activity {
    private static final int PICK_ISO = 199;
    private static final int REQUEST_TERMUX = 201;
    private static final String RUN_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX = "com.termux";
    private static final String PREFIX = "/data/data/com.termux/files/usr/bin/bash";
    private static final String ISO_PATH = "Download/Win10-VM/";

    private TextView status;
    private Spinner memory, cpus, disk;
    private String pendingAsset;
    private String[] pendingArgs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 22, 39));
        getWindow().setNavigationBarColor(Color.rgb(12, 22, 39));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 22, 39));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(body);
        setContentView(scroll);

        TextView heading = label("WINDOWS 10", 30, Color.WHITE);
        heading.setTypeface(null, 1);
        body.addView(heading);
        TextView subtitle = label("REAL X86 PC EMULATION  •  QEMU + TERMUX", 12, 0xff7bb8fa);
        body.addView(subtitle);
        gap(body, 12);
        body.addView(label("A full virtual PC, not a Windows-looking theme. Install your own genuine Windows ISO. The first boot can be very slow on ARM phones.", 14, 0xffc5d3e5));
        gap(body, 14);

        status = label("Step 1: Install Termux from F-Droid, open it once, and enable external commands.", 14, Color.WHITE);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(0xff213550);
        body.addView(status);
        gap(body, 16);

        addButton(body, "1  Open Termux", () -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(TERMUX);
            if (launch != null) startActivity(launch);
            else {
                update("Install Termux from https://f-droid.org/packages/com.termux/ and open it once.");
                openUrl("https://f-droid.org/packages/com.termux/");
            }
        });
        addButton(body, "Copy one-time Termux setup command", () -> {
            String command = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings && termux-setup-storage";
            ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux setup", command));
            update("Setup command copied. Paste it into Termux, run it, grant storage access and return to this app.");
        });
        addButton(body, "2  Install QEMU engine in Termux", () -> execute("install-termux.sh"));
        addButton(body, "3  Import genuine Windows 10 ISO", () -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("*/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(pick, PICK_ISO);
        });
        gap(body, 10);
        body.addView(label("Virtual PC settings", 18, Color.WHITE));
        memory = makeSpinner(body, "RAM (MB)", new String[]{"2048", "3072", "4096"}, 1);
        cpus = makeSpinner(body, "Emulated CPU cores", new String[]{"1", "2", "4"}, 1);
        disk = makeSpinner(body, "New virtual disk (GB)", new String[]{"32", "48", "64"}, 1);
        gap(body, 12);
        addButton(body, "4  Start real Windows VM", () -> execute("start-vm.sh", memory.getSelectedItem().toString(), cpus.getSelectedItem().toString(), disk.getSelectedItem().toString()));
        addButton(body, "5  Open VM screen (VNC)", () -> startActivity(new Intent(this, VncActivity.class)));
        addButton(body, "Stop the VM (force power-off)", () -> execute("stop-vm.sh"));
        gap(body, 15);
        body.addView(label("Windows networking: QEMU user-mode NAT + virtual Intel E1000 adapter. VM screen: localhost:5901. For security, the VNC port never listens on the public network.", 13, 0xffb3c8e1));
        gap(body, 8);
        body.addView(label("Not suitable for guaranteed Visual Studio / Roblox Studio performance. Use Remote Desktop to a real PC or cloud Windows VM for demanding development software.", 13, 0xffffc986));
    }

    private int dp(float d) { return (int)(d * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String text, int sp, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color); v.setLineSpacing(dp(3), 1.0f);
        return v;
    }
    private void gap(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private void addButton(LinearLayout body, String title, Runnable fn) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1463b2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.bottomMargin = dp(8);
        body.addView(b, lp); b.setOnClickListener(v -> fn.run());
    }
    private Spinner makeSpinner(LinearLayout body, String title, String[] items, int index) {
        body.addView(label(title, 13, 0xffb3c8e1));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(index);
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff7bb8fa));
        body.addView(spinner, new LinearLayout.LayoutParams(-1, dp(42)));
        return spinner;
    }
    private void update(String s) { status.setText(s); Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void openUrl(String url) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }

    private void execute(String asset, String... args) {
        if (getPackageManager().getLaunchIntentForPackage(TERMUX) == null) {
            update("Termux is not installed. Install it from F-Droid first."); return;
        }
        if (checkSelfPermission(RUN_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            pendingAsset = asset; pendingArgs = args;
            requestPermissions(new String[]{RUN_PERMISSION}, REQUEST_TERMUX);
            update("Allow 'Run commands in Termux' in this app's Android permissions, and set allow-external-apps=true in ~/.termux/termux.properties.");
            return;
        }
        try {
            byte[] bytes;
            try (InputStream source = getAssets().open(asset);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096]; int n;
                while ((n = source.read(chunk)) != -1) buffer.write(chunk, 0, n);
                bytes = buffer.toByteArray();
            }
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName(TERMUX, "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", PREFIX);
            String[] argv = new String[2 + args.length];
            argv[0] = "-s"; argv[1] = "--";
            System.arraycopy(args, 0, argv, 2, args.length);
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", argv);
            intent.putExtra("com.termux.RUN_COMMAND_STDIN", new String(bytes, StandardCharsets.UTF_8));
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", !asset.equals("start-vm.sh") && !asset.equals("install-termux.sh"));
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Windows VM " + asset);
            startService(intent);
            update(asset.equals("start-vm.sh") ? "QEMU start requested. Switch back here and tap 'Open VM screen'. First boot may take minutes." : "Command sent to Termux. Check its output for completion or errors.");
        } catch (Exception ex) {
            update("Cannot run Termux command: " + ex.getMessage() + ". Open Termux once and enable allow-external-apps=true.");
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == REQUEST_TERMUX) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && pendingAsset != null) {
                String asset = pendingAsset; String[] args = pendingArgs; pendingAsset = null; execute(asset, args);
            } else update("Termux permission denied. Grant 'Run commands in Termux environment' in Android App Info > Permissions.");
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ISO || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri source = data.getData();
        update("Copying Windows installer ISO to Download/Win10-VM/Windows10.iso ...");
        Executors.newSingleThreadExecutor().execute(() -> {
            Uri dest = null;
            try {
                android.content.ContentResolver resolver = getContentResolver();
                // Only remove an older ISO created by this app in our own Downloads subfolder.
                try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Downloads._ID},
                        MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                        new String[]{"Windows10.iso", ISO_PATH}, null)) {
                    if (cursor != null) while (cursor.moveToNext()) {
                        Uri old = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Long.toString(cursor.getLong(0)));
                        try { resolver.delete(old, null, null); } catch (Exception ignored) { }
                    }
                }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, "Windows10.iso");
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, ISO_PATH);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                dest = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (dest == null) throw new IllegalStateException("Cannot create ISO file in Downloads");
                long size = 0;
                try (InputStream in = resolver.openInputStream(source);
                     OutputStream out = resolver.openOutputStream(dest, "w")) {
                    if (in == null || out == null) throw new IllegalStateException("Cannot access selected ISO");
                    byte[] buf = new byte[1024 * 1024]; int n;
                    while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); size += n; }
                }
                if (size < 64L * 1024 * 1024) throw new IllegalArgumentException("File is too small to be a Windows installer ISO");
                cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(dest, cv, null, null);
                final long copied = size;
                runOnUiThread(() -> update("ISO imported (" + (copied / (1024 * 1024)) + " MB). In Termux run termux-setup-storage, grant access, then start the VM."));
            } catch (Exception e) {
                if (dest != null) try { getContentResolver().delete(dest, null, null); } catch (Exception ignored) { }
                String error = e.getMessage();
                runOnUiThread(() -> update("ISO import failed: " + error));
            }
        });
    }
}
