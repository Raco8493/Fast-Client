package io.github.realwinvm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Localhost-only RFB 3.8 viewer supporting true-colour RAW encoding and
 * VNC pointer/keyboard events. Displays real QEMU VGA frames, not mock UI.
 */
public final class VncView extends View {
    public interface StatusCallback { void update(String text); }
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final StatusCallback callback;
    private final Object sendLock = new Object();
    private volatile Socket socket;
    private volatile DataOutputStream out;
    private volatile Bitmap framebuffer;
    private volatile Thread receiver;
    private volatile boolean active;
    private volatile long epoch;
    private volatile int guestWidth = 1024;
    private volatile int guestHeight = 768;

    public VncView(Context context, StatusCallback cb) {
        super(context); callback = cb;
        setFocusable(true);
        setBackgroundColor(Color.BLACK);
    }

    public synchronized void connect() {
        disconnect();
        active = true;
        final long generation = epoch;
        Thread thread = new Thread(() -> runClient(generation), "qemu-vnc-client");
        receiver = thread;
        thread.start();
    }

    public synchronized void disconnect() {
        active = false;
        epoch++;
        Socket old = socket;
        socket = null;
        out = null;
        if (old != null) try { old.close(); } catch (IOException ignored) {}
    }

    private void state(String s) { callback.update(s); }
    private void runClient(long generation) {
        Socket client = new Socket();
        try {
            state("Connecting to QEMU • 127.0.0.1:5900");
            client.connect(new InetSocketAddress("127.0.0.1", 5900), 4000);
            client.setTcpNoDelay(true);
            if (!active || epoch != generation) return;
            socket = client;
            DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream(), 65536));
            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(client.getOutputStream(), 65536));
            byte[] version = new byte[12];
            in.readFully(version);
            String signature = new String(version, StandardCharsets.US_ASCII);
            if (!signature.startsWith("RFB ")) throw new IOException("Invalid RFB handshake");
            // QEMU VNC supports the RFB 3.8 protocol.
            writer.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
            writer.flush();
            int options = in.readUnsignedByte();
            if (options == 0) {
                int len = in.readInt();
                if (len < 0 || len > 10000) throw new IOException("Bad auth error");
                byte[] reason = new byte[len]; in.readFully(reason);
                throw new IOException(new String(reason, StandardCharsets.UTF_8));
            }
            boolean noAuth = false;
            for (int i = 0; i < options; ++i) if (in.readUnsignedByte() == 1) noAuth = true;
            if (!noAuth) throw new IOException("QEMU VNC requires authentication unsupported by this local viewer");
            writer.writeByte(1); writer.flush();
            if (in.readInt() != 0) throw new IOException("VNC authorization denied");
            writer.writeByte(1); writer.flush(); // shared session
            int w = in.readUnsignedShort();
            int h = in.readUnsignedShort();
            if (w < 100 || h < 100 || w > 2560 || h > 1600) throw new IOException("Unexpected guest display size");
            byte[] serverPixelFormat = new byte[16]; in.readFully(serverPixelFormat);
            int nameLen = in.readInt();
            if (nameLen < 0 || nameLen > 2048) throw new IOException("Invalid server name");
            byte[] name = new byte[nameLen]; in.readFully(name);
            guestWidth = w; guestHeight = h;
            framebuffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            postInvalidate();

            // SetPixelFormat: 32bpp truecolor, little endian, RGB masks 255, shifts 16/8/0.
            writer.writeByte(0); writer.write(new byte[3]);
            writer.writeByte(32); writer.writeByte(24); writer.writeByte(0); writer.writeByte(1);
            writer.writeShort(255); writer.writeShort(255); writer.writeShort(255);
            writer.writeByte(16); writer.writeByte(8); writer.writeByte(0); writer.write(new byte[3]);
            // SetEncodings: RAW only (0). Guaranteed by RFB.
            writer.writeByte(2); writer.writeByte(0); writer.writeShort(1); writer.writeInt(0);
            writer.flush();
            synchronized (sendLock) { out = writer; requestFrame(false); }
            state("CONNECTED • Windows " + w + "×" + h + " • internet via QEMU NAT");
            while (active && generation == epoch) {
                int type = in.readUnsignedByte();
                if (type == 0) { // FramebufferUpdate
                    in.readUnsignedByte();
                    int rectangles = in.readUnsignedShort();
                    for (int n = 0; n < rectangles; ++n) {
                        int x = in.readUnsignedShort(), y = in.readUnsignedShort();
                        int rw = in.readUnsignedShort(), rh = in.readUnsignedShort();
                        int encoding = in.readInt();
                        if (encoding != 0) throw new IOException("Unsupported VNC encoding: " + encoding);
                        if (rw < 1 || rh < 1 || x + rw > w || y + rh > h || (long) rw * rh > 4_096_000) throw new IOException("Invalid VNC rectangle");
                        byte[] raw = new byte[rw * rh * 4];
                        in.readFully(raw);
                        int[] argb = new int[rw * rh];
                        for (int k = 0, p = 0; k < argb.length; ++k, p += 4) {
                            int b = raw[p] & 255, g = raw[p + 1] & 255, r = raw[p + 2] & 255;
                            argb[k] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                        Bitmap bmp = framebuffer;
                        if (bmp != null) bmp.setPixels(argb, 0, rw, x, y, rw, rh);
                    }
                    postInvalidate();
                    synchronized (sendLock) { requestFrame(true); }
                } else if (type == 2) { // Bell
                    // No sound synthesis, but guest keeps running.
                } else if (type == 3) { // ServerCutText
                    in.skipBytes(3); int len = in.readInt();
                    if (len < 0 || len > 4_194_304) throw new IOException("Clipboard too large");
                    for (int skipped = 0; skipped < len;) {
                        long chunk = in.skip(len - skipped);
                        if (chunk <= 0) throw new EOFException("Clipboard truncated");
                        skipped += (int)chunk;
                    }
                } else {
                    throw new IOException("Unknown VNC message type " + type);
                }
            }
        } catch (Exception ex) {
            if (active && epoch == generation) state("VNC disconnected: " + ex.getMessage() + " • Start VM in Termux, then Reconnect");
        } finally {
            if (epoch == generation) { active = false; out = null; socket = null; }
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void requestFrame(boolean incremental) throws IOException {
        DataOutputStream writer = out;
        if (writer == null) return;
        writer.writeByte(3); writer.writeByte(incremental ? 1 : 0);
        writer.writeShort(0); writer.writeShort(0);
        writer.writeShort(guestWidth); writer.writeShort(guestHeight);
        writer.flush();
    }
    private void key(int keySym, boolean down) throws IOException {
        if (!active || out == null) return;
        out.writeByte(4); out.writeByte(down ? 1 : 0); out.writeShort(0);
        out.writeInt(keySym);
    }
    public void tapKey(int code) {
        synchronized (sendLock) {
            try {
                if (out == null) return;
                key(code, true); key(code, false); out.flush();
            } catch (IOException e) { state("Keyboard error: " + e.getMessage()); }
        }
    }
    public void typeText(String text) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (cp == '\n') tapKey(0xFF0D);
            else if (cp >= 0x20 && cp <= 0x7e) tapKey(cp);
            else if (cp >= 0xa0 && cp <= 0x10ffff) tapKey(0x01000000 | cp);
            i += Character.charCount(cp);
        }
    }
    public void ctrlAltDel() {
        synchronized (sendLock) {
            try {
                if (out == null) return;
                key(0xFFE3, true); key(0xFFE9, true);
                key(0xFFFF, true); key(0xFFFF, false);
                key(0xFFE9, false); key(0xFFE3, false);
                out.flush();
            } catch (IOException e) { state("Keyboard error: " + e.getMessage()); }
        }
    }
    private void pointer(int x, int y, int buttons) {
        synchronized (sendLock) {
            try {
                if (!active || out == null) return;
                out.writeByte(5); out.writeByte(buttons);
                out.writeShort(Math.max(0, Math.min(guestWidth - 1, x)));
                out.writeShort(Math.max(0, Math.min(guestHeight - 1, y)));
                out.flush();
            } catch (IOException ignored) { }
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bitmap = framebuffer;
        if (bitmap == null) return;
        float scale = Math.min(getWidth() / (float) guestWidth, getHeight() / (float) guestHeight);
        float dw = guestWidth * scale, dh = guestHeight * scale;
        float left = (getWidth() - dw) / 2f, top = (getHeight() - dh) / 2f;
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        float scale = Math.min(getWidth() / (float) guestWidth, getHeight() / (float) guestHeight);
        if (scale <= 0) return true;
        float l = (getWidth() - guestWidth * scale) / 2f;
        float t = (getHeight() - guestHeight * scale) / 2f;
        int x = Math.round((event.getX() - l) / scale);
        int y = Math.round((event.getY() - t) / scale);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: pointer(x, y, 1); return true;
            case MotionEvent.ACTION_MOVE: pointer(x, y, 1); return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                pointer(x, y, 0); return true;
            default: return true;
        }
    }
}
