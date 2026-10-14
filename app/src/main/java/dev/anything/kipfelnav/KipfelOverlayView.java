package dev.anything.kipfelnav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * ナビバーの3つのボタンの真上に、動くキプフェルを描く。
 * このビューは画面の下端に置かれる(高さはナビバーより少し大きいくらい)。
 * タップは素通り(ウィンドウ側で FLAG_NOT_TOUCHABLE)。
 * ナビバーが下にないとき(ロック画面・全画面・ナビバーが横にあるとき)は何も描かない。
 */
class KipfelOverlayView extends View {

    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final float dp;
    private final RectF dst = new RectF();
    private final Paint guide = new Paint();
    private final Paint guideStrip = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint();

    private SpriteSheet sheet;
    private int frame;

    // 直近に測った画面の状態
    private int navHeight;
    private boolean imeVisible;
    private String screen = Prefs.COVER;

    KipfelOverlayView(Context context) {
        super(context);
        windowManager = context.getSystemService(WindowManager.class);
        prefs = Prefs.of(context);
        dp = getResources().getDisplayMetrics().density;
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(2 * dp);
        guide.setColor(Color.argb(230, 255, 140, 0));
        guideStrip.setColor(Color.argb(70, 0, 200, 255));
        text.setColor(Color.WHITE);
        text.setTextSize(12 * dp);
        panel.setColor(Color.argb(170, 0, 0, 0));
        reloadSheet();
        measureScreen();
    }

    void reloadSheet() {
        sheet = SpriteSheet.load(getContext());
        invalidate();
    }

    /** 150ms ごとに呼ばれる。コマを進めて、画面の状態を測り直す。 */
    void tick() {
        if (sheet != null) {
            frame = (frame + 1) % sheet.frames;
        }
        refresh();
    }

    /** コマは進めずに、画面の状態だけ測り直す(開閉・回転・画面の切り替わり)。 */
    void refresh() {
        measureScreen();
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
        screen = Prefs.screenFor(b.width(), b.height(), dp);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (navHeight <= 0) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        float center = Prefs.get(prefs, screen, Prefs.CENTER);
        float spacing = Prefs.get(prefs, screen, Prefs.SPACING);
        float sizeFrac = Prefs.get(prefs, screen, Prefs.SIZE);
        float offsetY = Prefs.get(prefs, screen, Prefs.OFFSET_Y) * dp;
        boolean guides = prefs.getBoolean(Prefs.GUIDES, false);
        boolean backOnRight = prefs.getBoolean(Prefs.BACK_ON_RIGHT, false);

        float size = navHeight * sizeFrac;
        if (sheet != null && prefs.getBoolean(Prefs.PIXEL_SNAP, true)) {
            size = Math.max(1, Math.round(size / sheet.cell)) * sheet.cell;
        }
        float cy = h - navHeight / 2f + offsetY;

        if (guides) {
            c.drawRect(0, h - navHeight, w, h, guideStrip);
        }

        int backRow = imeVisible ? SpriteSheet.HIDE_KEYBOARD : SpriteSheet.BACK;
        int[] rows = backOnRight
                ? new int[]{SpriteSheet.RECENTS, SpriteSheet.HOME, backRow}
                : new int[]{backRow, SpriteSheet.HOME, SpriteSheet.RECENTS};
        for (int i = 0; i < 3; i++) {
            float cx = w * (center + (i - 1) * spacing);
            dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            if (sheet != null) {
                sheet.draw(c, rows[i], frame, dst);
            }
            if (guides || sheet == null) {
                c.drawRect(dst, guide);
            }
        }

        if (guides) {
            String s = (Prefs.MAIN.equals(screen) ? "開いた画面" : "閉じた画面")
                    + "  ナビバー " + navHeight + "px  キーボード " + (imeVisible ? "あり" : "なし")
                    + (sheet == null ? "  画像未取り込み" : "");
            float tw = text.measureText(s);
            float ty = h - navHeight - 16 * dp;
            c.drawRect(8 * dp, ty - text.getTextSize() - 4 * dp, 16 * dp + tw, ty + 6 * dp, panel);
            c.drawText(s, 12 * dp, ty, text);
        }
    }
}
