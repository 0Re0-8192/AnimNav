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
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.Collections;
import java.util.List;

/**
 * 画面全体に、タップを素通りさせる透明な層を1枚重ねて、ナビバーの上にキプフェルを描く。
 * アクセシビリティの重ね描き層は、ナビバーやキーボードより上に来る。
 * 画面の内容は読まない。
 */
public class NavOverlayService extends AccessibilityService {

    // 画面の状態(ナビバー・キーボード・ロック)を見る間隔。コマ送りは絵の側で時間から決める
    private static final long POLL_MS = 50;
    private static final long FAST_POLL_MS = 16;
    private static final int OVERLAY_HEIGHT_DP = 120;
    private static final int OVERLAY_HEIGHT_GUIDES_DP = 420; // ガイド表示中は確認用の文字を出すので高くする

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private KipfelOverlayView overlay;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            pollNow();
            // スワイプで出したバーが出ているあいだは、画面のコマごとに見る
            handler.postDelayed(this, overlay != null && overlay.wantsFastPoll() ? FAST_POLL_MS : POLL_MS);
        }
    };

    // 設定画面での変更をすぐ反映する(リスナーは強参照で持っておく)
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> {
        if (overlay == null) {
            return;
        }
        if (Prefs.IMAGES_VERSION.equals(key) || Prefs.FRAME_MS.equals(key)) {
            overlay.reloadImages();
        }
        if (Prefs.GUIDES.equals(key)) {
            applyHeight();
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

        prefs = Prefs.of(this);
        // 描くのはナビバーのあたりだけなので、画面の下端だけを覆う
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayHeight(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("KipfelNavOverlay");
        windowManager.addView(overlay, lp);

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        if (getSystemService(PowerManager.class).isInteractive()) {
            startTicking();
        }
    }

    private int overlayHeight() {
        int dp = prefs.getBoolean(Prefs.GUIDES, false) ? OVERLAY_HEIGHT_GUIDES_DP : OVERLAY_HEIGHT_DP;
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void applyHeight() {
        if (overlay != null && lp != null) {
            lp.height = overlayHeight();
            windowManager.updateViewLayout(overlay, lp);
        }
    }

    private void pollNow() {
        if (overlay != null) {
            overlay.poll(windows());
        }
    }

    /** 画面に出ているウィンドウの一覧(種類と位置を見るだけ)。取れなければ空。 */
    private List<AccessibilityWindowInfo> windows() {
        try {
            List<AccessibilityWindowInfo> w = getWindows();
            return w != null ? w : Collections.emptyList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private void startTicking() {
        if (!ticking) {
            ticking = true;
            handler.post(tick);
            if (overlay != null) {
                overlay.setRunning(true);
            }
        }
    }

    private void stopTicking() {
        ticking = false;
        handler.removeCallbacks(tick);
        if (overlay != null) {
            overlay.setRunning(false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Fold の開閉・回転:すぐ測り直す
        pollNow();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 中身は使わない。ウィンドウが出入りしたら、次の周期を待たずに測り直すだけ
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            pollNow();
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
