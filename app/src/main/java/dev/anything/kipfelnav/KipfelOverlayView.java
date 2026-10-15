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
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ナビバーの3つのボタンの真上に、ボタンごとの絵を描く。タップは素通り。
 *
 * - ナビバーは下にあるとは限らない。カバー画面を横にすると、右(または左)の端に縦に並ぶ。
 * - ナビバーが出ているかは、アプリ向けの画面情報(WindowMetrics)と、アクセシビリティの
 *   ウィンドウ一覧の両方で見る。スワイプで一時的に出したナビバーは前者では「隠れている」扱いに
 *   なるので、後者で拾う(後者は Android 側の都合で 0.2 秒ほど遅れて届く)。
 * - 消えるとき・出てくるときは、純正のナビバーのように外へ沈みながらフェードする。
 * - 全画面のときは、画面の端の細い帯(EdgeCatcher)でスワイプを見つけた時点で、
 *   バーと同じ動きで先に出し始める(先読み)。バーが出てこなければ引っ込める。
 * - ロック中(パスワード入力画面など)は、戻るだけを描く。
 * - キーボード表示中は、戻るの位置に「キーボードを閉じる」の絵を描く。
 */
class KipfelOverlayView extends View {

    enum Side { BOTTOM, RIGHT, LEFT }

    private static final long FADE_MS = 220;
    // スワイプで出したバーが出入りするとき。位置はバー自体に合わせるので、フェードは短く
    private static final long QUICK_FADE_MS = 70;
    // 先読み:Android がバーを出すときの動き(長さと緩急)に合わせて、外から滑り込ませる
    private static final long PREDICT_SLIDE_MS = 275;
    private static final Interpolator BAR_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    // これまでにバーが出てこなければ空振りとして引っ込める
    private static final long PREDICT_TIMEOUT_MS = 1000;

    private final WindowManager windowManager;
    private final KeyguardManager keyguardManager;
    private final SharedPreferences prefs;
    private final float dp;
    private final Rect dst = new Rect();
    private final Rect wb = new Rect();
    private final Paint guide = new Paint();
    private final Paint guideStrip = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint();
    private final Map<Slot, SlotImage> images = new EnumMap<>(Slot.class);

    private boolean running;

    // ---- 直近に測った画面の状態
    private String screen = Prefs.COVER;
    private boolean imeVisible;
    private boolean locked;
    private boolean statusHidden;
    // 隠れているナビバーが出てくる辺と太さ(null = ナビバーがない)
    private Side hiddenSide;
    private int hiddenThick;
    // 画面情報(WindowMetrics)から
    private Side metricsSide;          // null = ナビバーなし
    private int metricsThick;
    // ウィンドウ一覧から
    private Side windowSide;           // null = 見つからない
    private int windowThick;
    private int windowSlide;           // 外へ沈んでいる量
    // まとめ
    private boolean windowOnly;        // スワイプで出したバー(ウィンドウ一覧でだけ見えている)
    private boolean ignoreWindowUntilGone;
    private boolean metricsShownBefore;
    private boolean shownNow;
    private Side side = Side.BOTTOM;   // 最後に見えていた辺(消えていく途中もこれで描く)
    private int thick;                 // ナビバーの本来の太さ
    private int slide;
    private final List<String> windowLines = new ArrayList<>();

    // 先読み
    private long predictStart;         // 0 = 先読みしていない
    private boolean predictConfirmed;  // バーが本当に出てきた
    private int predictHits;
    private int predictMisses;
    private int lateShows;             // 先読みできずに、ウィンドウ一覧で遅れて気づいた回数

    // 出てくる・消えるアニメーション(0 = 消えている、1 = 出ている)
    private float shown;
    private boolean sinkOnFade = true;
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
        measure(Collections.emptyList());
        shown = shownNow ? 1f : 0f;
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

    /** 定期的に、またウィンドウが変わったときに呼ばれる。画面の状態を測り直して描き直す。 */
    void poll(List<AccessibilityWindowInfo> windows) {
        boolean wasShown = shownNow;
        boolean wasWindowOnly = windowOnly;
        measure(windows);
        if (wasShown != shownNow) {
            if (shownNow && predictStart != 0) {
                // 先読み:位置は時間から決める(currentSlide)ので、独自の浮き上がりはしない
                animateShown(true, QUICK_FADE_MS, false);
            } else if (!shownNow && wasWindowOnly) {
                // スワイプで出したバーは、一覧から消えた時点でもう沈みきっている
                animateShown(false, QUICK_FADE_MS, false);
            } else if (shownNow && windowOnly) {
                // スワイプで出したバーは、気づいた時点でもうバーが上がりきっていることが多い。
                // 急に出て見えないように、ふつうの出方(下から浮き上がりながらフェード)にする。
                // バーが途中なら、その位置(slide)ぶんが上に足されるだけで、同じ最終位置に着く
                lateShows++;
                animateShown(true, FADE_MS, true);
            } else {
                animateShown(shownNow, FADE_MS, true);
            }
        }
        invalidate();
    }

    /** スワイプで出したバーが出ているあいだと先読み中。バーの出入りを追いかけるために細かく見る。 */
    boolean trackingSwipeBar() {
        return windowOnly || predictStart != 0;
    }

    /** 画面の端からのスワイプを見つけたとき。ナビバーが出てくるのに合わせて、先に出し始める。 */
    void predictSwipe() {
        if (shownNow || locked || hiddenSide == null) {
            return;
        }
        predictStart = SystemClock.uptimeMillis();
        predictConfirmed = false;
    }

    /** ナビバーが隠れていて、スワイプで出てくるのを待っている(端の帯を置いてよい)。 */
    boolean waitingForSwipe() {
        return !shownNow && !locked && !imeVisible && hiddenSide != null;
    }

    /** 隠れているナビバーが出てくる辺。 */
    Side edgeSide() {
        return hiddenSide != null ? hiddenSide : Side.BOTTOM;
    }

    /** ステータスバーも隠れている(上からのスワイプでもナビバーが出てくる)。 */
    boolean statusBarHidden() {
        return statusHidden;
    }

    /** ナビバーのある辺。消えていく途中は最後に見えていた辺。重ね描きの窓の置き場所に使う。 */
    Side side() {
        return side;
    }

    private void measure(List<AccessibilityWindowInfo> windows) {
        WindowMetrics m = windowManager.getCurrentWindowMetrics();
        Rect b = m.getBounds();
        WindowInsets in = m.getWindowInsets();
        Insets nav = in.getInsets(WindowInsets.Type.navigationBars());
        Insets navAll = in.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars());
        imeVisible = in.isVisible(WindowInsets.Type.ime());
        locked = keyguardManager.isKeyguardLocked();
        statusHidden = in.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top > 0
                && !in.isVisible(WindowInsets.Type.statusBars());
        screen = Prefs.screenFor(b.width(), b.height(), dp);
        hiddenSide = navAll.bottom > 0 ? Side.BOTTOM : navAll.right > 0 ? Side.RIGHT
                : navAll.left > 0 ? Side.LEFT : null;
        hiddenThick = hiddenSide == null ? 0 : inset(navAll, hiddenSide);

        metricsSide = nav.bottom > 0 ? Side.BOTTOM : nav.right > 0 ? Side.RIGHT : nav.left > 0 ? Side.LEFT : null;
        metricsThick = metricsSide == null ? 0 : inset(nav, metricsSide);
        findNavWindow(windows, b.width(), b.height(), navAll);

        boolean metricsShown = metricsSide != null;
        boolean windowShown = windowSide != null;
        // アプリがバーを隠した(画面情報が「隠れた」に変わった)ときは、すぐ消し始める。
        // そのあとバーが沈みきってウィンドウ一覧から消えるまでは、一覧の方は見ない
        if (metricsShownBefore && !metricsShown && windowShown) {
            ignoreWindowUntilGone = true;
        }
        if (!windowShown) {
            ignoreWindowUntilGone = false;
        }
        metricsShownBefore = metricsShown;
        windowOnly = !metricsShown && windowShown && !ignoreWindowUntilGone;
        boolean predicting = updatePrediction(metricsShown || windowOnly);
        shownNow = metricsShown || windowOnly || predicting;

        if (metricsShown || windowOnly) {
            side = metricsShown ? metricsSide : windowSide;
            // 本来の太さ。沈んでいく途中でウィンドウが縮んでも、絵の大きさは変えない
            thick = Math.max(inset(navAll, side), metricsShown ? metricsThick : windowThick);
        } else if (predicting) {
            side = hiddenSide;
            thick = hiddenThick;
        }
        // 沈めた分は、スワイプで出したバーを追いかけているときだけ更新する。
        // 消えていく途中は最後の値のままにして、元の位置に跳ね戻らないようにする。
        // 先読み中は、描くときに時間から決める
        if (windowOnly && !predicting) {
            slide = windowSlide;
        } else if (shownNow) {
            slide = 0;
        }
    }

    /**
     * 先読みを続けるか。バーが出てきたのを確かめて、滑り込みが終わったら、ふだんの判定に引き継ぐ。
     * ウィンドウ一覧はバーが出きってから届くので、それまでは出ている扱いのまま待つ。
     */
    private boolean updatePrediction(boolean barSeen) {
        if (predictStart == 0) {
            return false;
        }
        long age = SystemClock.uptimeMillis() - predictStart;
        if (barSeen) {
            predictConfirmed = true;
        }
        if (predictConfirmed && age >= PREDICT_SLIDE_MS) {
            predictHits++;
            predictStart = 0;
        } else if (!predictConfirmed && (age >= PREDICT_TIMEOUT_MS || locked || hiddenSide == null)) {
            predictMisses++;
            predictStart = 0;
        }
        return predictStart != 0;
    }

    /** 外へ沈んでいる量。先読み中は、バーと同じ動きで外から滑り込む。 */
    private float currentSlide() {
        if (predictStart == 0) {
            return slide;
        }
        float t = (SystemClock.uptimeMillis() - predictStart) / (float) PREDICT_SLIDE_MS;
        if (t >= 1f) {
            return 0;
        }
        postInvalidateOnAnimation();
        return thick * (1f - BAR_IN.getInterpolation(t));
    }

    private static int inset(Insets i, Side s) {
        switch (s) {
            case RIGHT:
                return i.right;
            case LEFT:
                return i.left;
            default:
                return i.bottom;
        }
    }

    /**
     * ウィンドウ一覧から、画面の端にある細長いシステムのウィンドウ(=ナビバー)を探す。
     * Fold7 では「ナビゲーションバー」という名前が付いている。中身は見ない(種類・位置・名前だけ)。
     */
    private void findNavWindow(List<AccessibilityWindowInfo> windows, int sw, int sh, Insets navAll) {
        boolean guides = prefs.getBoolean(Prefs.GUIDES, false);
        if (guides) {
            windowLines.clear();
        }
        // 見つけ始めは 24dp 以上。追いかけている最中は、沈んで細くなっていく途中も拾う
        float min = windowSide != null ? 1 : 24 * dp;
        float max = 120 * dp;
        Side found = null;
        int foundThick = 0;
        int foundSlide = 0;
        for (AccessibilityWindowInfo w : windows) {
            w.getBoundsInScreen(wb);
            CharSequence title = w.getTitle();
            String t = title == null ? "" : title.toString();
            boolean named = t.contains("avigation") || t.contains("ナビゲーション");
            boolean candidate = named || w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM;
            Side s = null;
            int th = 0;
            if (candidate) {
                if (wb.bottom >= sh - 2 && wb.width() >= sw / 2 && wb.height() >= min && wb.height() <= max) {
                    s = Side.BOTTOM;
                    th = wb.height();
                } else if (named && wb.right >= sw - 2 && wb.height() >= sh / 2
                        && wb.width() >= min && wb.width() <= max) {
                    // 横のナビバーは名前で確かめる(エッジパネルの取っ手などを拾わないように)
                    s = Side.RIGHT;
                    th = wb.width();
                } else if (named && wb.left <= 2 && wb.height() >= sh / 2
                        && wb.width() >= min && wb.width() <= max) {
                    s = Side.LEFT;
                    th = wb.width();
                }
            }
            if (s != null && th > foundThick) {
                found = s;
                foundThick = th;
                int full = Math.max(inset(navAll, s), th);
                // 外へ沈んでいる量(ウィンドウ一覧の位置が沈む途中を反映しているときだけ 0 より大きい)
                switch (s) {
                    case RIGHT:
                        foundSlide = wb.left - (sw - full);
                        break;
                    case LEFT:
                        foundSlide = full - wb.right;
                        break;
                    default:
                        foundSlide = wb.top - (sh - full);
                        break;
                }
                foundSlide = Math.max(0, Math.min(full, foundSlide));
            }
            if (guides && windowLines.size() < 12) {
                windowLines.add((s != null ? "★" : "・") + typeName(w.getType()) + " " + wb.toShortString()
                        + (t.isEmpty() ? "" : " 「" + t + "」"));
            }
        }
        windowSide = found;
        windowThick = foundThick;
        windowSlide = foundSlide;
    }

    private static String typeName(int type) {
        switch (type) {
            case AccessibilityWindowInfo.TYPE_APPLICATION:
                return "アプリ";
            case AccessibilityWindowInfo.TYPE_INPUT_METHOD:
                return "キーボード";
            case AccessibilityWindowInfo.TYPE_SYSTEM:
                return "システム";
            case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
                return "重ね描き";
            case AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER:
                return "分割線";
            default:
                return "種類" + type;
        }
    }

    private void animateShown(boolean show, long durationMs, boolean sink) {
        if (fade != null) {
            fade.cancel();
        }
        sinkOnFade = sink;
        float target = show ? 1f : 0f;
        fade = ValueAnimator.ofFloat(shown, target);
        fade.setDuration((long) (durationMs * Math.abs(target - shown)));
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
        boolean guides = prefs.getBoolean(Prefs.GUIDES, false);
        if (shown > 0f && thick > 0) {
            drawButtons(c, guides);
        }
        if (guides) {
            drawDebug(c);
        }
    }

    private void drawButtons(Canvas c, boolean guides) {
        int w = getWidth();
        int h = getHeight();
        String key = Prefs.screenKey(screen, side != Side.BOTTOM);
        float center = Prefs.get(prefs, key, Prefs.CENTER);
        float spacing = Prefs.get(prefs, key, Prefs.SPACING);
        float sizeFrac = Prefs.get(prefs, key, Prefs.SIZE);
        float offset = Prefs.get(prefs, key, Prefs.OFFSET_Y) * dp;
        boolean backOnRight = prefs.getBoolean(Prefs.BACK_ON_RIGHT, false);
        boolean snap = prefs.getBoolean(Prefs.PIXEL_SNAP, true);

        // 外へ沈む量:消えるときのアニメーション + スワイプで出したバーの沈み具合
        float out = (sinkOnFade ? (1f - shown) * thick : 0) + currentSlide();
        int alpha = Math.round(255 * shown);
        c.save();
        switch (side) {
            case RIGHT:
                c.translate(out, 0);
                break;
            case LEFT:
                c.translate(-out, 0);
                break;
            default:
                c.translate(0, out);
                break;
        }

        if (guides) {
            guideStrip.setAlpha(Math.round(70 * shown));
            switch (side) {
                case RIGHT:
                    c.drawRect(w - thick, 0, w, h, guideStrip);
                    break;
                case LEFT:
                    c.drawRect(0, 0, thick, h, guideStrip);
                    break;
                default:
                    c.drawRect(0, h - thick, w, h, guideStrip);
                    break;
            }
        }

        // 並び:下にあるときは左から「戻る・ホーム・履歴」。
        // 横にあるときは、縦向きの並びを 90° 回したもの(右端なら上から「履歴・ホーム・戻る」)
        Slot backSlot = imeVisible && images.containsKey(Slot.HIDE_KEYBOARD) ? Slot.HIDE_KEYBOARD : Slot.BACK;
        Slot[] slots = {backSlot, Slot.HOME, Slot.RECENTS};
        boolean reverse = backOnRight ^ (side == Side.RIGHT);
        if (reverse) {
            slots = new Slot[]{Slot.RECENTS, Slot.HOME, backSlot};
        }
        for (int i = 0; i < 3; i++) {
            Slot s = slots[i];
            // ロック中は戻るだけ(パスワード入力画面ではホームと履歴がない)
            if (locked && s != backSlot) {
                continue;
            }
            SlotImage img = images.get(s);
            float size = thick * sizeFrac;
            if (img != null && snap) {
                size = snapSize(size, img.intrinsicSize());
            }
            float along = center + (i - 1) * spacing;
            float cx;
            float cy;
            switch (side) {
                case RIGHT:
                    cx = w - thick / 2f - offset;
                    cy = h * along;
                    break;
                case LEFT:
                    cx = thick / 2f + offset;
                    cy = h * along;
                    break;
                default:
                    cx = w * along;
                    cy = h - thick / 2f + offset;
                    break;
            }
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
        c.restore();
    }

    /** ガイド表示中の確認用の文字。 */
    private void drawDebug(Canvas c) {
        List<String> lines = new ArrayList<>();
        String sideName = side == Side.RIGHT ? "右" : side == Side.LEFT ? "左" : "下";
        lines.add((Prefs.MAIN.equals(screen) ? "開いた画面" : "閉じた画面")
                + "  ナビバー " + (shownNow ? sideName : "なし")
                + "  キーボード " + (imeVisible ? "あり" : "なし") + (locked ? "  ロック中" : ""));
        lines.add("画面情報 " + (metricsSide == null ? "なし" : metricsSide + " " + metricsThick + "px")
                + " / ウィンドウ一覧 " + (windowSide == null ? "なし" : windowSide + " " + windowThick + "px")
                + " / 本来 " + thick + "px / 沈み " + slide + "px" + (windowOnly ? " (スワイプ)" : ""));
        lines.add("先読み 当たり " + predictHits + " / 空振り " + predictMisses + " / 間に合わず " + lateShows
                + (predictStart != 0 ? "  (先読み中)" : ""));
        lines.addAll(windowLines);
        float lineH = text.getTextSize() * 1.3f;
        float maxW = 0;
        for (String s : lines) {
            maxW = Math.max(maxW, text.measureText(s));
        }
        float top = getHeight() * 0.25f;
        c.drawRect(8 * dp, top - 4 * dp, 16 * dp + maxW, top + lineH * lines.size() + 6 * dp, panel);
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(lines.get(i), 12 * dp, top + lineH * (i + 1) - 4 * dp, text);
        }
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
