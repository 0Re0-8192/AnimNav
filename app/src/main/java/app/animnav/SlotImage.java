package app.animnav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.List;

/** 1つのボタンに描く絵。GIF/WebP のアニメか、PNG のコマの並び。 */
abstract class SlotImage {

    /** 元の絵の幅(px)。大きさをそろえるのに使う。 */
    abstract int intrinsicSize();

    abstract void draw(Canvas c, Rect dst, int alpha);

    /** 表示用の説明(取り込み画面に出す)。 */
    abstract String describe(Context c);

    void start() {
    }

    void stop() {
    }

    /** GIF / WebP。アニメーションは Android が自分で進める。 */
    static final class Animated extends SlotImage {
        final AnimatedImageDrawable drawable;
        private final String kind;

        Animated(AnimatedImageDrawable d, String kind) {
            this.drawable = d;
            this.kind = kind;
            d.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        }

        @Override
        int intrinsicSize() {
            return drawable.getIntrinsicWidth();
        }

        @Override
        void draw(Canvas c, Rect dst, int alpha) {
            drawable.setBounds(dst);
            drawable.setAlpha(alpha);
            drawable.draw(c);
        }

        @Override
        String describe(Context c) {
            return c.getString(R.string.image_desc_animated, kind, intrinsicSize());
        }

        @Override
        void start() {
            drawable.start();
        }

        @Override
        void stop() {
            drawable.stop();
        }

        boolean owns(Drawable who) {
            return who == drawable;
        }
    }

    /** PNG 1枚、または PNG 連番。コマは1枚ずつ別の Bitmap なので、隣のコマがにじまない。 */
    static final class Frames extends SlotImage {
        private final List<Bitmap> frames;
        private final long frameMs;
        private final Paint paint = new Paint();
        private final Rect src = new Rect();

        Frames(List<Bitmap> frames, long frameMs) {
            this.frames = frames;
            this.frameMs = Math.max(16, frameMs);
            paint.setFilterBitmap(false); // ドット絵なのでぼかさない
        }

        @Override
        int intrinsicSize() {
            return frames.get(0).getWidth();
        }

        @Override
        void draw(Canvas c, Rect dst, int alpha) {
            int i = (int) ((SystemClock.uptimeMillis() / frameMs) % frames.size());
            Bitmap b = frames.get(i);
            src.set(0, 0, b.getWidth(), b.getHeight());
            paint.setAlpha(alpha);
            c.drawBitmap(b, src, dst, paint);
        }

        @Override
        String describe(Context c) {
            return frames.size() == 1
                    ? c.getString(R.string.image_desc_still, intrinsicSize())
                    : c.getString(R.string.image_desc_frames, frames.size(), intrinsicSize());
        }
    }
}
