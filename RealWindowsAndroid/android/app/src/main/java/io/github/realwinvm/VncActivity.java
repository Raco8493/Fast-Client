package io.github.realwinvm;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-app RFB/VNC viewer. QEMU must already be listening on localhost port 5900. */
public final class VncActivity extends Activity {
    private VncView viewer;
    private TextView status;
    private EditText keyboard;
    private boolean clearGuard;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xFF0D1B2C);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(52)));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setText("Windows VNC: connecting...");
        toolbar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        control(toolbar, "Reconnect", () -> viewer.connect());
        control(toolbar, "Back", this::finish);

        viewer = new VncView(this, (message) -> runOnUiThread(() -> status.setText(message)));
        root.addView(viewer, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(0xFF13233A);
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(54)));
        control(controls, "⌨ Keyboard", () -> {
            keyboard.requestFocus();
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT);
        });
        control(controls, "Enter", () -> viewer.tapKey(0xFF0D));
        control(controls, "Esc", () -> viewer.tapKey(0xFF1B));
        control(controls, "Tab", () -> viewer.tapKey(0xFF09));
        control(controls, "⌫", () -> viewer.tapKey(0xFF08));
        control(controls, "Ctrl Alt Del", () -> viewer.ctrlAltDel());

        keyboard = new EditText(this);
        keyboard.setTextSize(14);
        keyboard.setSingleLine(true);
        keyboard.setTextColor(0xFFAFCEE9);
        keyboard.setHintTextColor(0xFF6483A4);
        keyboard.setHint("Tap ⌨ to type");
        keyboard.setBackgroundColor(0xFF0D1B2C);
        root.addView(keyboard, new LinearLayout.LayoutParams(-1, dp(42)));
        keyboard.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (clearGuard || s.length() == 0) return;
                String typed = s.toString();
                clearGuard = true;
                keyboard.setText("");
                clearGuard = false;
                viewer.typeText(typed);
            }
        });
        viewer.connect();
    }

    private void control(LinearLayout root, String label, Runnable run) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setMinWidth(0);
        b.setPadding(dp(5), 0, dp(5), 0);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFF24466B));
        root.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
        b.setOnClickListener(v -> run.run());
    }
    private int dp(int px) { return Math.round(getResources().getDisplayMetrics().density * px); }
    @Override protected void onDestroy() { viewer.disconnect(); super.onDestroy(); }
}
