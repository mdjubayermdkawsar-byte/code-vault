package com.example.keepkeyboard;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class KeepKeyboardService extends InputMethodService {

    private static KeepKeyboardService instance;

    // লেআউট ও প্যানেল
    private FrameLayout contentContainer;
    private LinearLayout englishLayout;
    private LinearLayout banglaLayout;
    private LinearLayout symbolLayout;
    private LinearLayout numpadLayout;
    private LinearLayout menuLayout;
    private LinearLayout clipboardLayout;
    private LinearLayout clipItemsContainer;

    // সাজেশন বার
    private final TextView[] suggestViews = new TextView[3];
    private final StringBuilder currentWordBuffer = new StringBuilder();

    private boolean isShifted = false;
    private final List<Button> englishCharButtons = new ArrayList<>();
    private final List<String> clipHistory = new ArrayList<>();

    // ভেক্টর আইকন আইডি
    private static final int ICON_BACKSPACE = 1;
    private static final int ICON_SEARCH = 2;
    private static final int ICON_SHIFT = 3;
    private static final int ICON_GLOBE = 4;
    private static final int ICON_MENU = 5;
    private static final int ICON_CLIPBOARD = 6;
    private static final int ICON_NUMPAD = 7;
    private static final int ICON_ARROW_BACK = 8;
    private static final int ICON_SETTINGS = 9;
    private static final int ICON_MIC = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ভয়েস টাইপিং ইনসার্ট করার গ্লোবাল মেথড
    public static void insertVoiceText(String text) {
        if (instance != null) {
            InputConnection ic = instance.getCurrentInputConnection();
            if (ic != null && text != null && !text.isEmpty()) {
                ic.commitText(text + " ", 1);
                instance.currentWordBuffer.setLength(0);
                instance.updateSuggestions();
            }
        }
    }

    @Override
    public View onCreateInputView() {
        englishCharButtons.clear();
        currentWordBuffer.setLength(0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1B1B1F"));
        root.setPadding(dp(2), dp(2), dp(2), dp(2));

        // ১. Gboard স্টাইল শীর্ষ সাজেশন ও টুলবার
        root.addView(buildTopToolbar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        // ২. কনটেইনার
        contentContainer = new FrameLayout(this);

        englishLayout = buildEnglishLayout();
        banglaLayout = buildBanglaLayout();
        symbolLayout = buildSymbolLayout();
        numpadLayout = buildNumpadLayout();
        menuLayout = buildMenuLayout();
        clipboardLayout = buildClipboardLayout();

        contentContainer.addView(englishLayout);
        contentContainer.addView(banglaLayout);
        contentContainer.addView(symbolLayout);
        contentContainer.addView(numpadLayout);
        contentContainer.addView(menuLayout);
        contentContainer.addView(clipboardLayout);

        showPanel(englishLayout);

        root.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setupClipboardTracker();
        updateSuggestions();

        return root;
    }

    // শীর্ষ টুলবার (মেন্যু আইকন + ৩টি লাইভ সাজেশন বক্স + ভয়েস মাইক আইকন)
    private LinearLayout buildTopToolbar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(2), dp(4), dp(2));

        // মেন্যু গ্রিড আইকন (টুলস প্যানেল)
        ImageView btnMenu = createToolbarIcon(ICON_MENU);
        btnMenu.setOnClickListener(v -> showPanel(menuLayout));
        topBar.addView(btnMenu);

        // ৩টি রিয়েল-টাইম সাজেশন বাটন
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView tv = new TextView(this);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setBackground(createKeyDrawable(Color.TRANSPARENT, dp(8)));
            tv.setOnClickListener(v -> applySuggestion(suggestViews[index].getText().toString()));
            suggestViews[i] = tv;

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
            p.setMargins(dp(2), dp(2), dp(2), dp(2));
            topBar.addView(tv, p);
        }

        // ভয়েস টাইপিং মাইক বাটন
        ImageView btnMic = createToolbarIcon(ICON_MIC);
        btnMic.setOnClickListener(v -> triggerVoiceTyping());
        topBar.addView(btnMic);

        // ক্লিপবোর্ড বাটন
        ImageView btnClip = createToolbarIcon(ICON_CLIPBOARD);
        btnClip.setOnClickListener(v -> {
            refreshClipboardList();
            showPanel(clipboardLayout);
        });
        topBar.addView(btnClip);

        return topBar;
    }

    // সাজেশন আপডেট লজিক
    private void updateSuggestions() {
        boolean isBangla = (banglaLayout != null && banglaLayout.getVisibility() == View.VISIBLE);
        List<String> list = SuggestionHelper.getSuggestions(currentWordBuffer.toString(), isBangla);
        for (int i = 0; i < 3; i++) {
            if (i < list.size()) {
                suggestViews[i].setText(list.get(i));
                suggestViews[i].setVisibility(View.VISIBLE);
            } else {
                suggestViews[i].setText("");
                suggestViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void applySuggestion(String word) {
        if (word == null || word.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (currentWordBuffer.length() > 0) {
                ic.deleteSurroundingText(currentWordBuffer.length(), 0);
            }
            ic.commitText(word + " ", 1);
            currentWordBuffer.setLength(0);
            updateSuggestions();
        }
    }

    // ভয়েস টাইপিং লঞ্চার
    private void triggerVoiceTyping() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("LAUNCH_VOICE", true);
        intent.putExtra("IS_BANGLA", banglaLayout.getVisibility() == View.VISIBLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // ১. ইংরেজি লেআউট
    private LinearLayout buildEnglishLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(createRow(new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"}, true));
        layout.addView(createRow(new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"}, true));

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setWeightSum(10f);

        ImageView btnShift = new ImageView(this);
        btnShift.setImageDrawable(createVectorIcon(ICON_SHIFT, Color.WHITE, dp(22)));
        btnShift.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnShift.setPadding(dp(10), dp(10), dp(10), dp(10));
        btnShift.setOnClickListener(v -> toggleShift());
        LinearLayout.LayoutParams shiftP = new LinearLayout.LayoutParams(0, dp(48), 1.5f);
        shiftP.setMargins(dp(2), dp(2), dp(2), dp(2));
        row3.addView(btnShift, shiftP);

        for (String c : new String[]{"z", "x", "c", "v", "b", "n", "m"}) {
            Button btn = createCharButton(c, true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
            p.setMargins(dp(2), dp(2), dp(2), dp(2));
            row3.addView(btn, p);
        }

        ImageView btnBack = new ImageView(this);
        btnBack.setImageDrawable(createVectorIcon(ICON_BACKSPACE, Color.WHITE, dp(22)));
        btnBack.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnBack.setPadding(dp(10), dp(10), dp(10), dp(10));
        btnBack.setOnClickListener(v -> handleBackspace());
        LinearLayout.LayoutParams backP = new LinearLayout.LayoutParams(0, dp(48), 1.5f);
        backP.setMargins(dp(2), dp(2), dp(2), dp(2));
        row3.addView(btnBack, backP);

        layout.addView(row3);
        layout.addView(createBottomRow("English", false));

        return layout;
    }

    // ২. সম্পূর্ণ বাংলা লেআউট + কার চিহ্ন বার (Kars Bar)
    private LinearLayout buildBanglaLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // ★ আকার, ই-কার, ঈ-কার, উ-কার, এ-কার সহ সমস্ত কারচিহ্নের বিশেষ রো
        String[] kars = {"া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ", "্"};
        LinearLayout karRow = new LinearLayout(this);
        karRow.setOrientation(LinearLayout.HORIZONTAL);
        karRow.setWeightSum(kars.length);
        karRow.setBackgroundColor(Color.parseColor("#222327"));
        karRow.setPadding(0, dp(2), 0, dp(2));

        for (String kar : kars) {
            Button btnKar = new Button(this);
            btnKar.setText(kar);
            btnKar.setTextColor(Color.parseColor("#8AB4F8")); // উজ্জ্বল নীল রঙে কারচিহ্ন
            btnKar.setTextSize(18);
            btnKar.setPadding(0, 0, 0, 0);
            btnKar.setBackground(createKeyDrawable(Color.parseColor("#2E3035"), dp(6)));
            btnKar.setOnClickListener(v -> commitText(kar));
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            kp.setMargins(dp(1), dp(1), dp(1), dp(1));
            karRow.addView(btnKar, kp);
        }
        layout.addView(karRow);

        // স্বরবর্ণ ও ব্যঞ্জনবর্ণের স্বাভাবিক রো
        layout.addView(createRow(new String[]{"অ", "আ", "ই", "ঈ", "উ", "ঊ", "এ", "ঐ", "ও", "ঔ"}, false));
        layout.addView(createRow(new String[]{"ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"}, false));
        layout.addView(createRow(new String[]{"ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"}, false));
        layout.addView(createRow(new String[]{"প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ"}, false));

        // ৫ম রো
        LinearLayout row5 = new LinearLayout(this);
        row5.setOrientation(LinearLayout.HORIZONTAL);
        row5.setWeightSum(10f);

        for (String c : new String[]{"স", "হ", "ড়", "ঢ়", "য়", "ৎ", "ং", "ঃ", "ঁ"}) {
            Button btn = createCharButton(c, false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
            p.setMargins(dp(1), dp(1), dp(1), dp(1));
            row5.addView(btn, p);
        }

        ImageView btnBack = new ImageView(this);
        btnBack.setImageDrawable(createVectorIcon(ICON_BACKSPACE, Color.WHITE, dp(20)));
        btnBack.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnBack.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(v -> handleBackspace());
        LinearLayout.LayoutParams backP = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
        backP.setMargins(dp(1), dp(1), dp(1), dp(1));
        row5.addView(btnBack, backP);

        layout.addView(row5);
        layout.addView(createBottomRow("বাংলা", true));

        return layout;
    }

    // ৩. ?123 সিম্বল লেআউট
    private LinearLayout buildSymbolLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(createRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}, false));
        layout.addView(createRow(new String[]{"@", "#", "$", "_", "&", "-", "+", "(", ")", "/"}, false));
        layout.addView(createRow(new String[]{"=", "<", ">", "*", "\"", "'", ":", ";", "!", "?"}, false));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setWeightSum(7f);

        Button btnAbc = new Button(this);
        btnAbc.setText("ABC");
        btnAbc.setTextColor(Color.WHITE);
        btnAbc.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnAbc.setOnClickListener(v -> showPanel(englishLayout));
        LinearLayout.LayoutParams pAbc = new LinearLayout.LayoutParams(0, dp(48), 1.5f);
        pAbc.setMargins(dp(2), dp(2), dp(2), dp(2));
        bottom.addView(btnAbc, pAbc);

        Button btnSpace = new Button(this);
        btnSpace.setText("Space");
        btnSpace.setTextColor(Color.parseColor("#C7C7C7"));
        btnSpace.setBackground(createKeyDrawable(Color.parseColor("#35363A"), dp(8)));
        btnSpace.setOnClickListener(v -> commitText(" "));
        LinearLayout.LayoutParams pSpace = new LinearLayout.LayoutParams(0, dp(48), 4.0f);
        pSpace.setMargins(dp(2), dp(2), dp(2), dp(2));
        bottom.addView(btnSpace, pSpace);

        ImageView btnBack = new ImageView(this);
        btnBack.setImageDrawable(createVectorIcon(ICON_BACKSPACE, Color.WHITE, dp(22)));
        btnBack.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnBack.setPadding(dp(10), dp(10), dp(10), dp(10));
        btnBack.setOnClickListener(v -> handleBackspace());
        LinearLayout.LayoutParams pBack = new LinearLayout.LayoutParams(0, dp(48), 1.5f);
        pBack.setMargins(dp(2), dp(2), dp(2), dp(2));
        bottom.addView(btnBack, pBack);

        layout.addView(bottom);
        return layout;
    }

    // ৪. ক্যালকুলেটর / নাম্বারপ্যাড লেআউট
    private LinearLayout buildNumpadLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(createNumpadRow(new String[]{"+", "1", "2", "3", "%"}));
        layout.addView(createNumpadRow(new String[]{"-", "4", "5", "6", ","}));
        layout.addView(createNumpadRow(new String[]{"*", "7", "8", "9", "⌫"}));
        layout.addView(createNumpadRow(new String[]{"/", "ABC", "0", "=", "↵"}));

        return layout;
    }

    private LinearLayout createNumpadRow(String[] items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(items.length);

        for (String item : items) {
            View v;
            if (item.equals("⌫")) {
                ImageView img = new ImageView(this);
                img.setImageDrawable(createVectorIcon(ICON_BACKSPACE, Color.WHITE, dp(22)));
                img.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
                img.setPadding(dp(12), dp(12), dp(12), dp(12));
                img.setOnClickListener(btn -> handleBackspace());
                v = img;
            } else if (item.equals("↵")) {
                ImageView img = new ImageView(this);
                img.setImageDrawable(createVectorIcon(ICON_SEARCH, Color.parseColor("#1B1B1F"), dp(22)));
                img.setBackground(createKeyDrawable(Color.parseColor("#8AB4F8"), dp(22)));
                img.setPadding(dp(12), dp(12), dp(12), dp(12));
                img.setOnClickListener(btn -> handleSearch());
                v = img;
            } else if (item.equals("ABC")) {
                Button btn = new Button(this);
                btn.setText("ABC");
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(14);
                btn.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
                btn.setOnClickListener(b -> showPanel(englishLayout));
                v = btn;
            } else {
                Button btn = new Button(this);
                btn.setText(item);
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(20);
                boolean isOp = "+-*/%=".contains(item);
                btn.setBackground(createKeyDrawable(isOp ? Color.parseColor("#282A2D") : Color.parseColor("#35363A"), dp(8)));
                btn.setOnClickListener(b -> commitText(item));
                v = btn;
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1.0f);
            p.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(v, p);
        }
        return row;
    }

    // ৫. টুলস মেন্যু প্যানেল
    private LinearLayout buildMenuLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(8), dp(12), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView btnClose = new ImageView(this);
        btnClose.setImageDrawable(createVectorIcon(ICON_ARROW_BACK, Color.WHITE, dp(22)));
        btnClose.setPadding(dp(6), dp(6), dp(6), dp(6));
        btnClose.setOnClickListener(v -> showPanel(englishLayout));
        header.addView(btnClose, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText("টুলস ও ফিচার");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        layout.addView(header);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(16), 0, 0);

        row1.addView(createMenuCard("Settings", ICON_SETTINGS, v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }));

        row1.addView(createMenuCard("Voice Typing", ICON_MIC, v -> triggerVoiceTyping()));
        row1.addView(createMenuCard("Clipboard", ICON_CLIPBOARD, v -> {
            refreshClipboardList();
            showPanel(clipboardLayout);
        }));
        row1.addView(createMenuCard("Numpad", ICON_NUMPAD, v -> showPanel(numpadLayout)));

        layout.addView(row1);
        return layout;
    }

    private View createMenuCard(String label, int iconType, View.OnClickListener clickListener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(12), dp(6), dp(12));
        card.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(12)));
        card.setOnClickListener(clickListener);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(createVectorIcon(iconType, Color.parseColor("#8AB4F8"), dp(26)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(6), 0, 0);
        card.addView(tv);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        p.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(p);
        return card;
    }

    // ৬. ক্লিপবোর্ড প্যানেল
    private LinearLayout buildClipboardLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView btnBack = new ImageView(this);
        btnBack.setImageDrawable(createVectorIcon(ICON_ARROW_BACK, Color.WHITE, dp(22)));
        btnBack.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnBack.setOnClickListener(v -> showPanel(englishLayout));
        header.addView(btnBack, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText("ক্লিপবোর্ড (সাম্প্রতিক কপি)");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setPadding(dp(10), 0, 0, 0);
        header.addView(title);
        layout.addView(header);

        ScrollView scroll = new ScrollView(this);
        clipItemsContainer = new LinearLayout(this);
        clipItemsContainer.setOrientation(LinearLayout.VERTICAL);
        clipItemsContainer.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(clipItemsContainer);

        layout.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));

        return layout;
    }

    private void refreshClipboardList() {
        clipItemsContainer.removeAllViews();
        if (clipHistory.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("কোনো কপি করা লেখা পাওয়া যায়নি");
            empty.setTextColor(Color.GRAY);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            clipItemsContainer.addView(empty);
            return;
        }

        for (String text : clipHistory) {
            Button itemBtn = new Button(this);
            itemBtn.setText(text);
            itemBtn.setTextColor(Color.WHITE);
            itemBtn.setTextSize(13);
            itemBtn.setAllCaps(false);
            itemBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            itemBtn.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
            itemBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
            itemBtn.setOnClickListener(v -> {
                commitText(text);
                showPanel(englishLayout);
            });

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, dp(3), 0, dp(3));
            clipItemsContainer.addView(itemBtn, p);
        }
    }

    private void setupClipboardTracker() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.addPrimaryClipChangedListener(() -> {
                if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                    if (text != null && text.length() > 0) {
                        String str = text.toString();
                        clipHistory.remove(str);
                        clipHistory.add(0, str);
                        if (clipHistory.size() > 15) clipHistory.remove(clipHistory.size() - 1);
                    }
                }
            });
        }
    }

    private LinearLayout createRow(String[] chars, boolean isEnglish) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(chars.length);

        for (String c : chars) {
            Button btn = createCharButton(c, isEnglish);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, isEnglish ? dp(48) : dp(44), 1.0f);
            p.setMargins(dp(1), dp(2), dp(1), dp(2));
            row.addView(btn, p);
        }
        return row;
    }

    private LinearLayout createBottomRow(String spaceText, boolean isBangla) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(8.5f);

        Button btnNum = new Button(this);
        btnNum.setText(isBangla ? "?১২৩" : "?123");
        btnNum.setTextColor(Color.WHITE);
        btnNum.setTextSize(13);
        btnNum.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnNum.setOnClickListener(v -> showPanel(symbolLayout));
        LinearLayout.LayoutParams pNum = new LinearLayout.LayoutParams(0, dp(48), 1.2f);
        pNum.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(btnNum, pNum);

        ImageView btnGlobe = new ImageView(this);
        btnGlobe.setImageDrawable(createVectorIcon(ICON_GLOBE, Color.WHITE, dp(22)));
        btnGlobe.setBackground(createKeyDrawable(Color.parseColor("#282A2D"), dp(8)));
        btnGlobe.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnGlobe.setOnClickListener(v -> toggleLanguage());
        LinearLayout.LayoutParams pGlobe = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        pGlobe.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(btnGlobe, pGlobe);

        Button btnSpace = new Button(this);
        btnSpace.setText(spaceText);
        btnSpace.setTextColor(Color.parseColor("#C7C7C7"));
        btnSpace.setTextSize(14);
        btnSpace.setBackground(createKeyDrawable(Color.parseColor("#35363A"), dp(8)));
        btnSpace.setOnClickListener(v -> commitText(" "));
        LinearLayout.LayoutParams pSpace = new LinearLayout.LayoutParams(0, dp(48), 3.8f);
        pSpace.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(btnSpace, pSpace);

        Button btnDot = new Button(this);
        btnDot.setText(isBangla ? "।" : ".");
        btnDot.setTextColor(Color.WHITE);
        btnDot.setTextSize(18);
        btnDot.setBackground(createKeyDrawable(Color.parseColor("#35363A"), dp(8)));
        btnDot.setOnClickListener(v -> commitText(btnDot.getText().toString()));
        LinearLayout.LayoutParams pDot = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        pDot.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(btnDot, pDot);

        ImageView btnSearch = new ImageView(this);
        btnSearch.setImageDrawable(createVectorIcon(ICON_SEARCH, Color.parseColor("#1B1B1F"), dp(22)));
        btnSearch.setBackground(createKeyDrawable(Color.parseColor("#8AB4F8"), dp(22)));
        btnSearch.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnSearch.setOnClickListener(v -> handleSearch());
        LinearLayout.LayoutParams pSearch = new LinearLayout.LayoutParams(0, dp(48), 1.5f);
        pSearch.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(btnSearch, pSearch);

        return row;
    }

    private Button createCharButton(String text, boolean isEnglish) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(isEnglish ? 18 : 16);
        btn.setPadding(0, 0, 0, 0);
        btn.setBackground(createKeyDrawable(Color.parseColor("#35363A"), dp(8)));
        btn.setOnClickListener(v -> commitText(btn.getText().toString()));
        if (isEnglish) englishCharButtons.add(btn);
        return btn;
    }

    private ImageView createToolbarIcon(int iconType) {
        ImageView img = new ImageView(this);
        img.setImageDrawable(createVectorIcon(iconType, Color.parseColor("#E3E3E3"), dp(20)));
        img.setBackground(createKeyDrawable(Color.TRANSPARENT, dp(18)));
        img.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(36), dp(36));
        p.setMargins(dp(2), 0, dp(2), 0);
        img.setLayoutParams(p);
        return img;
    }

    private void showPanel(View activePanel) {
        englishLayout.setVisibility(View.GONE);
        banglaLayout.setVisibility(View.GONE);
        symbolLayout.setVisibility(View.GONE);
        numpadLayout.setVisibility(View.GONE);
        menuLayout.setVisibility(View.GONE);
        clipboardLayout.setVisibility(View.GONE);

        activePanel.setVisibility(View.VISIBLE);
        updateSuggestions();
    }

    private void toggleLanguage() {
        if (englishLayout.getVisibility() == View.VISIBLE) {
            showPanel(banglaLayout);
        } else {
            showPanel(englishLayout);
        }
    }

    private void toggleShift() {
        isShifted = !isShifted;
        for (Button btn : englishCharButtons) {
            String txt = btn.getText().toString();
            btn.setText(isShifted ? txt.toUpperCase() : txt.toLowerCase());
        }
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            if (text.equals(" ")) {
                currentWordBuffer.setLength(0);
            } else {
                currentWordBuffer.append(text);
            }
            updateSuggestions();
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            if (currentWordBuffer.length() > 0) {
                currentWordBuffer.deleteCharAt(currentWordBuffer.length() - 1);
            }
            updateSuggestions();
        }
    }

    private void handleSearch() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            currentWordBuffer.setLength(0);
            updateSuggestions();
        }
    }

    // খাঁটি ভেক্টর আইকন ইঞ্জিন
    private Drawable createVectorIcon(int type, int color, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (type == ICON_BACKSPACE) {
            Path path = new Path();
            path.moveTo(sizePx * 0.35f, sizePx * 0.22f);
            path.lineTo(sizePx * 0.90f, sizePx * 0.22f);
            path.lineTo(sizePx * 0.90f, sizePx * 0.78f);
            path.lineTo(sizePx * 0.35f, sizePx * 0.78f);
            path.lineTo(sizePx * 0.10f, sizePx * 0.50f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(sizePx * 0.45f, sizePx * 0.38f, sizePx * 0.72f, sizePx * 0.62f, paint);
            canvas.drawLine(sizePx * 0.72f, sizePx * 0.38f, sizePx * 0.45f, sizePx * 0.62f, paint);

        } else if (type == ICON_SEARCH) {
            canvas.drawCircle(sizePx * 0.42f, sizePx * 0.42f, sizePx * 0.25f, paint);
            canvas.drawLine(sizePx * 0.60f, sizePx * 0.60f, sizePx * 0.85f, sizePx * 0.85f, paint);

        } else if (type == ICON_SHIFT) {
            Path path = new Path();
            path.moveTo(sizePx * 0.50f, sizePx * 0.20f);
            path.lineTo(sizePx * 0.20f, sizePx * 0.52f);
            path.lineTo(sizePx * 0.38f, sizePx * 0.52f);
            path.lineTo(sizePx * 0.38f, sizePx * 0.82f);
            path.lineTo(sizePx * 0.62f, sizePx * 0.82f);
            path.lineTo(sizePx * 0.62f, sizePx * 0.52f);
            path.lineTo(sizePx * 0.80f, sizePx * 0.52f);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);

        } else if (type == ICON_GLOBE) {
            canvas.drawCircle(sizePx * 0.50f, sizePx * 0.50f, sizePx * 0.38f, paint);
            canvas.drawLine(sizePx * 0.12f, sizePx * 0.50f, sizePx * 0.88f, sizePx * 0.50f, paint);
            canvas.drawOval(new RectF(sizePx * 0.28f, sizePx * 0.12f, sizePx * 0.72f, sizePx * 0.88f), paint);

        } else if (type == ICON_MENU) {
            paint.setStyle(Paint.Style.FILL);
            float s = sizePx * 0.28f;
            canvas.drawRoundRect(new RectF(sizePx * 0.15f, sizePx * 0.15f, sizePx * 0.15f + s, sizePx * 0.15f + s), dp(2), dp(2), paint);
            canvas.drawRoundRect(new RectF(sizePx * 0.57f, sizePx * 0.15f, sizePx * 0.57f + s, sizePx * 0.15f + s), dp(2), dp(2), paint);
            canvas.drawRoundRect(new RectF(sizePx * 0.15f, sizePx * 0.57f, sizePx * 0.15f + s, sizePx * 0.57f + s), dp(2), dp(2), paint);
            canvas.drawRoundRect(new RectF(sizePx * 0.57f, sizePx * 0.57f, sizePx * 0.57f + s, sizePx * 0.57f + s), dp(2), dp(2), paint);

        } else if (type == ICON_CLIPBOARD) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(new RectF(sizePx * 0.22f, sizePx * 0.25f, sizePx * 0.78f, sizePx * 0.88f), dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(sizePx * 0.38f, sizePx * 0.12f, sizePx * 0.62f, sizePx * 0.30f), dp(2), dp(2), paint);

        } else if (type == ICON_NUMPAD) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(new RectF(sizePx * 0.18f, sizePx * 0.18f, sizePx * 0.82f, sizePx * 0.82f), dp(3), dp(3), paint);
            canvas.drawLine(sizePx * 0.18f, sizePx * 0.40f, sizePx * 0.82f, sizePx * 0.40f, paint);
            canvas.drawCircle(sizePx * 0.35f, sizePx * 0.60f, dp(1.5f), paint);
            canvas.drawCircle(sizePx * 0.65f, sizePx * 0.60f, dp(1.5f), paint);

        } else if (type == ICON_ARROW_BACK) {
            Path path = new Path();
            path.moveTo(sizePx * 0.70f, sizePx * 0.20f);
            path.lineTo(sizePx * 0.30f, sizePx * 0.50f);
            path.lineTo(sizePx * 0.70f, sizePx * 0.80f);
            canvas.drawPath(path, paint);

        } else if (type == ICON_SETTINGS) {
            canvas.drawCircle(sizePx * 0.50f, sizePx * 0.50f, sizePx * 0.20f, paint);
            canvas.drawCircle(sizePx * 0.50f, sizePx * 0.50f, sizePx * 0.35f, paint);

        } else if (type == ICON_MIC) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(sizePx * 0.38f, sizePx * 0.18f, sizePx * 0.62f, sizePx * 0.58f), dp(4), dp(4), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(new RectF(sizePx * 0.28f, sizePx * 0.30f, sizePx * 0.72f, sizePx * 0.68f), 0, 180, false, paint);
            canvas.drawLine(sizePx * 0.50f, sizePx * 0.68f, sizePx * 0.50f, sizePx * 0.84f, paint);
        }

        return new BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createKeyDrawable(int normalColor, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(normalColor);
        gd.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), gd, null);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}