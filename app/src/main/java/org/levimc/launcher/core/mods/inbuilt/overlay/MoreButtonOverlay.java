package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.widget.ImageButton;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.MoreButtonsSvgBridge;
import org.levimc.launcher.core.mods.inbuilt.manager.MoreButtonsManager;
import org.levimc.launcher.core.mods.inbuilt.model.MoreButtonConfig;
import org.levimc.launcher.core.mods.inbuilt.nativemod.MoreButtonsMod;
import org.levimc.pojavcontrols.KeyMapper;

public final class MoreButtonOverlay extends BaseOverlayButton {
    private final String buttonId;
    private MoreButtonConfig config;
    private boolean active;
    private boolean keyDown;
    private Bitmap normalBitmap;
    private Bitmap pressedBitmap;

    public MoreButtonOverlay(Activity activity, MoreButtonConfig config) {
        super(activity);
        this.buttonId = config.id;
        this.config = config.copy();
    }

    public String getButtonId() {
        return buttonId;
    }

    @Override
    protected String getModId() {
        return config.overlayKey();
    }

    @Override
    protected int getIconResource() {
        return 0;
    }

    @Override
    protected void configureOverlayView(android.view.View view) {
        rebuildBitmaps();
        // The custom SVG is composited with its red base; never tint the whole bitmap.
        if (view instanceof ImageButton) {
            ImageButton button = (ImageButton) view;
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setImageTintList(null);
            button.clearColorFilter();
            button.setPadding(0, 0, 0, 0);
        }
        updateVisual();
        view.setContentDescription(config.name);
    }

    @Override
    protected void onButtonPressStart() {
        if (!MoreButtonConfig.isSupportedMapping(config.keyCode)) return;

        if (config.toggle) {
            active = !active;
            sendMappedInput(config.keyCode, active);
            updateVisual();
            return;
        }

        if (keyDown) return;
        keyDown = true;
        active = true;
        sendMappedInput(config.keyCode, true);
        updateVisual();
    }

    @Override
    protected void onButtonPressEnd() {
        if (config.toggle || !keyDown) return;
        sendMappedInput(config.keyCode, false);
        keyDown = false;
        active = false;
        updateVisual();
    }

    @Override
    protected void onButtonClick() {
    }

    @Override
    public void hide() {
        if ((keyDown || active) && MoreButtonConfig.isSupportedMapping(config.keyCode)) {
            sendMappedInput(config.keyCode, false);
        }
        keyDown = false;
        active = false;
        super.hide();
        recycleBitmaps();
    }

    @Override
    public void applyConfigurationChanges() {
        MoreButtonConfig latest = MoreButtonsManager.getInstance(activity).getButton(buttonId);
        if (latest != null) {
            if ((keyDown || active)
                    && (latest.keyCode != config.keyCode || latest.toggle != config.toggle)
                    && MoreButtonConfig.isSupportedMapping(config.keyCode)) {
                sendMappedInput(config.keyCode, false);
                keyDown = false;
                active = false;
            }
            config = latest.copy();
            rebuildBitmaps();
            updateVisual();
        }
        super.applyConfigurationChanges();
    }

    private void sendMappedInput(int mappingCode, boolean down) {
        int bedrockCode = KeyMapper.toBedrock(mappingCode);
        if (bedrockCode <= 0) return;
        if (MoreButtonsMod.sendKey(bedrockCode, down)) return;
        int androidCode = KeyMapper.toAndroidKeyCode(mappingCode);
        if (androidCode == KeyEvent.KEYCODE_UNKNOWN) return;
        if (down) sendKeyDown(androidCode); else sendKeyUp(androidCode);
    }

    private void updateVisual() {
        if (!(overlayView instanceof ImageButton)) return;
        ImageButton button = (ImageButton) overlayView;
        button.setImageTintList(null);
        button.clearColorFilter();
        button.setImageBitmap(active ? pressedBitmap : normalBitmap);
        button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
        button.setAlpha(getButtonOpacity());
        button.setContentDescription(config.name);
    }

    private void rebuildBitmaps() {
        recycleBitmaps();
        normalBitmap = createButtonBitmap(activity, config, false);
        pressedBitmap = createButtonBitmap(activity, config, true);
    }

    private void recycleBitmaps() {
        if (normalBitmap != null && !normalBitmap.isRecycled()) normalBitmap.recycle();
        if (pressedBitmap != null && !pressedBitmap.isRecycled()) pressedBitmap.recycle();
        normalBitmap = null;
        pressedBitmap = null;
    }

    public static Bitmap createButtonBitmap(Activity activity, MoreButtonConfig config, boolean pressed) {
        Bitmap base = BitmapFactory.decodeResource(activity.getResources(),
                pressed ? R.drawable.more_button_base_pressed : R.drawable.more_button_base_normal);
        if (base == null) return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        // Preserve the exact original button outline and alpha (including antialiasing).
        // Only recolor its background: the icon is drawn separately in solid black.
        Bitmap output = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint rubyBackground = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        rubyBackground.setColorFilter(new PorterDuffColorFilter(
                pressed ? Color.rgb(166, 25, 42) : Color.rgb(220, 38, 55),
                PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(base, 0, 0, rubyBackground);
        base.recycle();

        boolean hasPressedSvg = config.pressedSvg != null && !config.pressedSvg.trim().isEmpty();
        String svg = pressed && hasPressedSvg ? config.pressedSvg : config.normalSvg;
        if (svg == null || svg.trim().isEmpty()) return output;

        // Keep the original SVG geometry (sword, sprint, etc.) black in both states.
        svg = MoreButtonsManager.recolorSvg(svg, "#000000");

        int iconSize = Math.max(96, Math.min(410, Math.round(512f * config.iconScale / 100f)));
        byte[] png = MoreButtonsSvgBridge.renderSvg(svg, iconSize, iconSize);
        if (png == null || png.length == 0) return output;
        Bitmap icon = BitmapFactory.decodeByteArray(png, 0, png.length);
        if (icon == null) return output;

        int left = (512 - iconSize) / 2 + config.iconOffsetX;
        int top = (512 - iconSize) / 2 + config.iconOffsetY + (pressed ? 8 : 0);
        canvas.drawBitmap(icon, null, new Rect(left, top, left + iconSize, top + iconSize), null);
        icon.recycle();
        return output;
    }
}
