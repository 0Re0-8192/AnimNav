package dev.anything.kipfelnav;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * 確認用の図形を描く。
 * - ナビバーの位置:水色の帯、ボタン位置の目安のオレンジの四角3つ、帯の上を動く点
 * - キーボードが出る高さ:マゼンタの四角(キーボードより上に描けているか)
 * - 画面上部:画面サイズ・ナビバーの高さ・キーボードの状態など
 */
class DebugOverlayView extends View {

    private final WindowManager windowManager;
    private final float dp;
    private final Paint strip = new Paint();
    private final Paint button = new Paint();
    private final Paint dot = new Paint();
    private final Paint keyboardBox = new Paint();
    private final Paint panel = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    private WindowInsets lastInsets;
    private int screenChanges;
    private long startMs = System.currentTimeMillis();

    DebugOverlayView(Context context) {
        super(context);
        windowManager = context.getSystemService(WindowManager.class);
        dp = getResources().getDisplayMetrics().density;
        strip.setColor(Color.argb(110, 0, 200, 255));
        button.setColor(Color.argb(200, 255, 140, 0));
        dot.setColor(Color.argb(230, 255, 255, 255));
        keyboardBox.setColor(Color.argb(200, 255, 0, 200));
        panel.setColor(Color.argb(170, 0, 0, 0));
        text.setColor(Color.WHITE);
        text.setTextSize(12 * dp);
        label.setColor(Color.BLACK);
        label.setTextSize(14 * dp);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
    }

    void onScreenChanged() {
        screenChanges++;
        requestApplyInsets();
        invalidate();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        lastInsets = insets;
        invalidate();
        return insets;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();

        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        WindowInsets mInsets = metrics.getWindowInsets();
        Insets navFromMetrics = mInsets.getInsets(WindowInsets.Type.navigationBars());
        Insets navFromView = lastInsets != null
                ? lastInsets.getInsets(WindowInsets.Type.navigationBars()) : Insets.NONE;
        boolean navVisible = lastInsets != null && lastInsets.isVisible(WindowInsets.Type.navigationBars());
        boolean imeVisibleView = lastInsets != null && lastInsets.isVisible(WindowInsets.Type.ime());
        Insets imeView = lastInsets != null ? lastInsets.getInsets(WindowInsets.Type.ime()) : Insets.NONE;
        boolean imeVisibleMetrics = mInsets.isVisible(WindowInsets.Type.ime());

        // ナビバーの高さ:取れなければ 48dp と仮定
        int navH = navFromView.bottom > 0 ? navFromView.bottom
                : navFromMetrics.bottom > 0 ? navFromMetrics.bottom : (int) (48 * dp);
        int top = h - navH;

        // 水色の帯
        c.drawRect(0, top, w, h, strip);

        // ボタン位置の目安(画面の幅を3等分した中心)
        float size = Math.min(navH * 0.8f, 40 * dp);
        float cy = top + navH / 2f;
        String[] names = {"1", "2", "3"};
        for (int i = 0; i < 3; i++) {
            float cx = w * (2 * i + 1) / 6f;
            c.drawRect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, button);
            c.drawText(names[i], cx, cy + label.getTextSize() / 3, label);
        }

        // 帯の上を動く点(アニメーションの確認)
        long t = System.currentTimeMillis() - startMs;
        float x = (t % 3000) / 3000f * w;
        c.drawCircle(x, cy, 5 * dp, dot);

        // キーボードが出る高さあたり(画面の下から 30%)にマゼンタの四角
        float kbCx = w / 2f;
        float kbCy = h * 0.70f;
        float kb = 56 * dp;
        c.drawRect(kbCx - kb / 2, kbCy - kb / 2, kbCx + kb / 2, kbCy + kb / 2, keyboardBox);
        c.drawText("KB", kbCx, kbCy + label.getTextSize() / 3, label);

        // 数値
        String rot = new String[]{"0", "90", "180", "270"}[getDisplay() != null ? rotationIndex() : 0];
        String[] lines = {
                "screen " + bounds.width() + "x" + bounds.height() + "px  "
                        + Math.round(bounds.width() / dp) + "x" + Math.round(bounds.height() / dp) + "dp  "
                        + "density " + dp + "  rot " + rot,
                "view " + w + "x" + h + "  screen changes " + screenChanges,
                "nav(view) L" + navFromView.left + " T" + navFromView.top + " R" + navFromView.right
                        + " B" + navFromView.bottom + "  visible " + navVisible,
                "nav(metrics) L" + navFromMetrics.left + " R" + navFromMetrics.right
                        + " B" + navFromMetrics.bottom,
                "ime(view) visible " + imeVisibleView + " B" + imeView.bottom
                        + "  ime(metrics) visible " + imeVisibleMetrics,
        };
        float lineH = text.getTextSize() * 1.35f;
        float px = 12 * dp;
        float py = h * 0.12f;
        float pw = 0;
        for (String s : lines) {
            pw = Math.max(pw, text.measureText(s));
        }
        c.drawRect(px - 6 * dp, py - lineH, px + pw + 6 * dp, py + lineH * (lines.length - 1) + 8 * dp, panel);
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], px, py + i * lineH, text);
        }

        postInvalidateOnAnimation();
    }

    private int rotationIndex() {
        int r = getDisplay().getRotation();
        switch (r) {
            case Surface.ROTATION_90:
                return 1;
            case Surface.ROTATION_180:
                return 2;
            case Surface.ROTATION_270:
                return 3;
            default:
                return 0;
        }
    }
}
