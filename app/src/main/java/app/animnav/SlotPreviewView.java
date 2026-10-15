package app.animnav;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.color.MaterialColors;

/** 設定画面で、選んだ画像を動かして見せる小さな枠。 */
public class SlotPreviewView extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Rect dst = new Rect();
    private final float radius;
    private SlotImage image;
    private boolean attached;

    public SlotPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // ナビバーに近い暗い地の上で見せる(重ねたときの見え方に近い)
        bg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        radius = 16 * getResources().getDisplayMetrics().density;
    }

    void setImage(SlotImage img) {
        release();
        image = img;
        if (image instanceof SlotImage.Animated) {
            ((SlotImage.Animated) image).drawable.setCallback(this);
        }
        if (attached && image != null) {
            image.start();
        }
        invalidate();
    }

    private void release() {
        if (image != null) {
            image.stop();
            if (image instanceof SlotImage.Animated) {
                ((SlotImage.Animated) image).drawable.setCallback(null);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (image != null) {
            image.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        release();
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (image instanceof SlotImage.Animated && ((SlotImage.Animated) image).owns(who))
                || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas c) {
        box.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(box, radius, radius, bg);
        if (image == null) {
            return;
        }
        int pad = Math.round(getWidth() * 0.12f);
        int side = Math.min(getWidth(), getHeight()) - 2 * pad;
        int x = (getWidth() - side) / 2;
        int y = (getHeight() - side) / 2;
        dst.set(x, y, x + side, y + side);
        image.draw(c, dst, 255);
        if (image instanceof SlotImage.Frames) {
            postInvalidateOnAnimation(); // PNG 連番は時間でコマを進める
        }
    }
}
