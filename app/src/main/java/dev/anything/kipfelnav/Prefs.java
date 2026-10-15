package dev.anything.kipfelnav;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 位置合わせの設定。閉じた画面(cover)と開いた画面(main)で別々に持つ。
 * 位置は画面の幅に対する割合なので、回転しても追従する。
 * 初期値は Galaxy Z Fold7 + NavStar の実測値。
 */
final class Prefs {

    static final String NAME = "kipfelnav";
    static final String COVER = "cover";
    static final String MAIN = "main";

    static final String CENTER = "center";      // 真ん中のボタンの中心(幅に対する割合)
    static final String SPACING = "spacing";    // ボタンの間隔(幅に対する割合)
    static final String SIZE = "size";          // 絵の大きさ(ナビバーの高さに対する割合)
    static final String OFFSET_Y = "offsetY";   // 上下のずらし(dp)

    static final String GUIDES = "guides";              // 位置合わせ用の枠
    static final String BACK_ON_RIGHT = "backOnRight";  // 並びが「履歴・ホーム・戻る」
    static final String PIXEL_SNAP = "pixelSnap";       // 整数倍・整数分の1でドットをくっきり
    static final String FRAME_MS = "frameMs";           // PNG 連番の1コマの長さ
    static final String IMAGES_VERSION = "imagesVersion";
    static final String ENTER_STYLE = "enterStyle";     // スワイプで出したバーに遅れて出るときの入り方
    static final String ENTER_PREVIEW = "enterPreview"; // 設定画面から「出方を試す」(値は押した時刻)
    static final int ENTER_RIGHT_FIRST = 0;  // 右(下)のボタンから順に
    static final int ENTER_LEFT_FIRST = 1;   // 左(上)のボタンから順に
    static final int ENTER_ROW = 2;          // 3つを一列のまま
    static final String EDGE_CATCH = "edgeCatch";       // 全画面で端からのスワイプを先読みする(実験。初期値はオフ)
    static final String EDGE_CATCH_DP = "edgeCatchDp";  // そのための帯の幅(dp)

    static final int DEFAULT_FRAME_MS = 150;
    static final int DEFAULT_EDGE_CATCH_DP = 12;

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static String key(String screen, String item) {
        return screen + "." + item;
    }

    /**
     * 設定の保存先。ナビバーが横(カバー画面の横向き)のときは別に持つ。
     * そのときの位置は、画面の高さに対する割合。
     */
    static String screenKey(String screen, boolean sideways) {
        return sideways ? screen + SIDE_SUFFIX : screen;
    }

    static final String SIDE_SUFFIX = "Side";

    static float defaultValue(String screen, String item) {
        boolean cover = screen.startsWith(COVER);
        boolean sideways = screen.endsWith(SIDE_SUFFIX);
        switch (item) {
            case CENTER:
                return cover || sideways ? 0.500f : 0.718f;
            case SPACING:
                // カバー画面の横向きは実測で約 305px / 1080px
                return sideways ? 0.282f : cover ? 0.279f : 0.132f;
            case SIZE:
                return 0.70f;
            default:
                return 0f;
        }
    }

    static float get(SharedPreferences p, String screen, String item) {
        return p.getFloat(key(screen, item), defaultValue(screen, item));
    }

    /** 短い辺が 600dp 以上なら開いた画面。 */
    static String screenFor(int widthPx, int heightPx, float density) {
        return Math.min(widthPx, heightPx) / density >= 600 ? MAIN : COVER;
    }
}
