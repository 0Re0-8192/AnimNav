package dev.anything.kipfelnav;

import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.EnumMap;
import java.util.Map;

/**
 * ナビバーの3つのボタンの真上に、ボタンごとの絵を描く。
 * このビューは画面の下端に置かれる(高さはナビバーより少し大きいくらい)。タップは素通り。
 * - 下にナビバーがないとき(ロック画面・全画面・ナビバーが横にあるとき)は描かない。
 *   消えるとき・出てくるときは、純正のナビバーのように下へ沈みながらフェードする。
 * - ロック中(パスワード入力画面など)は、戻るだけを描く。
 * - キーボード表示中は、戻るの位置に「キーボードを閉じる」の絵を描く。
 */
class KipfelOverlayView extends View {

    private static final long FADE_MS = 220;

    private final WindowManager windowManager;
    private final KeyguardManager keyguardManager;
    private final SharedPreferences prefs;
    private final float dp;
    private final Rect dst = new Rect();
    private final Paint guide = new Paint();
    private final Paint guideStrip = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint();
    private final Map<Slot, SlotImage> images = new EnumMap<>(Slot.class);

    private boolean running;

    // 直近に測った画面の状態
    private int navHeight;
    private int lastNavHeight;
    private boolean imeVisible;
    private boolean locked;
    private String screen = Prefs.COVER;

    // 出てくる・消えるアニメーション(0 = 消えている、1 = 出ている)
    private float shown;
    private ValueAnimator fade;

    KipfelOverlayView(Context context) {
        super(context);
        windowManager = context.getSystemService(WindowManager.class);
        keyguardManager = context.getSystemService(KeyguardManager.class);
        prefs = Prefs.of(context);
        dp = getResources().getDisplayMetrics().density;
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(2 * dp);
        guide.setColor(Color.argb(230, 255, 140, 0));
        guideStrip.setColor(Color.argb(70, 0, 200, 255));
        text.setColor(Color.WHITE);
        text.setTextSize(12 * dp);
        panel.setColor(Color.argb(170, 0, 0, 0));
        reloadImages();
        measureScreen();
        shown = navHeight > 0 ? 1f : 0f;
        lastNavHeight = navHeight;
    }

    // ---- 画像

    void reloadImages() {
        for (SlotImage img : images.values()) {
            img.stop();
            if (img instanceof SlotImage.Animated) {
                ((SlotImage.Animated) img).drawable.setCallback(null);
            }
        }
        images.clear();
        for (Slot s : Slot.values()) {
            SlotImage img = ImageStore.load(getContext(), s);
            if (img == null) {
                continue;
            }
            if (img instanceof SlotImage.Animated) {
                ((SlotImage.Animated) img).drawable.setCallback(this);
            }
            if (running) {
                img.start();
            }
            images.put(s, img);
        }
        invalidate();
    }

    void setRunning(boolean run) {
        running = run;
        for (SlotImage img : images.values()) {
            if (run) {
                img.start();
            } else {
                img.stop();
            }
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        for (SlotImage img : images.values()) {
            if (img instanceof SlotImage.Animated && ((SlotImage.Animated) img).owns(who)) {
                return true;
            }
        }
        return super.verifyDrawable(who);
    }

    // ---- 画面の状態

    /** 50ms ごとに呼ばれる。画面の状態を測り直して描き直す。 */
    void poll() {
        boolean wasShown = navHeight > 0;
        measureScreen();
        boolean isShown = navHeight > 0;
        if (isShown) {
            lastNavHeight = navHeight;
        }
        if (wasShown != isShown) {
            animateShown(isShown);
        }
        invalidate();
    }

    private void measureScreen() {
        WindowMetrics m = windowManager.getCurrentWindowMetrics();
        Rect b = m.getBounds();
        WindowInsets in = m.getWindowInsets();
        Insets nav = in.getInsets(WindowInsets.Type.navigationBars());
        // 下にナビバーがあるときだけ描く。横にあるとき(left/right)は描かない
        navHeight = nav.bottom;
        imeVisible = in.isVisible(WindowInsets.Type.ime());
        locked = keyguardManager.isKeyguardLocked();
        screen = Prefs.screenFor(b.width(), b.height(), dp);
    }

    private void animateShown(boolean show) {
        if (fade != null) {
            fade.cancel();
        }
        float target = show ? 1f : 0f;
        fade = ValueAnimator.ofFloat(shown, target);
        fade.setDuration((long) (FADE_MS * Math.abs(target - shown)));
        fade.setInterpolator(show ? new DecelerateInterpolator() : new AccelerateInterpolator());
        fade.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    // ---- 描画

    @Override
    protected void onDraw(Canvas c) {
        if (shown <= 0f || lastNavHeight <= 0) {
            return;
        }
        int navH = lastNavHeight;
        int w = getWidth();
        int h = getHeight();
        float center = Prefs.get(prefs, screen, Prefs.CENTER);
        float spacing = Prefs.get(prefs, screen, Prefs.SPACING);
        float sizeFrac = Prefs.get(prefs, screen, Prefs.SIZE);
        float offsetY = Prefs.get(prefs, screen, Prefs.OFFSET_Y) * dp;
        boolean guides = prefs.getBoolean(Prefs.GUIDES, false);
        boolean backOnRight = prefs.getBoolean(Prefs.BACK_ON_RIGHT, false);
        boolean snap = prefs.getBoolean(Prefs.PIXEL_SNAP, true);

        // 消えるときは下に沈む
        int alpha = Math.round(255 * shown);
        c.save();
        c.translate(0, (1f - shown) * navH);

        if (guides) {
            guideStrip.setAlpha(Math.round(70 * shown));
            c.drawRect(0, h - navH, w, h, guideStrip);
        }

        Slot backSlot = imeVisible && images.containsKey(Slot.HIDE_KEYBOARD) ? Slot.HIDE_KEYBOARD : Slot.BACK;
        Slot[] slots = backOnRight
                ? new Slot[]{Slot.RECENTS, Slot.HOME, backSlot}
                : new Slot[]{backSlot, Slot.HOME, Slot.RECENTS};
        float cy = h - navH / 2f + offsetY;
        for (int i = 0; i < 3; i++) {
            Slot s = slots[i];
            // ロック中は戻るだけ(パスワード入力画面ではホームと履歴がない)
            if (locked && s != backSlot) {
                continue;
            }
            SlotImage img = images.get(s);
            float size = navH * sizeFrac;
            if (img != null && snap) {
                size = snapSize(size, img.intrinsicSize());
            }
            float cx = w * (center + (i - 1) * spacing);
            int half = Math.round(size / 2);
            int x0 = Math.round(cx) - half;
            int y0 = Math.round(cy) - half;
            dst.set(x0, y0, x0 + 2 * half, y0 + 2 * half);
            if (img != null) {
                img.draw(c, dst, alpha);
            }
            if (guides || img == null) {
                guide.setAlpha(Math.round(230 * shown));
                c.drawRect(dst, guide);
            }
        }

        if (guides) {
            String s = (Prefs.MAIN.equals(screen) ? "開いた画面" : "閉じた画面")
                    + "  ナビバー " + navH + "px  キーボード " + (imeVisible ? "あり" : "なし")
                    + (locked ? "  ロック中" : "");
            float tw = text.measureText(s);
            float ty = h - navH - 16 * dp;
            c.drawRect(8 * dp, ty - text.getTextSize() - 4 * dp, 16 * dp + tw, ty + 6 * dp, panel);
            c.drawText(s, 12 * dp, ty, text);
        }
        c.restore();
    }

    /**
     * ドットがくっきり見える大きさに寄せる。
     * 元の絵の整数倍か整数分の1(割り切れるもの)のうち、望みの大きさに一番近いもの。
     */
    static float snapSize(float want, int intrinsic) {
        if (intrinsic <= 0) {
            return want;
        }
        float best = intrinsic;
        for (int k = 1; k <= 16; k++) {
            float up = intrinsic * k;
            if (Math.abs(up - want) < Math.abs(best - want)) {
                best = up;
            }
            if (intrinsic % k == 0) {
                float down = (float) intrinsic / k;
                if (Math.abs(down - want) < Math.abs(best - want)) {
                    best = down;
                }
            }
        }
        return best;
    }
}
