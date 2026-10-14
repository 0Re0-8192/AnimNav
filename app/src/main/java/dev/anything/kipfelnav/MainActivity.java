package dev.anything.kipfelnav;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** 権限設定への近道、画像の取り込み、位置合わせ。 */
public class MainActivity extends Activity {

    private static final int PICK_SHEET = 1;

    private SharedPreferences prefs;
    private String screen;
    private LinearLayout root;
    private TextView status;
    private TextView sheetStatus;
    private int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.of(this);
        pad = (int) (16 * getResources().getDisplayMetrics().density);
        Rect b = getWindowManager().getCurrentWindowMetrics().getBounds();
        screen = Prefs.screenFor(b.width(), b.height(), getResources().getDisplayMetrics().density);

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
        text("4行(戻る・ホーム・履歴・キーボードを閉じる)× コマ数の PNG を取り込みます。", 15);
        sheetStatus = text("", 15);
        button("画像を取り込む", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/png");
            startActivityForResult(i, PICK_SHEET);
        });

        heading("3. 位置合わせ(" + (Prefs.MAIN.equals(screen) ? "開いた画面" : "閉じた画面") + ")", 18);
        text("閉じた画面と開いた画面で別々に保存されます。もう一方は、その画面でこのアプリを開いて合わせてください。", 15);
        check("位置合わせ用の枠を表示", Prefs.GUIDES, false);
        check("ボタンの並びが「履歴・ホーム・戻る」", Prefs.BACK_ON_RIGHT, false);
        check("ドットをくっきり(整数倍で拡大)", Prefs.PIXEL_SNAP, true);
        slider("横位置(真ん中のボタン)", Prefs.CENTER, 0f, 1f, 1000, v -> pct(v));
        slider("ボタンの間隔", Prefs.SPACING, 0f, 0.4f, 400, v -> pct(v));
        slider("大きさ(ナビバーの高さに対して)", Prefs.SIZE, 0.3f, 1.5f, 120, v -> pct(v));
        slider("上下", Prefs.OFFSET_Y, -30f, 30f, 60, v -> String.format(Locale.ROOT, "%+.0fdp", v));
        button("この画面の位置を初期値に戻す", () -> {
            prefs.edit()
                    .remove(Prefs.key(screen, Prefs.CENTER))
                    .remove(Prefs.key(screen, Prefs.SPACING))
                    .remove(Prefs.key(screen, Prefs.SIZE))
                    .remove(Prefs.key(screen, Prefs.OFFSET_Y))
                    .apply();
            recreate();
        });

        heading("4. キーボードの確認", 18);
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
        SpriteSheet s = SpriteSheet.load(this);
        sheetStatus.setText(s == null ? "画像:まだ取り込んでいません" : "画像:取り込み済み(" + s.frames + "コマ)");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SHEET || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            byte[] bytes = readAll(data.getData());
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (!SpriteSheet.isValid(b)) {
                Toast.makeText(this, "形が合いません(4行 × 正方形のコマの PNG が必要です)", Toast.LENGTH_LONG).show();
                return;
            }
            try (FileOutputStream out = new FileOutputStream(SpriteSheet.file(this))) {
                out.write(bytes);
            }
            prefs.edit().putLong(Prefs.SHEET_VERSION, System.currentTimeMillis()).apply();
            Toast.makeText(this, "取り込みました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "読み込めませんでした:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAll(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("開けません");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
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
