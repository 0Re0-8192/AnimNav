package dev.anything.kipfelnav;

import android.accessibilityservice.AccessibilityService;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 画面全体に、タップを素通りさせる透明な層を1枚重ねる。
 * アクセシビリティの重ね描き層は、ナビバーやキーボードより上に来る(AOSP の重なり順)。
 * それが Samsung でも同じかを確かめるためのテスト。
 */
public class NavOverlayService extends AccessibilityService {

    private WindowManager windowManager;
    private DebugOverlayView overlay;

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        overlay = new DebugOverlayView(this);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("KipfelNavOverlay");
        windowManager.addView(overlay, lp);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Fold の開閉・回転で画面の大きさが変わったら測り直す
        if (overlay != null) {
            overlay.onScreenChanged();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 画面の内容は使わない
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (overlay != null && windowManager != null) {
            windowManager.removeView(overlay);
            overlay = null;
        }
        super.onDestroy();
    }
}
