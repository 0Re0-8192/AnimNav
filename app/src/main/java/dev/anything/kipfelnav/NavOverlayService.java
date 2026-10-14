package dev.anything.kipfelnav;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 画面全体に、タップを素通りさせる透明な層を1枚重ねて、ナビバーの上にキプフェルを描く。
 * アクセシビリティの重ね描き層は、ナビバーやキーボードより上に来る。
 * 画面の内容は読まない。
 */
public class NavOverlayService extends AccessibilityService {

    private static final long FRAME_MS = 150; // 元のカーソルと同じ速さ(9/60 秒)
    private static final int OVERLAY_HEIGHT_DP = 120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private KipfelOverlayView overlay;
    private SharedPreferences prefs;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (overlay != null) {
                overlay.tick();
            }
            handler.postDelayed(this, FRAME_MS);
        }
    };

    // 設定画面での変更をすぐ反映する(リスナーは強参照で持っておく)
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> {
        if (overlay == null) {
            return;
        }
        if (Prefs.SHEET_VERSION.equals(key)) {
            overlay.reloadSheet();
        }
        overlay.invalidate();
    };

    // 画面が消えているあいだはアニメーションを止める
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopTicking();
            } else {
                startTicking();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        overlay = new KipfelOverlayView(this);

        // 描くのはナビバーのあたりだけなので、画面の下端だけを覆う
        int height = (int) (OVERLAY_HEIGHT_DP * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("KipfelNavOverlay");
        windowManager.addView(overlay, lp);

        prefs = Prefs.of(this);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        if (getSystemService(PowerManager.class).isInteractive()) {
            startTicking();
        }
    }

    private void startTicking() {
        if (!ticking) {
            ticking = true;
            handler.post(tick);
        }
    }

    private void stopTicking() {
        ticking = false;
        handler.removeCallbacks(tick);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Fold の開閉・回転:すぐ測り直す
        if (overlay != null) {
            overlay.refresh();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 画面の内容は使わない。画面が切り替わったら、次のコマを待たずに測り直すだけ
        if (overlay != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            overlay.refresh();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        stopTicking();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
            unregisterReceiver(screenReceiver);
        }
        if (overlay != null && windowManager != null) {
            windowManager.removeView(overlay);
            overlay = null;
        }
        super.onDestroy();
    }
}
