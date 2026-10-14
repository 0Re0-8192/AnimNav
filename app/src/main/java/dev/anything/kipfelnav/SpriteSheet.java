package dev.anything.kipfelnav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;

/**
 * 取り込んだコマ画像。4行(戻る・ホーム・履歴・キーボードを閉じる)× N コマの正方形のマス。
 * 絵はアプリに同梱しない(素材の規約で再配布できないため)。利用者が自分で取り込む。
 */
final class SpriteSheet {

    static final int BACK = 0;
    static final int HOME = 1;
    static final int RECENTS = 2;
    static final int HIDE_KEYBOARD = 3;
    static final int ROWS = 4;

    final Bitmap bitmap;
    final int cell;
    final int frames;
    private final Paint paint = new Paint();
    private final Rect src = new Rect();

    private SpriteSheet(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.cell = bitmap.getHeight() / ROWS;
        this.frames = bitmap.getWidth() / cell;
        paint.setFilterBitmap(false); // ドット絵なのでぼかさない
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "sheet.png");
    }

    /** 形が合わなければ null。 */
    static SpriteSheet load(Context c) {
        File f = file(c);
        if (!f.exists()) {
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        return isValid(b) ? new SpriteSheet(b) : null;
    }

    static boolean isValid(Bitmap b) {
        if (b == null || b.getHeight() % ROWS != 0) {
            return false;
        }
        int cell = b.getHeight() / ROWS;
        return cell > 0 && b.getWidth() >= cell && b.getWidth() % cell == 0;
    }

    void draw(Canvas c, int row, int frame, RectF dst) {
        int col = frame % frames;
        src.set(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell);
        c.drawBitmap(bitmap, src, dst, paint);
    }
}
