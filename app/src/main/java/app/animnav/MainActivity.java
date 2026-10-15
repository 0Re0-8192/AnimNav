package app.animnav;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;

/** 準備の案内、画像の取り込み、入り方の選択、位置合わせ。 */
public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private String screen;
    private boolean sideways;
    private TextView statusTitle;
    private TextView statusHint;
    private MaterialCardView statusCard;
    private final EnumMap<Slot, SlotRow> rows = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, ActivityResultLauncher<String[]>> pickers = new EnumMap<>(Slot.class);

    /** 画像の1行(プレビュー・説明・ボタン)。 */
    private static final class SlotRow {
        TextView desc;
        SlotPreviewView preview;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        prefs = Prefs.of(this);

        View scroll = findViewById(R.id.scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat ctl = WindowCompat.getInsetsController(getWindow(), scroll);
        ctl.setAppearanceLightStatusBars(!night);
        ctl.setAppearanceLightNavigationBars(!night);

        // ナビバーが横にある(カバー画面の横向きなど)ときは、その配置用の設定を合わせる
        Rect b = getWindowManager().getCurrentWindowMetrics().getBounds();
        android.graphics.Insets nav = getWindowManager().getCurrentWindowMetrics().getWindowInsets()
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars());
        sideways = nav.bottom == 0 && (nav.left > 0 || nav.right > 0);
        screen = Prefs.screenFor(b.width(), b.height(), getResources().getDisplayMetrics().density);

        bindStatus();
        bindSetup();
        bindImages();
        bindEntrance();
        bindPosition();
        bindExperimental();
        bindAbout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        statusTitle.setText(on ? R.string.status_on : R.string.status_off);
        statusHint.setText(on ? R.string.status_on_hint : R.string.status_off_hint);
        int bg = on ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer;
        int fg = on ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer;
        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, bg));
        statusTitle.setTextColor(MaterialColors.getColor(statusCard, fg));
        statusHint.setTextColor(MaterialColors.getColor(statusCard, fg));
        refreshSlots();
    }

    // ---- 各セクション

    private void bindStatus() {
        statusCard = findViewById(R.id.statusCard);
        statusTitle = findViewById(R.id.statusTitle);
        statusHint = findViewById(R.id.statusHint);
    }

    private void bindSetup() {
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null))));
        findViewById(R.id.btnAccessibility).setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void bindImages() {
        android.widget.LinearLayout container = findViewById(R.id.slotsContainer);
        for (Slot slot : Slot.values()) {
            View row = getLayoutInflater().inflate(R.layout.item_slot, container, false);
            ((TextView) row.findViewById(R.id.slotTitle)).setText(slot.labelRes);
            SlotRow r = new SlotRow();
            r.desc = row.findViewById(R.id.slotDesc);
            r.preview = row.findViewById(R.id.preview);
            rows.put(slot, r);

            ActivityResultLauncher<String[]> picker = registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), uri -> {
                        if (uri != null) {
                            importImage(slot, uri);
                        }
                    });
            pickers.put(slot, picker);
            row.findViewById(R.id.btnChoose).setOnClickListener(v -> picker.launch(
                    new String[]{"image/gif", "image/webp", "image/png", "application/zip"}));
            row.findViewById(R.id.btnRemove).setOnClickListener(v -> {
                ImageStore.clear(this, slot);
                imagesChanged();
            });
            container.addView(row);
        }
        // 1コマの長さ(30〜600ms、10ms 刻み)。コマの長さを変えると画像を読み直すので、指を離したときに保存
        Slider frame = findViewById(R.id.frameSlider);
        TextView label = findViewById(R.id.frameLabel);
        frame.setValueTo(57);
        int ms = prefs.getInt(Prefs.FRAME_MS, Prefs.DEFAULT_FRAME_MS);
        frame.setValue(Math.max(0, Math.min(57, Math.round((ms - 30) / 10f))));
        label.setText(getString(R.string.frame_length, 30 + 10 * (int) frame.getValue()));
        frame.addOnChangeListener((s, value, fromUser) ->
                label.setText(getString(R.string.frame_length, 30 + 10 * (int) value)));
        frame.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {
            }

            @Override
            public void onStopTrackingTouch(Slider s) {
                prefs.edit().putInt(Prefs.FRAME_MS, 30 + 10 * (int) s.getValue()).apply();
            }
        });
    }

    private void importImage(Slot slot, Uri uri) {
        try {
            ImageStore.importInto(this, slot, uri);
            imagesChanged();
            Toast.makeText(this, getString(R.string.toast_imported, getString(slot.labelRes)),
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void imagesChanged() {
        prefs.edit().putLong(Prefs.IMAGES_VERSION, System.currentTimeMillis()).apply();
        refreshSlots();
    }

    private void refreshSlots() {
        for (Slot slot : Slot.values()) {
            SlotRow r = rows.get(slot);
            SlotImage img = ImageStore.load(this, slot);
            if (img == null) {
                r.desc.setText(slot == Slot.HIDE_KEYBOARD ? R.string.slot_not_set_hidekb : R.string.slot_not_set);
            } else {
                r.desc.setText(img.describe(this));
            }
            r.preview.setImage(img);
        }
    }

    private void bindEntrance() {
        MaterialButtonToggleGroup group = findViewById(R.id.enterGroup);
        int[] ids = {R.id.enter0, R.id.enter1, R.id.enter2};
        int now = prefs.getInt(Prefs.ENTER_STYLE, Prefs.ENTER_RIGHT_FIRST);
        group.check(ids[Math.max(0, Math.min(ids.length - 1, now))]);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    prefs.edit().putInt(Prefs.ENTER_STYLE, i)
                            .putLong(Prefs.ENTER_PREVIEW, System.currentTimeMillis()).apply();
                }
            }
        });
        findViewById(R.id.btnPreview).setOnClickListener(v ->
                prefs.edit().putLong(Prefs.ENTER_PREVIEW, System.currentTimeMillis()).apply());
    }

    private void bindPosition() {
        String screenName = getString(screen.startsWith(Prefs.MAIN) ? R.string.screen_large : R.string.screen_small);
        if (sideways) {
            screenName = getString(R.string.screen_with_side, screenName);
        }
        ((TextView) findViewById(R.id.positionTitle)).setText(getString(R.string.section_position, screenName));
        // 保存先:ナビバーが横のときは別に持つ
        String key = Prefs.screenKey(screen, sideways);

        bindSwitch(R.id.swGuides, Prefs.GUIDES, false);
        bindSwitch(R.id.swReverse, Prefs.BACK_ON_RIGHT, false);
        bindSwitch(R.id.swSnap, Prefs.PIXEL_SNAP, true);

        bindFloat(R.id.centerSlider, R.id.centerLabel, key, Prefs.CENTER,
                sideways ? R.string.pos_center_side : R.string.pos_center, 0f, 1f, 1000, true);
        bindFloat(R.id.spacingSlider, R.id.spacingLabel, key, Prefs.SPACING,
                R.string.pos_spacing, 0f, 0.4f, 400, true);
        bindFloat(R.id.sizeSlider, R.id.sizeLabel, key, Prefs.SIZE,
                sideways ? R.string.pos_size_side : R.string.pos_size, 0.3f, 1.5f, 120, true);
        bindFloat(R.id.offsetSlider, R.id.offsetLabel, key, Prefs.OFFSET_Y,
                sideways ? R.string.pos_offset_side : R.string.pos_offset, -30f, 30f, 60, false);

        findViewById(R.id.btnResetPos).setOnClickListener(v -> {
            prefs.edit()
                    .remove(Prefs.key(key, Prefs.CENTER))
                    .remove(Prefs.key(key, Prefs.SPACING))
                    .remove(Prefs.key(key, Prefs.SIZE))
                    .remove(Prefs.key(key, Prefs.OFFSET_Y))
                    .apply();
            recreate();
        });
    }

    private void bindExperimental() {
        bindSwitch(R.id.swEdge, Prefs.EDGE_CATCH, false);
        // 帯の幅:2〜24dp
        Slider s = findViewById(R.id.edgeSlider);
        TextView label = findViewById(R.id.edgeLabel);
        s.setValueTo(22);
        int dp = prefs.getInt(Prefs.EDGE_CATCH_DP, Prefs.DEFAULT_EDGE_CATCH_DP);
        s.setValue(Math.max(0, Math.min(22, dp - 2)));
        label.setText(edgeText((int) s.getValue() + 2));
        s.addOnChangeListener((sl, value, fromUser) -> {
            int v = (int) value + 2;
            label.setText(edgeText(v));
            if (fromUser) {
                prefs.edit().putInt(Prefs.EDGE_CATCH_DP, v).apply();
            }
        });
    }

    private String edgeText(int dp) {
        return getString(R.string.edge_width, dp, Math.round(dp * getResources().getDisplayMetrics().density));
    }

    private void bindAbout() {
        String v = "?";
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        ((TextView) findViewById(R.id.versionText)).setText(getString(R.string.about_version, v));
    }

    // ---- 部品

    private void bindSwitch(int id, String key, boolean def) {
        MaterialSwitch sw = findViewById(id);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
    }

    /** 整数の段数で動くスライダー(小数の刻みによる誤差を避ける)。 */
    private void bindFloat(int sliderId, int labelId, String screenKey, String item, int labelRes,
                           float min, float max, int steps, boolean percent) {
        Slider slider = findViewById(sliderId);
        TextView label = findViewById(labelId);
        slider.setValueTo(steps);
        float cur = Prefs.get(prefs, screenKey, item);
        slider.setValue(Math.max(0, Math.min(steps, Math.round((cur - min) / (max - min) * steps))));
        label.setText(getString(labelRes, format(min + (max - min) * slider.getValue() / steps, percent)));
        slider.addOnChangeListener((s, value, fromUser) -> {
            float val = min + (max - min) * value / steps;
            label.setText(getString(labelRes, format(val, percent)));
            if (fromUser) {
                prefs.edit().putFloat(Prefs.key(screenKey, item), val).apply();
            }
        });
    }

    private static String format(float v, boolean percent) {
        return percent ? String.format(Locale.ROOT, "%.1f%%", v * 100)
                : String.format(Locale.ROOT, "%+.0f dp", v);
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
