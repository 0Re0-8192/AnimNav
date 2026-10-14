package dev.anything.kipfelnav;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ボタンごとの画像の取り込みと読み込み。
 * 対応:GIF・WebP(アニメも可)、PNG 1枚、PNG 連番を固めた ZIP。
 * 旧版の「4行まとめ画像」(sheet.png)が残っていれば、画像のないボタンはそこから切り出して使う。
 */
final class ImageStore {

    private static final int MAX_FRAMES = 600;
    private static final int MAX_SIDE = 1024;
    private static final String[] EXTS = {"gif", "webp", "png", "zip"};

    private ImageStore() {
    }

    // ---- 取り込み

    /** 中身を見て種類を決め、そのボタンの画像として保存する。読めなければ例外。 */
    static void importInto(Context c, Slot slot, Uri uri) throws IOException {
        byte[] bytes = readAll(c.getContentResolver().openInputStream(uri));
        String ext = sniff(bytes);
        if (ext == null) {
            throw new IOException("GIF・WebP・PNG・ZIP のどれでもありません");
        }
        File tmp = new File(c.getFilesDir(), "import.tmp." + ext);
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        try {
            load(tmp, ext, Prefs.of(c).getInt(Prefs.FRAME_MS, Prefs.DEFAULT_FRAME_MS));
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
        clear(c, slot);
        if (!tmp.renameTo(new File(c.getFilesDir(), "slot_" + slot.key + "." + ext))) {
            tmp.delete();
            throw new IOException("保存できませんでした");
        }
    }

    static void clear(Context c, Slot slot) {
        for (String e : EXTS) {
            new File(c.getFilesDir(), "slot_" + slot.key + "." + e).delete();
        }
    }

    private static String sniff(byte[] b) {
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) {
            return "zip";
        }
        return null;
    }

    // ---- 読み込み

    /** そのボタンの画像。なければ旧版のまとめ画像から、それもなければ null。 */
    static SlotImage load(Context c, Slot slot) {
        int frameMs = Prefs.of(c).getInt(Prefs.FRAME_MS, Prefs.DEFAULT_FRAME_MS);
        for (String e : EXTS) {
            File f = new File(c.getFilesDir(), "slot_" + slot.key + "." + e);
            if (f.exists()) {
                try {
                    return load(f, e, frameMs);
                } catch (IOException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return loadLegacy(c, slot, frameMs);
    }

    /** 画像の出どころ(取り込み画面の表示用)。 */
    static boolean hasOwnFile(Context c, Slot slot) {
        for (String e : EXTS) {
            if (new File(c.getFilesDir(), "slot_" + slot.key + "." + e).exists()) {
                return true;
            }
        }
        return false;
    }

    private static SlotImage load(File f, String ext, int frameMs) throws IOException {
        switch (ext) {
            case "gif":
            case "webp": {
                Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f));
                String kind = ext.toUpperCase(Locale.ROOT);
                if (d instanceof AnimatedImageDrawable) {
                    return new SlotImage.Animated((AnimatedImageDrawable) d, kind);
                }
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                return new SlotImage.Frames(Collections.singletonList(b), frameMs, kind);
            }
            case "png": {
                Bitmap b = decodePng(readAll(new FileInputStream(f)));
                return new SlotImage.Frames(Collections.singletonList(b), frameMs, "PNG");
            }
            case "zip":
                return new SlotImage.Frames(readZip(f), frameMs, "PNG連番");
            default:
                throw new IOException("unknown");
        }
    }

    private static List<Bitmap> readZip(File f) throws IOException {
        List<String> names = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(f))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                String base = n.substring(n.lastIndexOf('/') + 1);
                if (e.isDirectory() || n.startsWith("__MACOSX") || base.startsWith(".")
                        || !base.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    continue;
                }
                if (names.size() >= MAX_FRAMES) {
                    throw new IOException("コマが多すぎます(" + MAX_FRAMES + "まで)");
                }
                names.add(base);
                data.add(readAllNoClose(z));
            }
        }
        if (names.isEmpty()) {
            throw new IOException("ZIP の中に PNG がありません");
        }
        // ファイル名の順(数字は数として比べる:2.png < 10.png)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> naturalCompare(names.get(a), names.get(b)));
        List<Bitmap> frames = new ArrayList<>();
        for (int i : order) {
            frames.add(decodePng(data.get(i)));
        }
        return frames;
    }

    private static SlotImage loadLegacy(Context c, Slot slot, int frameMs) {
        File f = new File(c.getFilesDir(), "sheet.png");
        if (!f.exists()) {
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap sheet = BitmapFactory.decodeFile(f.getPath(), o);
        if (sheet == null || sheet.getHeight() % 4 != 0) {
            return null;
        }
        int cell = sheet.getHeight() / 4;
        int n = sheet.getWidth() / cell;
        List<Bitmap> frames = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // 1コマずつ別の Bitmap に切り出す(隣のコマがにじまない)
            frames.add(Bitmap.createBitmap(sheet, i * cell, slot.legacyRow * cell, cell, cell));
        }
        return new SlotImage.Frames(frames, frameMs, "旧まとめ画像");
    }

    private static Bitmap decodePng(byte[] bytes) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (b == null) {
            throw new IOException("PNG を読めません");
        }
        if (b.getWidth() > MAX_SIDE || b.getHeight() > MAX_SIDE) {
            throw new IOException("画像が大きすぎます(" + MAX_SIDE + "px まで)");
        }
        return b;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("開けません");
        }
        try (InputStream s = in) {
            return readAllNoClose(s);
        }
    }

    private static byte[] readAllNoClose(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int cmp = na.length() != nb.length() ? na.length() - nb.length() : na.compareTo(nb);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }
}
