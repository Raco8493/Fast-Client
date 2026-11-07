package com.raco.win10vm;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Minimal local VNC/RFB client. Supports QEMU RFB 3.8, None security and RAW rectangles. */
public final class VncActivity extends Activity {
    private VncCanvas screen;
    private RfbClient client;
    private TextView message;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff0c1627);
        getWindow().setNavigationBarColor(0xff0c1627);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0c1627);
        setContentView(root);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        addBarButton(bar, "Esc", () -> key(0xff1b));
        addBarButton(bar, "Win", () -> key(0xffeb));
        addBarButton(bar, "Ctrl Alt Del", () -> {
            if (client == null) return;
            client.sendKey(0xffe3, true);
            client.sendKey(0xffe9, true);
            client.sendKey(0xffff, true);
            client.sendKey(0xffff, false);
            client.sendKey(0xffe9, false);
            client.sendKey(0xffe3, false);
        });
        addBarButton(bar, "Back", this::finish);
        screen = new VncCanvas(this);
        root.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(inputRow, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText textInput = new EditText(this);
        textInput.setSingleLine(true);
        textInput.setTextColor(Color.WHITE);
        textInput.setHintTextColor(0xffadbac8);
        textInput.setHint("Tap here to type in Windows");
        textInput.setTextSize(13);
        inputRow.addView(textInput, new LinearLayout.LayoutParams(0, -1, 1));
        textInput.addTextChangedListener(new TextWatcher() {
            boolean clearing;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (clearing || s.length() == 0) return;
                clearing = true;
                String typed = s.toString();
                for (int i = 0; i < typed.length(); i++) key(typed.charAt(i));
                s.clear();
                clearing = false;
            }
        });
        addBarButton(inputRow, "Enter", () -> key(0xff0d));
        addBarButton(inputRow, "⌫", () -> key(0xff08));
        message = new TextView(this);
        message.setTextColor(0xffbad3ee);
        message.setTextSize(12);
        message.setText("Connecting to local QEMU VNC at 127.0.0.1:5901…");
        message.setPadding(dp(12), dp(4), dp(12), dp(6));
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(30)));
        client = new RfbClient();
        new Thread(client, "vm-vnc-reader").start();
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private void addBarButton(LinearLayout bar, String title, Runnable click) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(title); b.setTextSize(11); b.setMinWidth(0);
        b.setTextColor(Color.WHITE); b.setPadding(dp(3), 0, dp(3), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1b4d7d));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        bar.addView(b, p); b.setOnClickListener(v -> click.run());
    }
    private void status(String value) { runOnUiThread(() -> { if (message != null) message.setText(value); }); }
    private void key(int sym) { if (client != null) { client.sendKey(sym, true); client.sendKey(sym, false); } }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        int key = keysym(code, event);
        if (key != 0 && client != null) { client.sendKey(key, true); return true; }
        return super.onKeyDown(code, event);
    }
    @Override public boolean onKeyUp(int code, KeyEvent event) {
        int key = keysym(code, event);
        if (key != 0 && client != null) { client.sendKey(key, false); return true; }
        return super.onKeyUp(code, event);
    }
    private int keysym(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER: return 0xff0d;
            case KeyEvent.KEYCODE_DEL: return 0xff08;
            case KeyEvent.KEYCODE_ESCAPE: return 0xff1b;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 0xffff;
            case KeyEvent.KEYCODE_TAB: return 0xff09;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0xff51;
            case KeyEvent.KEYCODE_DPAD_UP: return 0xff52;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0xff53;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0xff54;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 0xffe3;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 0xffe4;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 0xffe1;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 0xffe2;
            case KeyEvent.KEYCODE_ALT_LEFT: return 0xffe9;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 0xffea;
            default: int ch = event.getUnicodeChar(); return ch >= 32 && ch < 127 ? ch : 0;
        }
    }
    @Override protected void onDestroy() {
        if (client != null) client.close();
        super.onDestroy();
    }

    private final class VncCanvas extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF target = new RectF();
        private Bitmap bitmap;
        private final Object lock = new Object();
        VncCanvas(Context context) {
            super(context);
            setFocusableInTouchMode(true);
            textPaint.setColor(0xffcadff5); textPaint.setTextSize(dp(15));
        }
        void init(int width, int height) {
            synchronized(lock) {
                if (bitmap != null) bitmap.recycle();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.BLACK);
            }
            postInvalidate();
        }
        void raw(int x, int y, int w, int h, int[] argb) {
            synchronized(lock) { if (bitmap != null) bitmap.setPixels(argb, 0, w, x, y, w, h); }
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            synchronized(lock) {
                if (bitmap == null) {
                    canvas.drawText("Waiting for QEMU virtual display…", dp(20), dp(42), textPaint);
                    return;
                }
                float s = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
                float w = bitmap.getWidth()*s, h = bitmap.getHeight()*s;
                target.set((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2);
                canvas.drawBitmap(bitmap, null, target, paint);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (client == null || bitmap == null) return true;
            float s = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
            float marginX = (getWidth()-bitmap.getWidth()*s)/2;
            float marginY = (getHeight()-bitmap.getHeight()*s)/2;
            int x = Math.max(0, Math.min(bitmap.getWidth()-1, (int)((event.getX()-marginX)/s)));
            int y = Math.max(0, Math.min(bitmap.getHeight()-1, (int)((event.getY()-marginY)/s)));
            int action = event.getActionMasked();
            switch(action) {
                case MotionEvent.ACTION_DOWN: client.sendPointer(1, x, y); return true;
                case MotionEvent.ACTION_MOVE: client.sendPointer(1, x, y); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: client.sendPointer(0, x, y); return true;
                default: return true;
            }
        }
    }

    private final class RfbClient implements Runnable {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private volatile boolean running = true;
        private int width, height;
        @Override public void run() {
            try {
                for (int retry = 0; retry < 90 && running; retry++) {
                    try {
                        socket = new Socket(); socket.connect(new InetSocketAddress("127.0.0.1", 5901), 1800);
                        break;
                    } catch (IOException e) {
                        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
                        if (retry % 10 == 0) status("Waiting for QEMU to start… attempt " + (retry+1) + "/90");
                        Thread.sleep(1200);
                    }
                }
                if (!running || socket == null || !socket.isConnected()) throw new IOException("VNC unavailable. Start QEMU in Termux first.");
                socket.setTcpNoDelay(true);
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 16384));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 16384));
                handshake();
                status("Connected to Windows VM  •  " + width + "×" + height + "  •  touch = mouse");
                requestFrame(false);
                while (running) {
                    int type = in.readUnsignedByte();
                    if (type == 0) readFramebufferUpdate();
                    else if (type == 2) { /* Bell */ }
                    else if (type == 3) {
                        skip(3); int len = in.readInt();
                        if (len < 0 || len > 1024 * 1024) throw new IOException("VNC clipboard too long");
                        skip(len);
                    } else if (type == 1) {
                        in.readUnsignedByte(); in.readUnsignedShort(); int n = in.readUnsignedShort(); skip(n * 6);
                    } else throw new IOException("Unsupported VNC message: " + type);
                }
            } catch (Exception ex) {
                if (running) status("VNC disconnected: " + ex.getMessage() + "  |  Check Termux logs and VM setup.");
            } finally { close(); }
        }
        private void handshake() throws IOException {
            byte[] version = new byte[12]; in.readFully(version);
            String versionStr = new String(version, java.nio.charset.StandardCharsets.US_ASCII);
            if (!versionStr.startsWith("RFB 003.")) throw new IOException("Invalid VNC protocol: " + versionStr);
            out.write("RFB 003.008\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); out.flush();
            int types = in.readUnsignedByte();
            if (types == 0) { int len=in.readInt(); if (len>0 && len<1000) skip(len); throw new IOException("VNC server rejected connection"); }
            boolean none = false;
            for (int i=0;i<types;i++) if (in.readUnsignedByte()==1) none=true;
            if (!none) throw new IOException("VNC server requires unsupported authentication");
            out.writeByte(1); out.flush();
            int result=in.readInt(); if(result != 0) throw new IOException("VNC authentication failed ("+result+")");
            out.writeByte(1); out.flush(); // shared client
            width=in.readUnsignedShort(); height=in.readUnsignedShort();
            if(width<1 || height<1 || width>4096 || height>4096) throw new IOException("Unsupported VM display size");
            skip(16); int namelen=in.readInt(); if(namelen<0 || namelen>65536) throw new IOException("Invalid VNC name length");
            skip(namelen);
            // Request truecolor 32-bit little-endian B,G,R,X pixels.
            synchronized(this) {
                out.writeByte(0); out.write(new byte[3]);
                out.writeByte(32); out.writeByte(24); out.writeByte(0); out.writeByte(1);
                out.writeShort(255); out.writeShort(255); out.writeShort(255);
                out.writeByte(16); out.writeByte(8); out.writeByte(0); out.write(new byte[3]);
                // Ask for simple RAW rectangle encoding, zero means uncompressed.
                out.writeByte(2); out.writeByte(0); out.writeShort(2); out.writeInt(0); out.writeInt(-223);
                out.flush();
            }
            screen.init(width, height);
        }
        private void requestFrame(boolean incremental) throws IOException {
            synchronized(this) {
                out.writeByte(3); out.writeByte(incremental ? 1 : 0);
                out.writeShort(0); out.writeShort(0); out.writeShort(width); out.writeShort(height);
                out.flush();
            }
        }
        private void readFramebufferUpdate() throws IOException {
            in.readUnsignedByte(); int rects = in.readUnsignedShort();
            boolean resized = false;
            for (int i = 0; i < rects; i++) {
                int x=in.readUnsignedShort(), y=in.readUnsignedShort();
                int w=in.readUnsignedShort(), h=in.readUnsignedShort(), encoding=in.readInt();
                if (encoding == -223) { // DesktopSize pseudo-encoding, not normally sent because unrequested
                    if (w>0 && h>0 && w<=4096 && h<=4096) { width=w; height=h; screen.init(w,h); resized = true; }
                    continue;
                }
                if (encoding != 0) throw new IOException("VNC encoding not supported: " + encoding);
                if (w<1 || h<1 || x+w>width || y+h>height) throw new IOException("Invalid VNC rectangle");
                int[] pixels = new int[w*h];
                byte[] row = new byte[w*4];
                for (int yy=0; yy<h; yy++) {
                    in.readFully(row);
                    for (int xx=0; xx<w; xx++) {
                        int o=xx*4;
                        pixels[yy*w+xx]=0xff000000|((row[o+2]&255)<<16)|((row[o+1]&255)<<8)|(row[o]&255);
                    }
                }
                screen.raw(x,y,w,h,pixels);
            }
            screen.postInvalidate();
            requestFrame(!resized);
        }
        void sendKey(int sym, boolean down) {
            synchronized(this) {
                if(out==null || !running) return;
                try {
                    out.writeByte(4); out.writeByte(down ? 1 : 0); out.writeShort(0); out.writeInt(sym); out.flush();
                } catch(IOException e) { close(); }
            }
        }
        void sendPointer(int mask,int x,int y) {
            synchronized(this) {
                if(out==null || !running) return;
                try {
                    out.writeByte(5); out.writeByte(mask); out.writeShort(x); out.writeShort(y); out.flush();
                } catch(IOException e) { close(); }
            }
        }
        private void skip(int length) throws IOException {
            while(length>0) { int n=in.skipBytes(length); if(n<=0) throw new EOFException("VNC payload truncated"); length-=n; }
        }
        void close() {
            running=false;
            if(socket!=null) try { socket.close(); } catch(IOException ignored) { }
        }
    }
}
