package app.animnav;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;

/**
 * 全画面のアプリでナビバーが隠れているあいだだけ、画面の端に置く見えない細い帯。
 * 端からのスワイプを自分で見つけて、ナビバーが出てくるより先に絵を出し始める
 * (ウィンドウ一覧は、バーが出きってから届くので遅い)。
 *
 * - 帯の中のタッチはアプリに届かなくなるので、帯は細くしておく。
 * - Android のスワイプ判定は画面全体のタッチを横から見ているので、帯があってもナビバーはふつうに出る。
 */
class EdgeCatcher extends View {

    enum Edge { BOTTOM, RIGHT, LEFT, TOP }

    interface Listener {
        void onEdgeSwipe();
    }

    // Android が端からのスワイプとみなす条件に合わせる(この時間内に、この距離だけ内側へ動いたら)
    private static final long SWIPE_TIMEOUT_MS = 500;

    private final Listener listener;
    private final int distance;
    private Edge edge = Edge.BOTTOM;
    private float downX;
    private float downY;
    private boolean tracking;

    EdgeCatcher(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        Resources sys = Resources.getSystem();
        int id = sys.getIdentifier("system_gestures_distance_threshold", "dimen", "android");
        int d = id != 0 ? sys.getDimensionPixelSize(id) : 0;
        distance = d > 0 ? d : Math.round(24 * getResources().getDisplayMetrics().density);
    }

    void setEdge(Edge e) {
        edge = e;
    }

    @SuppressLint("ClickableViewAccessibility") // 見えない帯で、クリック操作の対象ではない
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                tracking = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && e.getEventTime() - e.getDownTime() <= SWIPE_TIMEOUT_MS
                        && inward(e) >= distance) {
                    tracking = false;
                    listener.onEdgeSwipe();
                }
                break;
            default:
                tracking = false;
                break;
        }
        return true;
    }

    /** 押した位置から、画面の内側へ動いた距離。 */
    private float inward(MotionEvent e) {
        switch (edge) {
            case TOP:
                return e.getRawY() - downY;
            case RIGHT:
                return downX - e.getRawX();
            case LEFT:
                return e.getRawX() - downX;
            default:
                return downY - e.getRawY();
        }
    }
}
