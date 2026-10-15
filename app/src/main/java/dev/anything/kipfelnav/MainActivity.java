package dev.anything.kipfelnav;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;

/** 権限設定への近道、画像の取り込み、位置合わせ。 */
public class MainActivity extends Activity {

    private static final int PICK_BASE = 100; // + Slot.ordinal()

    private SharedPreferences prefs;
    private String screen;
    private boolean sideways;
    private LinearLayout root;
    private TextView status;
    private final EnumMap<Slot, TextView> slotStatus = new EnumMap<>(Slot.class);
    private int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.of(this);
        pad = (int) (16 * getResources().getDisplayMetrics().density);
        Rect b = getWindowManager().getCurrentWindowMetrics().getBounds();
        // ナビバーが横にある(カバー画面の横向き)ときは、その配置用の設定を合わせる
        android.graphics.Insets nav = getWindowManager().getCurrentWindowMetrics().getWindowInsets()
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars());
        sideways = nav.bottom == 0 && (nav.left > 0 || nav.right > 0);
        screen = Prefs.screenKey(
                Prefs.screenFor(b.width(), b.height(), getResources().getDisplayMetrics().density), sideways);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 3, pad, pad * 2);

        heading("KipfelNav", 22);
        status = text("", 16);

        heading("1. 準備", 18);
        text("「アプリ情報」→ 右上の︙ →「制限付き設定を許可」をしてから、"
                + "「アクセシビリティ設定」→ インストール済みアプリ →「KipfelNav」をオン。", 15);
        button("アプリ情報を開く", () -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null))));
        button("アクセシビリティ設定を開く",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        heading("2. 画像", 18);
        text("ボタンごとに取り込みます。GIF・WebP(アニメも可)、PNG 1枚、PNG 連番を固めた ZIP に対応。"
                + "キーボードを閉じるを設定しないと、キーボード表示中も戻るの絵のままです。", 15);
        for (Slot slot : Slot.values()) {
            slotStatus.put(slot, text("", 15));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button pick = new Button(this);
            pick.setText(slot.label + "を選ぶ");
            pick.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"image/gif", "image/webp", "image/png", "application/zip"});
                startActivityForResult(i, PICK_BASE + slot.ordinal());
            });
            Button clear = new Button(this);
            clear.setText("外す");
            clear.setOnClickListener(v -> {
                ImageStore.clear(this, slot);
                imagesChanged();
            });
            row.addView(pick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3));
            row.addView(clear, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            root.addView(row);
        }
        intSlider("PNG 連番・PNG の1コマの長さ", Prefs.FRAME_MS, Prefs.DEFAULT_FRAME_MS, 30, 600, v -> v + "ms");

        heading("3. 位置合わせ(" + (screen.startsWith(Prefs.MAIN) ? "開いた画面" : "閉じた画面")
                + (sideways ? "・ナビバーが横" : "") + ")", 18);
        text("閉じた画面・開いた画面・ナビバーが横のとき(カバー画面の横向き)で別々に保存されます。"
                + "ほかの場合は、その状態でこのアプリを開いて合わせてください。", 15);
        check("位置合わせ用の枠を表示", Prefs.GUIDES, false);
        check("ボタンの並びが「履歴・ホーム・戻る」", Prefs.BACK_ON_RIGHT, false);
        check("ドットをくっきり(整数倍で拡大)", Prefs.PIXEL_SNAP, true);
        slider(sideways ? "縦位置(真ん中のボタン)" : "横位置(真ん中のボタン)", Prefs.CENTER, 0f, 1f, 1000, v -> pct(v));
        slider("ボタンの間隔", Prefs.SPACING, 0f, 0.4f, 400, v -> pct(v));
        slider(sideways ? "大きさ(ナビバーの幅に対して)" : "大きさ(ナビバーの高さに対して)",
                Prefs.SIZE, 0.3f, 1.5f, 120, v -> pct(v));
        slider(sideways ? "内側・外側(+で画面の内側へ)" : "上下(+で下へ)",
                Prefs.OFFSET_Y, -30f, 30f, 60, v -> String.format(Locale.ROOT, "%+.0fdp", v));
        button("この画面の位置を初期値に戻す", () -> {
            prefs.edit()
                    .remove(Prefs.key(screen, Prefs.CENTER))
                    .remove(Prefs.key(screen, Prefs.SPACING))
                    .remove(Prefs.key(screen, Prefs.SIZE))
                    .remove(Prefs.key(screen, Prefs.OFFSET_Y))
                    .apply();
            recreate();
        });

        heading("4. スワイプの先読み(実験)", 18);
        text("音ゲーなど、画面の端からのタッチを使うアプリでは使わないでください。"
                + "全画面のアプリでナビバーをスワイプで出すと、Android の都合でキプフェルが遅れて出ます。"
                + "これをオンにすると、全画面のあいだだけ画面の端(ナビバー側と上端)に見えない細い帯を置き、"
                + "そこから始まったスワイプを自分で見つけて、バーと一緒に出し始めます。"
                + "そのかわり、全画面のときは帯の中のタッチがアプリに届きません。"
                + "帯は「位置合わせ用の枠」をオンにすると赤く見えます。", 15);
        check("スワイプを先読みする(実験)", Prefs.EDGE_CATCH, false);
        intSlider("帯の幅", Prefs.EDGE_CATCH_DP, Prefs.DEFAULT_EDGE_CATCH_DP, 2, 24,
                v -> v + "dp(" + Math.round(v * getResources().getDisplayMetrics().density) + "px)");

        heading("5. キーボードの確認", 18);
        EditText input = new EditText(this);
        input.setHint("ここをタップしてキーボードを出す");
        root.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(isServiceEnabled() ? "重ね描き:オン" : "重ね描き:オフ(1. の手順でオンにしてください)");
        refreshSlotStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int idx = requestCode - PICK_BASE;
        if (idx < 0 || idx >= Slot.values().length || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Slot slot = Slot.values()[idx];
        try {
            ImageStore.importInto(this, slot, data.getData());
            imagesChanged();
            Toast.makeText(this, slot.label + "を取り込みました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "取り込めませんでした:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void imagesChanged() {
        prefs.edit().putLong(Prefs.IMAGES_VERSION, System.currentTimeMillis()).apply();
        refreshSlotStatus();
    }

    private void refreshSlotStatus() {
        for (Slot slot : Slot.values()) {
            SlotImage img = ImageStore.load(this, slot);
            String d;
            if (img == null) {
                d = slot == Slot.HIDE_KEYBOARD ? "未設定(戻るの絵を使います)" : "未設定";
            } else {
                d = img.describe() + "(" + img.intrinsicSize() + "px)";
            }
            slotStatus.get(slot).setText(slot.label + ":" + d);
        }
    }

    // ---- 画面の部品

    private interface Fmt {
        String of(float v);
    }

    private static String pct(float v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    private void heading(String s, int size) {
        TextView t = text(s, size);
        t.setPadding(0, pad, 0, pad / 2);
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        root.addView(t);
        return t;
    }

    private void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }

    private void check(String label, String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(prefs.getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
        root.addView(c);
    }

    private void slider(String label, String item, float min, float max, int steps, Fmt fmt) {
        TextView t = text("", 15);
        SeekBar bar = new SeekBar(this);
        bar.setMax(steps);
        float v = Prefs.get(prefs, screen, item);
        bar.setProgress(Math.round((v - min) / (max - min) * steps));
        t.setText(label + ":" + fmt.of(v));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float val = min + (max - min) * p / steps;
                t.setText(label + ":" + fmt.of(val));
                if (fromUser) {
                    prefs.edit().putFloat(Prefs.key(screen, item), val).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(bar);
    }

    private interface IntFmt {
        String of(int v);
    }

    private void intSlider(String label, String key, int def, int min, int max, IntFmt fmt) {
        TextView t = text("", 15);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int v = prefs.getInt(key, def);
        bar.setProgress(v - min);
        t.setText(label + ":" + fmt.of(v));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                t.setText(label + ":" + fmt.of(min + p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                // 指を離したときだけ保存(コマの長さを変えると画像を読み直すため)
                prefs.edit().putInt(key, min + s.getProgress()).apply();
            }
        });
        root.addView(bar);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        String me = new ComponentName(this, NavOverlayService.class).flattenToString();
        TextUtils.SimpleStringSplitter split = new TextUtils.SimpleStringSplitter(':');
        split.setString(enabled);
        for (String s : split) {
            if (s.equalsIgnoreCase(me)) {
                return true;
            }
        }
        return false;
    }
}
