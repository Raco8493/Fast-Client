package io.github.realwinvm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** UI for a genuine QEMU VM running locally in Termux, not a fake desktop. */
public final class MainActivity extends Activity {
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String WINVM = "/data/data/com.termux/files/home/winvm/winvm.sh";
    private static final int BLUE = Color.rgb(20, 116, 214);
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Real Windows VM");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 14, 26));
        LinearLayout body = new LinearLayout(this);
        body.setPadding(dp(24), dp(32), dp(24), dp(24));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);

        TextView title = text("⊞  Real Windows VM", 27, Color.WHITE);
        title.setTypeface(null, 1);
        body.addView(title);
        body.addView(text("Windows x64 inside QEMU • Android viewer", 14, 0xFF9BB0CE));
        status = text("Needs Termux, installed QEMU and your own Windows ISO. Windows is not bundled.", 15, 0xFFF0CF9E);
        status.setPadding(0, dp(24), 0, dp(18));
        body.addView(status);

        button(body, "▶  Start installed Windows", () -> command("start"));
        button(body, "💿  Boot Windows installer (ISO)", () -> command("start", "--install"));
        button(body, "▣  OPEN LIVE WINDOWS SCREEN", () ->
            startActivity(new Intent(this, VncActivity.class)));
        button(body, "■  Shut down Windows safely", () -> command("stop"));
        button(body, "⌨  Open Termux to set up ISO", this::openTermux);
        button(body, "ⓘ  Setup instructions", this::help);
        body.addView(text("DISPLAY  127.0.0.1:5900 (VNC)\nNETWORK  QEMU user-mode NAT\nCPU  QEMU software x86_64 emulation (TCG)\nSOURCE  GitHub • RealWindowsAndroid", 13, 0xFF8EA4C6));

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERMISSION}, 720);
        }
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.bottomMargin = dp(12);
        parent.addView(button, p);
        button.setOnClickListener(v -> action.run());
    }

    private void command(String... args) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERMISSION}, 720);
            status.setText("Grant 'Run commands in Termux environment' permission in Android settings.");
            return;
        }
        Intent cmd = new Intent("com.termux.RUN_COMMAND");
        cmd.setClassName("com.termux", "com.termux.app.RunCommandService");
        cmd.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
        String[] argv = new String[args.length + 1];
        argv[0] = WINVM;
        System.arraycopy(args, 0, argv, 1, args.length);
        cmd.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", argv);
        cmd.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        cmd.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        cmd.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            startService(cmd);
            status.setText("Command sent to Termux: " + String.join(" ", args) + ". Open Termux to see any errors. After boot, open live screen.");
        } catch (Exception e) {
            status.setText("Cannot launch Termux command: " + e.getMessage());
            Toast.makeText(this, "Check Termux and its RUN_COMMAND permission", Toast.LENGTH_LONG).show();
        }
    }

    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch == null) {
            new AlertDialog.Builder(this).setMessage("Termux not installed. Use official F-Droid or GitHub Termux release (not an old Play Store copy).")
                .setPositiveButton("OK", null).show();
        } else { startActivity(launch); }
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Set up a REAL Windows virtual machine")
            .setMessage("1. Install official Termux from F-Droid.\n2. Extract source ZIP under your Termux home.\n3. Inside Termux run:\n" +
                "bash ~/RealWindowsAndroid/termux/install.sh\n" +
                "termux-setup-storage\n" +
                "~/winvm/winvm.sh init\n" +
                "~/winvm/winvm.sh import-iso ~/storage/downloads/Windows.iso\n" +
                "~/winvm/winvm.sh start --install\n\n" +
                "To enable this controller, add 'allow-external-apps=true' to ~/.termux/termux.properties, restart Termux, then grant this app the RUN_COMMAND permission in Android app settings.\n\n" +
                "A Windows ISO and license are NOT included. Guest programs use real Windows, but speed is limited by ARM-to-x86 emulation. Open the live screen after QEMU starts.")
            .setPositiveButton("OK", null).show();
    }
}
