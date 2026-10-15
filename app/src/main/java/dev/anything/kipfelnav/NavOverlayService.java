package dev.anything.kipfelnav;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
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
    private static final long TRACKING_POLL_MS = 16; // スワイプで出したバーが出ているあいだ
    // 重ね描きの窓はナビバーのある辺に沿った帯にする(ガイド表示中は確認用の文字を出すので画面全体)
    private static final int OVERLAY_THICK_DP = 120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private KipfelOverlayView overlay;
    private WindowManager.LayoutParams lp;
    private SharedPreferences prefs;
    private boolean ticking;
    private KipfelOverlayView.Side appliedSide;
    private boolean appliedGuides;
    // 全画面のときに端からのスワイプを先に見つける帯(ナビバーの辺と、上端)
    private Catcher navCatcher;
    private Catcher topCatcher;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            pollNow();
            handler.postDelayed(this, nextPollMs());
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
            applyLayout();
        }
        if (Prefs.GUIDES.equals(key) || Prefs.EDGE_CATCH.equals(key) || Prefs.EDGE_CATCH_DP.equals(key)) {
            updateCatchers();
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
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("KipfelNavOverlay");
        setLayout();
        windowManager.addView(overlay, lp);
        navCatcher = new Catcher();
        topCatcher = new Catcher();

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        if (getSystemService(PowerManager.class).isInteractive()) {
            startTicking();
        }
    }

    /** 窓をナビバーのある辺に沿った帯にする。ガイド表示中は画面全体。 */
    private void setLayout() {
        appliedSide = overlay.side();
        appliedGuides = prefs.getBoolean(Prefs.GUIDES, false);
        int thick = (int) (OVERLAY_THICK_DP * getResources().getDisplayMetrics().density);
        int match = WindowManager.LayoutParams.MATCH_PARENT;
        if (appliedGuides) {
            lp.width = match;
            lp.height = match;
            lp.gravity = Gravity.TOP | Gravity.START;
            return;
        }
        switch (appliedSide) {
            case RIGHT:
                lp.width = thick;
                lp.height = match;
                lp.gravity = Gravity.TOP | Gravity.END;
                break;
            case LEFT:
                lp.width = thick;
                lp.height = match;
                lp.gravity = Gravity.TOP | Gravity.START;
                break;
            default:
                lp.width = match;
                lp.height = thick;
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                break;
        }
    }

    private void applyLayout() {
        if (overlay != null && lp != null) {
            setLayout();
            windowManager.updateViewLayout(overlay, lp);
        }
    }

    private long nextPollMs() {
        return overlay != null && overlay.trackingSwipeBar() ? TRACKING_POLL_MS : POLL_MS;
    }

    private void pollNow() {
        if (overlay == null) {
            return;
        }
        // スワイプで出したバーが出ているあいだは、手元に保存された一覧を捨てて最新を取りに行き、
        // バーが沈み始めたらすぐ追いかける
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && overlay.trackingSwipeBar()) {
            clearCache();
        }
        overlay.poll(windows());
        // ナビバーの辺が変わったら(カバー画面を横にしたときなど)、窓の置き場所も合わせる
        if (overlay.side() != appliedSide) {
            applyLayout();
        }
        updateCatchers();
    }

    /** 端の帯から、スワイプを見つけたと知らせが来た。すぐ先読みを始めて、細かく見る周期に切り替える。 */
    private void onEdgeSwipe() {
        if (overlay == null || !ticking) {
            return;
        }
        overlay.predictSwipe();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    /**
     * 端の帯は、ナビバーが隠れていてスワイプを待っているときだけタッチを受ける。
     * 上端の帯は、ステータスバーも隠れているときだけ(上からのスワイプでもナビバーが出てくるため)。
     */
    private void updateCatchers() {
        if (navCatcher == null) {
            return;
        }
        boolean on = ticking && prefs.getBoolean(Prefs.EDGE_CATCH, false) && overlay.waitingForSwipe();
        boolean guides = prefs.getBoolean(Prefs.GUIDES, false);
        int size = Math.max(1, Math.round(prefs.getInt(Prefs.EDGE_CATCH_DP, Prefs.DEFAULT_EDGE_CATCH_DP)
                * getResources().getDisplayMetrics().density));
        EdgeCatcher.Edge edge;
        switch (overlay.edgeSide()) {
            case RIGHT:
                edge = EdgeCatcher.Edge.RIGHT;
                break;
            case LEFT:
                edge = EdgeCatcher.Edge.LEFT;
                break;
            default:
                edge = EdgeCatcher.Edge.BOTTOM;
                break;
        }
        navCatcher.apply(edge, on, size, guides);
        topCatcher.apply(EdgeCatcher.Edge.TOP, on && overlay.statusBarHidden(), size, guides);
    }

    /** 端の帯1本分(窓とその置き場所)。使わないときもタッチを素通りさせて置いておく。 */
    private final class Catcher {
        final EdgeCatcher view;
        final WindowManager.LayoutParams lp;
        private String applied = "";

        Catcher() {
            view = new EdgeCatcher(NavOverlayService.this, NavOverlayService.this::onEdgeSwipe);
            lp = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.setFitInsetsTypes(0);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.gravity = Gravity.BOTTOM | Gravity.START;
            lp.setTitle("KipfelNavEdge");
            windowManager.addView(view, lp);
        }

        void apply(EdgeCatcher.Edge edge, boolean active, int size, boolean guides) {
            String state = edge + "/" + active + "/" + size + "/" + guides;
            if (state.equals(applied)) {
                return;
            }
            applied = state;
            view.setEdge(edge);
            int match = WindowManager.LayoutParams.MATCH_PARENT;
            switch (edge) {
                case TOP:
                    lp.width = match;
                    lp.height = size;
                    lp.gravity = Gravity.TOP | Gravity.START;
                    break;
                case RIGHT:
                    lp.width = size;
                    lp.height = match;
                    lp.gravity = Gravity.TOP | Gravity.END;
                    break;
                case LEFT:
                    lp.width = size;
                    lp.height = match;
                    lp.gravity = Gravity.TOP | Gravity.START;
                    break;
                default:
                    lp.width = match;
                    lp.height = size;
                    lp.gravity = Gravity.BOTTOM | Gravity.START;
                    break;
            }
            if (active) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            // ガイド表示中は、タッチを受けている帯を赤く見せる
            view.setBackgroundColor(active && guides ? Color.argb(110, 255, 40, 90) : Color.TRANSPARENT);
            windowManager.updateViewLayout(view, lp);
        }

        void remove() {
            windowManager.removeView(view);
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
        updateCatchers();
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
        if (navCatcher != null) {
            navCatcher.remove();
            topCatcher.remove();
            navCatcher = null;
            topCatcher = null;
        }
        super.onDestroy();
    }
}
