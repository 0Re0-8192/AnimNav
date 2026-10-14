package dev.anything.kipfelnav;

/** 絵を置く場所。取り込む画像はボタンごとに別々。 */
enum Slot {
    BACK("back", "戻る", 0),
    HOME("home", "ホーム", 1),
    RECENTS("recents", "履歴", 2),
    HIDE_KEYBOARD("hidekb", "キーボードを閉じる", 3);

    final String key;
    final String label;
    /** 旧版の「4行まとめ画像」での行番号。 */
    final int legacyRow;

    Slot(String key, String label, int legacyRow) {
        this.key = key;
        this.label = label;
        this.legacyRow = legacyRow;
    }
}
