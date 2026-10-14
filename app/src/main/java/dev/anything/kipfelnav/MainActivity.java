package dev.anything.kipfelnav;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 権限設定への近道と、キーボードを出すための入力欄だけの画面。 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 3, pad, pad);

        TextView title = new TextView(this);
        title.setText("KipfelNav テスト");
        title.setTextSize(22);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);

        TextView steps = new TextView(this);
        steps.setText(
                "1. 「アプリ情報」→ 右上の︙ →「制限付き設定を許可」\n"
                        + "2. 「アクセシビリティ設定」→ インストール済みアプリ →「KipfelNav 重ね描き(テスト)」をオン\n"
                        + "3. ナビバーに水色の帯とオレンジの四角、画面の下のほうにマゼンタの「KB」が出れば成功\n"
                        + "4. 下の入力欄をタップしてキーボードを出し、「KB」がキーボードの上に見えるか確認\n"
                        + "5. 本体を開いたり閉じたりして、帯の位置と数値が追従するか確認\n\n"
                        + "止めるときは、アクセシビリティ設定でオフにしてください。");
        steps.setTextSize(15);
        root.addView(steps);

        Button info = new Button(this);
        info.setText("アプリ情報を開く");
        info.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null))));
        root.addView(info);

        Button a11y = new Button(this);
        a11y.setText("アクセシビリティ設定を開く");
        a11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(a11y);

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
        status.setText(isServiceEnabled() ? "重ね描き:オン" : "重ね描き:オフ(下の手順でオンにしてください)");
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
