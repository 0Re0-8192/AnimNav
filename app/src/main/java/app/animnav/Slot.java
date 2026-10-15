package app.animnav;

/** 絵を置く場所。取り込む画像はボタンごとに別々。 */
enum Slot {
    BACK("back", R.string.slot_back),
    HOME("home", R.string.slot_home),
    RECENTS("recents", R.string.slot_recents),
    HIDE_KEYBOARD("hidekb", R.string.slot_hidekb);

    final String key;
    final int labelRes;

    Slot(String key, int labelRes) {
        this.key = key;
        this.labelRes = labelRes;
    }
}
