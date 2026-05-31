package habit.tracker.steda;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int HABITS = 10;
    private static final int WEEKS = 260;
    private static final int DAYS = WEEKS * 7;
    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;

    private static final String SECTION_OVERVIEW = "overview";
    private static final String SECTION_TRACK = "track";
    private static final String SECTION_WEEKLY = "weekly";
    private static final String SECTION_REFLECTION = "reflection";
    private static final String SECTION_TRIBE = "tribe";
    private static final String SECTION_CHAT = "chat";
    private static final String SECTION_SETTINGS = "settings";
    private static final String SECTION_SETUP = "setup";
    private static final String SECTION_STORAGE_CHOICE = "storage_choice";

    private static final int STORAGE_MODE_UNSET = -1;
    private static final int STORAGE_MODE_LOCAL = 0;
    private static final int STORAGE_MODE_ONLINE = 1;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private HabitData data;
    private FrameLayout root;
    private LinearLayout content;
    private ScrollView mainScroll;
    private View chatComposerView;
    private float tribePullStartY = -1f;
    private String currentSection = SECTION_OVERVIEW;
    private int selectedWeek = 0;
    private int pendingAnimDay = -1;
    private int pendingAnimHabit = -1;
    private int setupVisibleHabitCount = 2;
    private EditText[] setupHabitFields;
    private Spinner[] setupTargetSpinners;
    private EditText setupDisplayNameField;
    private EditText setupGroupCodeField;
    private Spinner setupPrivacySpinner;
    private TextView setupDurationField;
    private SharedPreferences prefs;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration tribeRegistration;
    private ListenerRegistration chatRegistration;
    private final ArrayList<TribeMember> tribeMembers = new ArrayList<>();
    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    private String firebaseStatus = "Firebase nicht eingerichtet";
    private String listeningGroup = "";
    private String listeningChatGroup = "";
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSyncRunnable = new Runnable() {
        @Override public void run() {
            if (data != null && data.configured && data.syncEnabled) {
                ensureSignedInAndSync(false);
            }
            syncHandler.postDelayed(this, 30000L);
        }
    };

    private final int bg = Color.rgb(12, 14, 18);
    private final int ink = Color.rgb(244, 247, 250);
    private final int muted = Color.rgb(150, 160, 173);
    private final int green = Color.rgb(74, 192, 113);
    private final int red = Color.rgb(224, 104, 96);
    private final int yellow = Color.rgb(226, 180, 80);
    private final int blue = Color.rgb(110, 138, 230);
    private final int darkBlue = Color.rgb(54, 70, 112);
    private final int cardBg = Color.rgb(22, 26, 33);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("endorphine_tribe_10w_v2", Context.MODE_PRIVATE);
        data = HabitData.fromJson(prefs.getString("data", null));
        setupFirebase();
        selectedWeek = getCurrentWeek();
        if (data.storageMode == STORAGE_MODE_UNSET) {
            showStorageChoice();
        } else if (!data.configured || activeHabitCount() == 0) {
            showSetup(false);
        } else {
            showMainSection(SECTION_OVERVIEW);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startForegroundSync();
    }

    @Override
    protected void onStop() {
        stopForegroundSync();
        super.onStop();
    }

    private void startForegroundSync() {
        startAutoSync();
        if (data != null && data.configured && data.syncEnabled) {
            ensureSignedInAndSync(false);
        }
    }

    private void startAutoSync() {
        syncHandler.removeCallbacks(autoSyncRunnable);
        syncHandler.postDelayed(autoSyncRunnable, 30000L);
    }

    private void stopForegroundSync() {
        syncHandler.removeCallbacks(autoSyncRunnable);
        stopTribeListener();
        stopChatListener();
    }

    @Override
    protected void onDestroy() {
        stopForegroundSync();
        super.onDestroy();
    }

    private void showMainSection(String section) {
        currentSection = section;
        selectedWeek = clamp(selectedWeek, 0, activeWeeks() - 1);
        buildShell(true, false);
        if (SECTION_OVERVIEW.equals(section)) renderOverview();
        else if (SECTION_TRACK.equals(section)) renderTrack();
        else if (SECTION_WEEKLY.equals(section)) renderWeekly();
        else if (SECTION_TRIBE.equals(section)) renderTribe();
        else if (SECTION_CHAT.equals(section)) renderChat();
        else renderReflection();
        animateSectionTransition();
    }

    private void showSettings() {
        currentSection = SECTION_SETTINGS;
        setupVisibleHabitCount = defaultSetupVisibleHabitCount();
        buildShell(true, false);
        renderSettings();
        animateSectionTransition();
    }

    private void showSetup(boolean fromSettings) {
        currentSection = SECTION_SETUP;
        setupVisibleHabitCount = defaultSetupVisibleHabitCount();
        buildShell(false, fromSettings && data.configured);
        renderSetup(fromSettings);
        animateSectionTransition();
    }

    private void showStorageChoice() {
        currentSection = SECTION_STORAGE_CHOICE;
        buildShell(false, false);
        renderStorageChoice();
        animateSectionTransition();
    }

    private void renderStorageChoice() {
        clearContent();
        addSectionTitle("Datenschutzhinweis");

        LinearLayout privacyCard = card();

        privacyCard.addView(smallText("Mit Aktivierung der Online-Speicherung werden dein anonymer Benutzer-Identifikator, dein gewählter Anzeigename, dein Gruppen-Code, deine Habit-Definitionen, tägliche Statusdaten, Wochenreflexionen sowie ggf. Chat-Nachrichten an Google Firebase (Authentication, Firestore) übertragen und dort gespeichert. Es werden keine personenbezogenen Identifikationsdaten (z.B. Klarname, E-Mail) verlangt. Die Daten sind nur Personen mit Kenntnis deines Gruppen-Codes sichtbar. Du kannst die Online-Speicherung jederzeit in den Einstellungen deaktivieren bzw. deine Daten lokal zurücksetzen.\n\nVorteil: Deine Daten sind über deine Benutzer-ID jederzeit wiederherstellbar – auch wenn dein Handy verloren geht oder beschädigt wird."));

        CheckBox privacyCheck = new CheckBox(this);
        privacyCheck.setText("Ich habe den Datenschutzhinweis gelesen und stimme der Online-Speicherung meiner Habit-Daten zu.");
        privacyCheck.setTextColor(ink);
        privacyCheck.setTextSize(13);
        privacyCheck.setButtonTintList(ColorStateList.valueOf(green));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.setMargins(0, dp(10), 0, 0);
        privacyCheck.setLayoutParams(cbLp);
        privacyCard.addView(privacyCheck);

        Button onlineBtn = primaryButton("Fortfahren");
        onlineBtn.setOnClickListener(v -> {
            if (!privacyCheck.isChecked()) {
                toast("Bitte den Datenschutzhinweis bestätigen.");
                return;
            }
            data.storageMode = STORAGE_MODE_ONLINE;
            data.privacyAccepted = true;
            saveData();
            showSetup(false);
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(12), 0, 0);
        onlineBtn.setLayoutParams(btnLp);
        privacyCard.addView(onlineBtn);

        addView(privacyCard);

        LinearLayout localCard = card();
        localCard.addView(label("Nur am Telefon"));
        localCard.addView(smallText("Deine Daten verlassen das Gerät nicht. Tribe Sync und Chat sind nicht verfügbar."));
        Button localBtn = neutralButton("Nur lokal speichern");
        localBtn.setOnClickListener(v -> {
            data.storageMode = STORAGE_MODE_LOCAL;
            data.privacyAccepted = false;
            data.syncEnabled = false;
            data.displayName = "";
            data.groupCode = "";
            saveData();
            showSetup(false);
        });
        localCard.addView(localBtn);
        addView(localCard);
    }

    private int defaultSetupVisibleHabitCount() {
        return clamp(Math.max(2, activeHabitCount()), 2, HABITS);
    }

    private void buildShell(boolean showNav, boolean showGear) {
        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int bottomSpace = showNav ? dp(90) : dp(24);
        content.setPadding(dp(14), dp(18), dp(14), bottomSpace);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnTouchListener((v, event) -> {
            if (!SECTION_TRIBE.equals(currentSection)) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN && scroll.getScrollY() == 0) {
                tribePullStartY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (tribePullStartY >= 0 && scroll.getScrollY() == 0 && event.getY() - tribePullStartY > dp(80)) {
                    toast("Teilnehmer werden aktualisiert …");
                    ensureSignedInAndSync(false);
                }
                tribePullStartY = -1f;
            }
            return false;
        });
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (showNav) {
            LinearLayout bottomNav = new LinearLayout(this);
            bottomNav.setOrientation(LinearLayout.HORIZONTAL);
            bottomNav.setBackground(bottomNavBackground());
            bottomNav.setElevation(dp(14));
            bottomNav.setPadding(dp(6), dp(7), dp(6), dp(7));
            bottomNav.addView(navItem(R.drawable.ic_nav_overview, "Übersicht", SECTION_OVERVIEW));
            bottomNav.addView(navItem(R.drawable.ic_nav_track, "Tracken", SECTION_TRACK));
            bottomNav.addView(navItem(R.drawable.ic_nav_week, "Woche", SECTION_WEEKLY, SECTION_REFLECTION));
            bottomNav.addView(navItem(R.drawable.ic_nav_tribe, "Tribe", SECTION_TRIBE, SECTION_CHAT));

            FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
            navLp.gravity = Gravity.BOTTOM;
            root.addView(bottomNav, navLp);
        }
        setContentView(root);
    }

    private View navItem(int iconRes, String labelText, String primarySection, String... alsoActiveFor) {
        boolean active = primarySection.equals(currentSection);
        for (String s : alsoActiveFor) if (s.equals(currentSection)) active = true;
        int activeTint = green;
        int inactiveTint = Color.rgb(138, 148, 160);

        LinearLayout item = new LinearLayout(this);
        item.setTag(primarySection);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(7), dp(2), dp(6));
        GradientDrawable rippleContent = new GradientDrawable();
        rippleContent.setColor(active ? Color.argb(28, 74, 192, 113) : Color.TRANSPARENT);
        rippleContent.setCornerRadius(dp(16));
        item.setBackground(withRipple(rippleContent, dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        item.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(active ? activeTint : inactiveTint);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
        icon.setLayoutParams(ilp);
        item.addView(icon);

        TextView t = new TextView(this);
        t.setText(labelText);
        t.setTextColor(active ? activeTint : inactiveTint);
        t.setTextSize(11);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(3), 0, 0);
        t.setLayoutParams(tlp);
        item.addView(t);

        item.setOnClickListener(v -> {
            if (SECTION_SETTINGS.equals(currentSection)) {
                autosaveSetupDraft(true);
            }
            showMainSection(primarySection);
        });
        return item;
    }

    private View segmentControl(String leftLabel, boolean leftActive, Runnable leftAction,
                                String rightLabel, boolean rightActive, Runnable rightAction) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.rgb(23, 27, 34));
        barBg.setCornerRadius(dp(22));
        barBg.setStroke(dp(1), Color.argb(30, 255, 255, 255));
        bar.setBackground(barBg);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        bar.setLayoutParams(lp);
        bar.addView(segmentButton(leftLabel, leftActive, leftAction), new LinearLayout.LayoutParams(0, dp(42), 1f));
        bar.addView(segmentButton(rightLabel, rightActive, rightAction), new LinearLayout.LayoutParams(0, dp(42), 1f));
        return bar;
    }

    private Button segmentButton(String labelText, boolean active, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(labelText);
        b.setTextSize(14);
        b.setTextColor(active ? Color.WHITE : Color.rgb(170, 180, 192));
        b.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (active) {
            b.setBackground(tabBackground(true));
            b.setElevation(dp(2));
        } else {
            GradientDrawable transparent = new GradientDrawable();
            transparent.setColor(Color.TRANSPARENT);
            transparent.setCornerRadius(dp(18));
            b.setBackground(withRipple(transparent, dp(18)));
            b.setElevation(0);
        }
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private View iconButton(int iconRes, Runnable action) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(22, 255, 255, 255));
        bg.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        f.setBackground(withRipple(bg, dp(21)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setColorFilter(Color.rgb(212, 220, 230));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(22), dp(22));
        ilp.gravity = Gravity.CENTER;
        f.addView(ic, ilp);
        f.setOnClickListener(v -> action.run());
        return f;
    }

    private void clearContent() {
        content.removeAllViews();
    }

    private TextView sectionBadge(String text) {
        TextView t = pillText(text, Color.rgb(44, 50, 60));
        t.setTextColor(Color.rgb(224, 229, 235));
        return t;
    }

    private void renderOverview() {
        clearContent();

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.setMargins(0, 0, 0, dp(12));
        topBar.setLayoutParams(topLp);
        TextView screenTitle = new TextView(this);
        screenTitle.setText("Übersicht");
        screenTitle.setTextColor(ink);
        screenTitle.setTypeface(Typeface.DEFAULT_BOLD);
        screenTitle.setTextSize(24);
        topBar.addView(screenTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View gear = iconButton(R.drawable.ic_nav_settings, this::showSettings);
        topBar.addView(gear, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(topBar);

        LinearLayout hero = heroCard();
        hero.addView(smallCaps("Challenge Dashboard"));
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroTop.setPadding(0, dp(8), 0, 0);

        LinearLayout heroLeft = new LinearLayout(this);
        heroLeft.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = new TextView(this);
        heroTitle.setText("Woche " + (getCurrentWeek() + 1) + " von " + activeWeeks());
        heroTitle.setTextColor(ink);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setTextSize(30);
        heroLeft.addView(heroTitle);
        TextView heroSub = smallText("Fokus auf Konstanz, Rhythmus und kleine tägliche Schritte.");
        heroSub.setTextColor(Color.rgb(214, 222, 230));
        heroLeft.addView(heroSub);
        heroTop.addView(heroLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroTop.addView(ringChart(percentInt(totalDone(), totalDenom()), "Gesamt"));
        hero.addView(heroTop);

        LinearLayout statsGrid = new LinearLayout(this);
        statsGrid.setOrientation(LinearLayout.HORIZONTAL);
        statsGrid.setPadding(0, dp(16), 0, 0);

        int tDone = dayDone(dayIndexForToday());
        int tDenom = dayDenom(dayIndexForToday());
        int todayAccent = tDenom > 0 && tDone >= tDenom ? green : (tDone > 0 ? yellow : 0);
        statsGrid.addView(metricPill("Heute", tDone + " / " + Math.max(1, tDenom), todayAccent, 0), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statsGrid.addView(space(dp(8)), new LinearLayout.LayoutParams(dp(8), 1));

        int streak = getStreak();
        int streakAccent = streak >= 7 ? red : (streak >= 3 ? yellow : 0);
        int streakIcon = streak >= 3 ? R.drawable.ic_flame : 0;
        statsGrid.addView(metricPill("Streak", streak + " Tage", streakAccent, streakIcon), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statsGrid.addView(space(dp(8)), new LinearLayout.LayoutParams(dp(8), 1));

        int goals = weekGoalsReached(getCurrentWeek());
        int goalAccent = activeHabitCount() > 0 && goals >= activeHabitCount() ? green : 0;
        statsGrid.addView(metricPill("Wochenziele", goals + " / " + activeHabitCount(), goalAccent, 0), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(statsGrid);

        int weekPct = percentInt(weekDone(getCurrentWeek()), weekDenom(getCurrentWeek()));
        LinearLayout barHeader = new LinearLayout(this);
        barHeader.setOrientation(LinearLayout.HORIZONTAL);
        barHeader.setGravity(Gravity.CENTER_VERTICAL);
        barHeader.setPadding(0, dp(18), 0, dp(7));
        barHeader.addView(smallCaps("Diese Woche"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView barValue = new TextView(this);
        barValue.setText(weekPct + "%");
        barValue.setTextColor(ink);
        barValue.setTextSize(13);
        barValue.setTypeface(Typeface.DEFAULT_BOLD);
        barHeader.addView(barValue);
        hero.addView(barHeader);
        ProgressBar pb = progressBar(100, weekPct, green);
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        pblp.setMargins(0, 0, 0, 0);
        hero.addView(pb, pblp);
        addView(hero);

        addSubsectionTitle(activeWeeks() + "-Wochen Heatmap");
        LinearLayout heat = card();
        heat.addView(label("Dein Verlauf auf einen Blick"));
        LinearLayout legendRow1 = new LinearLayout(this);
        legendRow1.setOrientation(LinearLayout.HORIZONTAL);
        legendRow1.addView(legendItem(green, "Stark"));
        legendRow1.addView(legendItem(yellow, "Teilweise"));
        LinearLayout legendRow2 = new LinearLayout(this);
        legendRow2.setOrientation(LinearLayout.HORIZONTAL);
        legendRow2.addView(legendItem(red, "Nicht geschafft"));
        legendRow2.addView(legendItem(Color.rgb(72, 78, 86), "Leer"));
        LinearLayout legendBox = new LinearLayout(this);
        legendBox.setOrientation(LinearLayout.VERTICAL);
        legendBox.addView(legendRow1);
        legendBox.addView(legendRow2);
        heat.addView(legendBox);
        TextView heatHint = smallText("Tippe ein Feld an, um den Tag im Tracken-Reiter zu öffnen.");
        heatHint.setPadding(0, dp(2), 0, dp(2));
        heat.addView(heatHint);

        String[] dowNames = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(data.startDateMillis);
        int startIdx = (startCal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mo=0 .. So=6

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, dp(8), 0, dp(4));
        headerRow.addView(space(dp(28)), new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 7; i++) {
            TextView dh = new TextView(this);
            dh.setText(dowNames[(startIdx + i) % 7]);
            dh.setTextColor(muted);
            dh.setTextSize(10);
            dh.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(0, 0, dp(6), 0);
            headerRow.addView(dh, dlp);
        }
        heat.addView(headerRow);

        int todayIdx = dayIndexForToday();
        for (int w = 0; w < activeWeeks(); w++) {
            final int week = w;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView weekTag = smallCaps("W" + (w + 1));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(weekTag, wlp);
            for (int i = 0; i < 7; i++) {
                final int d = w * 7 + i;
                boolean isToday = d == todayIdx;
                boolean future = d > todayIdx;
                View cell = new View(this);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(dayHeatColor(d));
                gd.setCornerRadius(dp(6));
                if (isToday) gd.setStroke(dp(2), ink);
                else gd.setStroke(dp(1), Color.argb(35, 255, 255, 255));
                cell.setBackground(gd);
                if (future) cell.setAlpha(0.4f);
                cell.setOnClickListener(v -> {
                    selectedWeek = week;
                    showMainSection(SECTION_TRACK);
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(20), dp(20));
                clp.setMargins(0, dp(2), dp(6), dp(2));
                row.addView(cell, clp);
            }
            heat.addView(row);
        }
        addView(heat);

        addSubsectionTitle("Wochenfortschritt");
        for (int w = 0; w < activeWeeks(); w++) {
            LinearLayout row = card();
            row.addView(label("Woche " + (w + 1)));
            row.addView(smallText(formatDate(getDateForDay(w * 7)) + " – " + formatDate(getDateForDay(w * 7 + 6))));
            int done = weekDone(w);
            int denom = weekDenom(w);
            TextView percent = animatedPercent(done, denom);
            percent.setPadding(0, dp(6), 0, 0);
            row.addView(percent);
            row.addView(smallText("Erreichte Habit-Ziele: " + weekGoalsReached(w) + " / " + activeHabitCount()));
            ProgressBar wp = progressBar(100, percentInt(done, denom), w == getCurrentWeek() ? green : blue);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
            wlp.setMargins(0, dp(10), 0, 0);
            row.addView(wp, wlp);
            addView(row);
        }
    }

    private void renderSetup(boolean fromSettings) {
        clearContent();

        renderSetupFields(fromSettings, false);

        LinearLayout actionCard = card();
        if (fromSettings) {
            Button back = neutralButton("Zurück zu den Einstellungen");
            back.setOnClickListener(v -> {
                autosaveSetupDraft(true);
                showSettings();
            });
            actionCard.addView(back);
        } else {
            Button save = primaryButton("Setup abschließen");
            save.setOnClickListener(v -> {
                autosaveSetupDraft(false);
                if (activeHabitCount() == 0) {
                    toast("Bitte mindestens ein Habit eintragen.");
                    return;
                }
                data.configured = true;
                saveData();
                if (data.syncEnabled) ensureSignedInAndSync(false);
                selectedWeek = getCurrentWeek();
                toast("Setup abgeschlossen");
                showMainSection(SECTION_OVERVIEW);
            });
            actionCard.addView(save);
        }
        addView(actionCard);
    }

    private void renderSetupFields(boolean fromSettings, boolean embeddedInSettings) {
        LinearLayout hero = heroCard();
        hero.addView(smallCaps(fromSettings ? "Profil & Challenge" : "Ersteinrichtung"));
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroTop.setPadding(0, dp(8), 0, 0);
        LinearLayout heroLeft = new LinearLayout(this);
        heroLeft.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = new TextView(this);
        heroTitle.setText(fromSettings ? "Einstellungen" : "Willkommen in Steda");
        heroTitle.setTextColor(ink);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setTextSize(27);
        heroLeft.addView(heroTitle);
        heroTop.addView(heroLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroTop.addView(setupHeroIcon());
        hero.addView(heroTop);
        TextView heroSub = smallText(fromSettings
                ? "Passe Startdatum, Habits, Wochenziele und Tribe Sync an."
                : "Richte deine Challenge einmal ein – Zeitraum, optionaler Tribe Sync und deine Habits.");
        heroSub.setTextColor(Color.rgb(214, 222, 230));
        heroSub.setPadding(0, dp(8), 0, 0);
        hero.addView(heroSub);
        addView(hero);

        addView(setupStepHeader(R.drawable.ic_nav_week, "Zeitraum"));
        LinearLayout dateCard = card();
        dateCard.addView(label("Startdatum"));
        Button date = neutralButton("📅  " + formatDate(data.startDateMillis));
        date.setOnClickListener(v -> pickStartDate(() -> {
            if (embeddedInSettings) renderSettings();
            else {
                buildShell(false, fromSettings && data.configured);
                renderSetup(fromSettings);
            }
        }));
        dateCard.addView(date);
        dateCard.addView(label("Challenge-Dauer"));
        Button durationField = neutralButton(data.durationWeeks + "  ▾");
        durationField.setTextSize(16);
        LinearLayout.LayoutParams durationLp = new LinearLayout.LayoutParams(dp(86), dp(42));
        durationLp.setMargins(0, dp(3), 0, dp(4));
        durationField.setLayoutParams(durationLp);
        durationField.setOnClickListener(v -> showDurationPickerDialog(data.durationWeeks, value -> {
            data.durationWeeks = clamp(value, 1, WEEKS);
            selectedWeek = clamp(selectedWeek, 0, activeWeeks() - 1);
            durationField.setText(data.durationWeeks + "  ▾");
            setupDurationField = durationField;
            saveData();
        }));
        setupDurationField = durationField;
        dateCard.addView(durationField);
        dateCard.addView(smallText("Wähle die Dauer als Wochenzahl. Die App passt Übersicht, Heatmap und Wochenauswahl daran an."));
        addView(dateCard);

        setupDisplayNameField = null;
        setupGroupCodeField = null;
        setupPrivacySpinner = null;
        if (data.storageMode == STORAGE_MODE_ONLINE) {
            addView(setupStepHeader(R.drawable.ic_nav_tribe, "Tribe Sync (optional)"));
            LinearLayout syncCard = card();
            syncCard.addView(smallText("Wenn du Name und Gruppen-Code einträgst, kann die App deine Fortschritte mit der Gruppe synchronisieren."));
            syncCard.addView(label("Name in der Gruppe"));
            EditText displayNameField = edit(data.displayName, "z.B. Ju");
            setupDisplayNameField = displayNameField;
            syncCard.addView(displayNameField);
            syncCard.addView(label("Gruppen-Code"));
            EditText groupCodeField = edit(data.groupCode, "z.B. endorphine-tribe-10w");
            setupGroupCodeField = groupCodeField;
            groupCodeField.setSingleLine(true);
            syncCard.addView(groupCodeField);
            syncCard.addView(label("Sichtbarkeit"));
            Spinner privacySpinner = privacySpinner(data.privacyMode);
            setupPrivacySpinner = privacySpinner;
            syncCard.addView(privacySpinner);
            addView(syncCard);
        } else {
            addView(setupStepHeader(R.drawable.ic_nav_settings, "Speicherung: Nur am Telefon"));
            LinearLayout localInfo = card();
            localInfo.addView(smallText("Deine Daten werden ausschließlich lokal auf diesem Gerät gespeichert. Tribe Sync und Chat sind deaktiviert. Du kannst die Online-Speicherung später in den Einstellungen aktivieren."));
            addView(localInfo);
        }

        EditText[] habitFields = new EditText[HABITS];
        Spinner[] targetSpinners = new Spinner[HABITS];
        setupHabitFields = habitFields;
        setupTargetSpinners = targetSpinners;
        int visibleCount = clamp(setupVisibleHabitCount, 2, HABITS);
        addView(setupStepHeader(R.drawable.ic_nav_track, "Deine Habits"));
        for (int i = 0; i < visibleCount; i++) {
            LinearLayout habitCard = card();
            LinearLayout habitHeader = new LinearLayout(this);
            habitHeader.setOrientation(LinearLayout.HORIZONTAL);
            habitHeader.setGravity(Gravity.CENTER_VERTICAL);
            habitHeader.addView(numberBadge(i + 1));
            TextView habitLabel = label("Habit");
            LinearLayout.LayoutParams hllp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hllp.setMargins(dp(9), 0, 0, 0);
            habitHeader.addView(habitLabel, hllp);
            habitCard.addView(habitHeader);
            EditText h = edit(data.habitNames[i], "z.B. Krafttraining, Laufen, Mobility ...");
            habitFields[i] = h;
            habitCard.addView(h);

            habitCard.addView(label("Wie oft pro Woche"));
            Spinner t = targetSpinner(data.targets[i]);
            targetSpinners[i] = t;
            habitCard.addView(t);
            if (embeddedInSettings && (visibleCount > 2 || isHabitActive(i))) {
                int habitIndex = i;
                LinearLayout deleteRow = new LinearLayout(this);
                deleteRow.setGravity(Gravity.END);
                TextView deleteHabit = deleteHabitButton();
                deleteHabit.setOnClickListener(v -> confirmDeleteHabit(habitIndex));
                LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(32), dp(32));
                deleteLp.setMargins(0, dp(4), 0, 0);
                deleteRow.addView(deleteHabit, deleteLp);
                habitCard.addView(deleteRow);
            }
            addView(habitCard);
        }

        if (visibleCount < HABITS) {
            LinearLayout addHabitCard = card();
            Button addHabit = neutralButton("+ Habit hinzufügen");
            addHabit.setOnClickListener(v -> {
                for (int i = 0; i < visibleCount; i++) {
                    data.habitNames[i] = habitFields[i].getText().toString().trim();
                    data.targets[i] = targetSpinners[i].getSelectedItemPosition();
                }
                setupVisibleHabitCount = clamp(setupVisibleHabitCount + 1, 2, HABITS);
                if (embeddedInSettings) renderSettings();
                else renderSetup(fromSettings);
            });
            addHabitCard.addView(addHabit);
            addView(addHabitCard);
        }

    }

    private View setupHeroIcon() {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(38, 74, 192, 113));
        bg.setStroke(dp(2), Color.argb(120, 120, 220, 160));
        f.setBackground(bg);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_nav_track);
        ic.setColorFilter(Color.rgb(120, 224, 160));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(32), dp(32));
        ilp.gravity = Gravity.CENTER;
        f.addView(ic, ilp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(58));
        f.setLayoutParams(lp);
        return f;
    }

    private View setupStepHeader(int iconRes, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(14), 0, dp(8));
        row.setLayoutParams(lp);
        ImageView ic = new ImageView(this);
        ic.setImageResource(iconRes);
        ic.setColorFilter(green);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(19), dp(19));
        row.addView(ic, ilp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(ink);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(16);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(dp(9), 0, 0, 0);
        row.addView(t, tlp);
        return row;
    }

    private TextView numberBadge(int n) {
        TextView t = new TextView(this);
        t.setText(String.valueOf(n));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(13);
        t.setIncludeFontPadding(false);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(green);
        gd.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        t.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView deleteHabitButton() {
        TextView t = new TextView(this);
        t.setText("−");
        t.setTextColor(Color.WHITE);
        t.setTextSize(25);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setBackground(rippleRound(red, dp(16)));
        t.setContentDescription("Habit löschen");
        return t;
    }

    private void confirmDeleteHabit(int habitIndex) {
        autosaveSetupDraft(true);
        if (habitIndex < 0 || habitIndex >= HABITS) return;

        String habitName = data.habitNames[habitIndex] == null ? "" : data.habitNames[habitIndex].trim();
        String message = habitName.isEmpty()
                ? "Dieses Habit-Feld wird entfernt."
                : "\"" + habitName + "\" wird inklusive Verlauf gelöscht.";
        new AlertDialog.Builder(this)
                .setTitle("Habit wirklich löschen?")
                .setMessage(message)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (dialog, which) -> {
                    deleteHabit(habitIndex);
                    toast("Habit gelöscht");
                    renderSettings();
                })
                .show();
    }

    private void deleteHabit(int habitIndex) {
        for (int i = habitIndex; i < HABITS - 1; i++) {
            data.habitNames[i] = data.habitNames[i + 1];
            data.targets[i] = data.targets[i + 1];
            for (int d = 0; d < DAYS; d++) {
                data.states[d][i] = data.states[d][i + 1];
            }
        }
        data.habitNames[HABITS - 1] = "";
        data.targets[HABITS - 1] = 0;
        for (int d = 0; d < DAYS; d++) {
            data.states[d][HABITS - 1] = 0;
        }
        setupVisibleHabitCount = clamp(Math.max(defaultSetupVisibleHabitCount(), setupVisibleHabitCount - 1), 2, HABITS);
        saveData();
        if (data.syncEnabled) ensureSignedInAndSync(false);
    }

    private void autosaveSetupDraft(boolean markConfigured) {
        if (setupHabitFields != null && setupTargetSpinners != null) {
            for (int i = 0; i < HABITS; i++) {
                if (setupHabitFields[i] != null) {
                    data.habitNames[i] = setupHabitFields[i].getText().toString().trim();
                    data.targets[i] = setupTargetSpinners[i].getSelectedItemPosition();
                }
            }
        }
        if (setupDisplayNameField != null) data.displayName = setupDisplayNameField.getText().toString().trim();
        if (setupGroupCodeField != null) data.groupCode = setupGroupCodeField.getText().toString().trim();
        if (setupPrivacySpinner != null) data.privacyMode = setupPrivacySpinner.getSelectedItemPosition();
        if (setupDurationField != null) data.durationWeeks = clamp(parseInt(setupDurationField.getText().toString().replaceAll("[^0-9]", ""), data.durationWeeks), 1, WEEKS);
        selectedWeek = clamp(selectedWeek, 0, activeWeeks() - 1);
        data.syncEnabled = data.storageMode == STORAGE_MODE_ONLINE && !data.displayName.isEmpty() && !data.groupCode.isEmpty();
        if (markConfigured) data.configured = true;
        saveData();
        if (data.syncEnabled) ensureSignedInAndSync(false);
    }

    @Override
    public void onBackPressed() {
        if (SECTION_SETUP.equals(currentSection) && data != null && data.configured) {
            autosaveSetupDraft(true);
            showSettings();
            return;
        }
        if (SECTION_SETTINGS.equals(currentSection)) {
            autosaveSetupDraft(true);
            showMainSection(SECTION_OVERVIEW);
            return;
        }
        if (SECTION_REFLECTION.equals(currentSection)) {
            showMainSection(SECTION_WEEKLY);
            return;
        }
        if (SECTION_CHAT.equals(currentSection)) {
            showMainSection(SECTION_TRIBE);
            return;
        }
        super.onBackPressed();
    }

    private void renderTrack() {
        clearContent();
        
        content.addView(smallText("Status kannst du direkt über die drei Auswahl-Chips setzen."));
        content.addView(compactWeekSpinner("Woche auswählen", selectedWeek, pos -> {
            selectedWeek = pos;
            renderTrack();
        }));

        ArrayList<Integer> days = new ArrayList<>();
        for (int d = selectedWeek * 7; d < selectedWeek * 7 + 7; d++) days.add(d);
        int today = dayIndexForToday();
        if (selectedWeek == getCurrentWeek() && days.contains(today)) {
            days.remove(Integer.valueOf(today));
            days.add(0, today);
        }

        for (int d : days) {
            boolean isToday = d == today;
            LinearLayout dayCard = isToday ? heroCard() : card();
            if (isToday) dayCard.addView(smallCaps("Heute"));

            LinearLayout dayTop = new LinearLayout(this);
            dayTop.setOrientation(LinearLayout.HORIZONTAL);
            dayTop.setGravity(Gravity.CENTER_VERTICAL);
            TextView dayLabel = label("Tag " + (d + 1) + " · " + formatDate(getDateForDay(d)));
            dayLabel.setPadding(0, 0, 0, 0);
            dayTop.addView(dayLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            int dayDone = dayDone(d);
            int dayDenom = dayDenom(d);
            dayTop.addView(sectionBadge(percentText(dayDone, dayDenom)));
            dayCard.addView(dayTop);
            dayCard.addView(smallText(isToday ? "Aktueller Tag" : "Tagesquote"));
            ProgressBar dpb = progressBar(Math.max(1, activeHabitCount()), Math.min(dayDone, Math.max(1, activeHabitCount())), isToday ? green : blue, d == pendingAnimDay);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
            dlp.setMargins(0, dp(8), 0, dp(4));
            dayCard.addView(dpb, dlp);

            for (int h = 0; h < HABITS; h++) {
                if (!isHabitActive(h)) continue;
                int dayIndex = d;
                int habitIndex = h;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackground(subCardBackground());
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.setMargins(0, dp(10), 0, 0);
                row.setLayoutParams(rlp);

                LinearLayout left = new LinearLayout(this);
                left.setOrientation(LinearLayout.VERTICAL);
                TextView habitName = label(data.habitNames[h]);
                habitName.setPadding(0, 0, 0, 0);
                left.addView(habitName);
                left.addView(smallText("Ziel: " + data.targets[h] + "× pro Woche"));
                row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                boolean animateThis = d == pendingAnimDay && h == pendingAnimHabit;
                LinearLayout selector = stateSelector(data.states[d][h], animateThis, newState -> {
                    if (data.states[dayIndex][habitIndex] == newState) data.states[dayIndex][habitIndex] = 0;
                    else data.states[dayIndex][habitIndex] = newState;
                    pendingAnimDay = dayIndex;
                    pendingAnimHabit = habitIndex;
                    saveData();
                    renderTrack();
                });
                row.addView(selector);
                dayCard.addView(row);
            }
            addView(dayCard);
        }

        // Animation nur einmal direkt nach dem Antippen abspielen.
        pendingAnimDay = -1;
        pendingAnimHabit = -1;
    }

    private void renderWeekly() {
        clearContent();
        removeChatComposer();

        content.addView(segmentControl(
                "Statistik", true, () -> { },
                "Reflexion", false, () -> showMainSection(SECTION_REFLECTION)));

        content.addView(compactWeekSpinner("Woche auswählen", selectedWeek, pos -> {
            selectedWeek = pos;
            renderWeekly();
        }));

        LinearLayout summary = heroCard();
        summary.addView(smallCaps("Wochenfokus"));
        TextView wkLabel = label("Woche " + (selectedWeek + 1));
        wkLabel.setTextSize(24);
        summary.addView(wkLabel);
        summary.addView(smallText(formatDate(getDateForDay(selectedWeek * 7)) + " – " + formatDate(getDateForDay(selectedWeek * 7 + 6))));
        TextView weeklyPercent = animatedPercent(weekDone(selectedWeek), weekDenom(selectedWeek));
        weeklyPercent.setPadding(0, dp(8), 0, 0);
        summary.addView(weeklyPercent);
        summary.addView(smallText("Wochenziele erreicht: " + weekGoalsReached(selectedWeek) + " / " + activeHabitCount()));
        ProgressBar wp = progressBar(100, percentInt(weekDone(selectedWeek), weekDenom(selectedWeek)), green);
        LinearLayout.LayoutParams wpl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        wpl.setMargins(0, dp(12), 0, 0);
        summary.addView(wp, wpl);
        addView(summary);

        for (int h = 0; h < HABITS; h++) {
            if (!isHabitActive(h)) continue;
            int done = habitWeekDone(selectedWeek, h);
            int denom = habitWeekDenom(selectedWeek, h);
            boolean reached = data.targets[h] <= 0 || done >= data.targets[h];
            boolean fullyRated = habitWeekFullyRated(selectedWeek, h);
            boolean allX = habitWeekAllX(selectedWeek, h);
            int statusColor;
            String statusText;
            if (reached) {
                statusColor = green;
                statusText = "Wochenziel erreicht";
            } else if (fullyRated && allX) {
                statusColor = red;
                statusText = "Wochenziel nicht erreicht";
            } else if (fullyRated) {
                statusColor = yellow;
                statusText = "Wochenziel nur teilweise erreicht";
            } else {
                statusColor = blue;
                statusText = "Noch offen";
            }
            LinearLayout row = card();
            row.addView(label(data.habitNames[h]));
            row.addView(smallText("Geschafft: " + done + " / " + data.targets[h] + " · Quote: " + percentText(done, denom)));
            ProgressBar hp = progressBar(Math.max(1, data.targets[h]), Math.min(done, Math.max(1, data.targets[h])), statusColor);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
            hlp.setMargins(0, dp(10), 0, 0);
            row.addView(hp, hlp);
            TextView status = pillText(statusText, statusColor);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMargins(0, dp(10), 0, 0);
            row.addView(status, slp);
            addView(row);
        }
    }

    private void renderReflection() {
        clearContent();
        removeChatComposer();

        content.addView(segmentControl(
                "Statistik", false, () -> showMainSection(SECTION_WEEKLY),
                "Reflexion", true, () -> { }));

        content.addView(compactWeekSpinner("Woche auswählen", selectedWeek, pos -> {
            selectedWeek = pos;
            renderReflection();
        }));

        LinearLayout form = card();
        form.addView(label("Woche " + (selectedWeek + 1) + " reflektieren"));
        String[] labels = {"Fokus für die Woche", "Wins / Was lief gut?", "Blocker / Was war schwierig?", "Anpassung für nächste Woche"};
        EditText[] fields = new EditText[4];
        for (int i = 0; i < 4; i++) {
            form.addView(label(labels[i]));
            EditText e = edit(data.reflections[selectedWeek][i], labels[i]);
            e.setMinLines(2);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            fields[i] = e;
            form.addView(e);
        }
        Button save = primaryButton("Reflexion speichern");
        save.setOnClickListener(v -> {
            for (int i = 0; i < 4; i++) data.reflections[selectedWeek][i] = fields[i].getText().toString();
            saveData();
            toast("Reflexion gespeichert");
        });
        form.addView(save);
        addView(form);
    }

    private void renderTribe() {
        clearContent();
        removeChatComposer();

        content.addView(segmentControl(
                "Rangliste", true, () -> { },
                "Chat", false, () -> showMainSection(SECTION_CHAT)));

        if (!data.syncEnabled) {
            LinearLayout off = emptyStateCard(R.drawable.ic_nav_tribe, "Sync ist noch nicht aktiv",
                    "Aktiviere den Tribe Sync über Setup bearbeiten. Danach sehen alle mit demselben Gruppen-Code die gemeinsame Übersicht.");
            Button open = primaryButton("Sync einrichten");
            open.setOnClickListener(v -> showSettings());
            off.addView(open);
            addView(off);
            return;
        }

        if (tribeRegistration == null || !sanitizedGroupCode().equals(listeningGroup)) {
            ensureSignedInAndSync(false);
        }

        if (tribeMembers.isEmpty()) {
            addView(emptyStateCard(R.drawable.ic_nav_tribe, "Noch keine Gruppendaten sichtbar",
                    "Die App synchronisiert automatisch beim Start und ungefähr alle 30 Sekunden. Zieh die Liste nach unten, um manuell zu aktualisieren."));
            return;
        }

        // Doppelte Einträge derselben Person zusammenführen (z.B. Altdaten aus früheren
        // Installationen mit anderer Anmelde-ID): pro Anzeigename nur der neueste Stand.
        Map<String, TribeMember> uniqueByName = new HashMap<>();
        for (TribeMember m : tribeMembers) {
            String key = m.displayName == null ? "" : m.displayName.trim().toLowerCase(Locale.ROOT);
            TribeMember existing = uniqueByName.get(key);
            if (existing == null || m.updatedAtClient > existing.updatedAtClient) {
                uniqueByName.put(key, m);
            }
        }
        ArrayList<TribeMember> sortedMembers = new ArrayList<>(uniqueByName.values());
        Collections.sort(sortedMembers, (a, b) -> {
            if (b.weekPercent != a.weekPercent) return b.weekPercent - a.weekPercent;
            if (b.streak != a.streak) return b.streak - a.streak;
            return a.displayName.compareToIgnoreCase(b.displayName);
        });

        int rank = 1;
        for (TribeMember m : sortedMembers) {
            int accent = tribeAccentColor(m.displayName);
            int medalColor;
            String rankText;
            if (rank == 1) { medalColor = Color.rgb(216, 178, 58); rankText = "🥇 1"; }
            else if (rank == 2) { medalColor = Color.rgb(174, 182, 193); rankText = "🥈 2"; }
            else if (rank == 3) { medalColor = Color.rgb(198, 132, 80); rankText = "🥉 3"; }
            else { medalColor = accent; rankText = "#" + rank; }

            LinearLayout member = card();
            if (rank <= 3) member.setBackground(podiumCardBackground(medalColor));
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView rankBadge = pillText(rankText, medalColor);
            if (rank == 1) rankBadge.setTextColor(Color.rgb(28, 24, 8));
            top.addView(rankBadge);
            TextView avatar = initialCircle(m.displayName, accent);
            LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(48), dp(48));
            avlp.setMargins(dp(10), 0, 0, 0);
            top.addView(avatar, avlp);
            LinearLayout nameBox = new LinearLayout(this);
            nameBox.setOrientation(LinearLayout.VERTICAL);
            nameBox.setPadding(dp(12), 0, 0, 0);
            nameBox.addView(label(m.displayName));
            nameBox.addView(smallText("Aktualisiert: " + timeAgo(m.updatedAtClient)));
            top.addView(nameBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView streakBadge = pillText(m.streak + " Tage", accent);
            top.addView(streakBadge);
            member.addView(top);

            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.setPadding(0, dp(12), 0, 0);
            stats.addView(metricPill("Heute", m.todayDone + " / " + m.todayDenom), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            stats.addView(space(dp(8)), new LinearLayout.LayoutParams(dp(8), 1));
            stats.addView(metricPill("Woche", m.weekPercent + "%"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            stats.addView(space(dp(8)), new LinearLayout.LayoutParams(dp(8), 1));
            stats.addView(metricPill("Gesamt", m.overallPercent + "%"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            member.addView(stats);
            ProgressBar tribeProgress = progressBar(100, m.weekPercent, accent);
            LinearLayout.LayoutParams tplp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
            tplp.setMargins(0, dp(12), 0, 0);
            member.addView(tribeProgress, tplp);
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setPadding(0, dp(12), 0, 0);
            chipRow.addView(pillText("Wochenziele " + m.weekGoals + " / " + m.activeHabits, accent));
            if (m.details != null && !m.details.trim().isEmpty()) {
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                chipLp.setMargins(dp(8), 0, 0, 0);
                chipRow.addView(pillText(m.details, Color.rgb(86, 97, 110)), chipLp);
            }
            member.addView(chipRow);
            addView(member);
            rank++;
        }
    }

    private void renderChat() {
        clearContent();
        removeChatComposer();

        content.addView(segmentControl(
                "Rangliste", false, () -> showMainSection(SECTION_TRIBE),
                "Chat", true, () -> { }));

        if (!data.syncEnabled) {
            LinearLayout off = emptyStateCard(R.drawable.ic_nav_chat, "Chat ist noch nicht aktiv",
                    "Aktiviere den Tribe Sync über Setup bearbeiten. Der Chat nutzt denselben Gruppen-Code wie der Tribe-Reiter.");
            Button open = primaryButton("Sync einrichten");
            open.setOnClickListener(v -> showSettings());
            off.addView(open);
            addView(off);
            return;
        }

        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), dp(168));

        if (chatRegistration == null || !sanitizedGroupCode().equals(listeningChatGroup)) {
            ensureSignedInAndSync(false);
            startChatListener();
        }

        if (chatMessages.isEmpty()) {
            addView(emptyStateCard(R.drawable.ic_nav_chat, "Noch keine Nachrichten",
                    "Schreib die erste Nachricht. Alle mit demselben Gruppen-Code sehen sie hier."));
        } else {
            long lastDayKey = Long.MIN_VALUE;
            for (ChatMessage msg : chatMessages) {
                long dayKey = dayKeyOf(msg.createdAtClient);
                if (dayKey != lastDayKey) {
                    addView(chatDateSeparator(msg.createdAtClient));
                    lastDayKey = dayKey;
                }

                boolean mine = auth != null && auth.getCurrentUser() != null && auth.getCurrentUser().getUid().equals(msg.userId);
                LinearLayout outer = new LinearLayout(this);
                outer.setOrientation(LinearLayout.HORIZONTAL);
                outer.setGravity(mine ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                outerLp.setMargins(0, 0, 0, dp(8));
                outer.setLayoutParams(outerLp);

                if (!mine) {
                    TextView avatar = initialCircle(msg.displayName, tribeAccentColor(msg.displayName));
                    avatar.setTextSize(14);
                    LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(34), dp(34));
                    avlp.setMargins(0, 0, dp(8), 0);
                    avlp.gravity = Gravity.BOTTOM;
                    outer.addView(avatar, avlp);
                }

                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(14), dp(9), dp(14), dp(10));
                bubble.setBackground(chatBubbleBackground(mine));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(mine ? dp(262) : dp(250), ViewGroup.LayoutParams.WRAP_CONTENT);
                bubble.setLayoutParams(blp);

                TextView sender = smallCaps((mine ? "Du" : msg.displayName) + " · " + timeAgo(msg.createdAtClient));
                bubble.addView(sender);
                TextView body = new TextView(this);
                body.setText(msg.text);
                body.setTextColor(ink);
                body.setTextSize(15);
                body.setPadding(0, dp(5), 0, 0);
                bubble.addView(body);
                outer.addView(bubble);
                addView(outer);
            }
        }

        addChatComposer();
        if (mainScroll != null) mainScroll.post(() -> mainScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void removeChatComposer() {
        if (chatComposerView != null) {
            try { root.removeView(chatComposerView); } catch (Exception ignored) { }
            chatComposerView = null;
        }
    }

    private void addChatComposer() {
        removeChatComposer();

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(10), dp(12), dp(10));
        composer.setBackground(chatComposerBackground());
        composer.setElevation(dp(8));

        EditText messageField = edit("", "Nachricht");
        messageField.setSingleLine(false);
        messageField.setMinLines(1);
        messageField.setMaxLines(4);
        messageField.setBackground(chatInputBackground());
        messageField.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        composer.addView(messageField, inputLp);

        Button send = new Button(this);
        send.setText("➤");
        send.setAllCaps(false);
        send.setTextColor(Color.WHITE);
        send.setTextSize(18);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setBackground(rippleRound(green, dp(23)));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        sendLp.setMargins(dp(10), 0, 0, 0);
        composer.addView(send, sendLp);

        send.setOnClickListener(v -> {
            String msg = messageField.getText().toString().trim();
            if (msg.isEmpty()) return;
            sendChatMessage(msg);
            messageField.setText("");
        });

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM;
        flp.setMargins(0, 0, 0, dp(70));
        root.addView(composer, flp);
        chatComposerView = composer;
    }

    private void renderSettings() {
        clearContent();
        setupVisibleHabitCount = clamp(Math.max(setupVisibleHabitCount, defaultSetupVisibleHabitCount()), 2, HABITS);

        renderSetupFields(true, true);

        LinearLayout menu = card();
        menu.addView(label("Daten"));
        menu.addView(smallText("Die App speichert alle Daten automatisch Online. Mit der Benutzer ID können alle Daten wiederhergestellt werden."));

        menu.addView(label("Benutzer ID"));
        menu.addView(userIdBox(data.userId));

        Button changeId = neutralButton("Benutzer ID ändern");
        changeId.setOnClickListener(v -> confirmChangeUserId());
        menu.addView(changeId);

        Button export = neutralButton("Daten exportieren");
        export.setOnClickListener(v -> {
            autosaveSetupDraft(true);
            exportJson();
        });
        menu.addView(export);

        Button imp = neutralButton("Daten importieren");
        imp.setOnClickListener(v -> {
            autosaveSetupDraft(true);
            importJson();
        });
        menu.addView(imp);

        Button reset = dangerButton("App zurücksetzen");
        reset.setOnClickListener(v -> confirmResetApp());
        menu.addView(reset);

        addView(menu);

        LinearLayout info = card();
        info.addView(label("Status-Legende"));
        info.addView(smallText("✓ = geschafft · ✗ = nicht geschafft · – = bewusst ausgelassen/neutral · leer = noch nicht eingetragen"));
        info.addView(smallText("Quoten zählen nur ✓ und ✗. – und leere Felder bleiben neutral."));
        addView(info);

        LinearLayout dev = card();
        dev.addView(label("Entwickler-Kontakt"));
        dev.addView(smallText("Fragen, Feedback oder ein Fehler? Schreib direkt dem Entwickler."));
        Button contact = neutralButton("Kontaktieren");
        contact.setOnClickListener(v -> openDeveloperEmail());
        dev.addView(contact);
        addView(dev);

        LinearLayout license = card();
        license.addView(label("Lizenz"));
        license.addView(smallText("Diese Software steht unter der Lizenz"));
        TextView licenseName = new TextView(this);
        licenseName.setText("CC NC-SA 4.0");
        licenseName.setTextColor(ink);
        licenseName.setTypeface(Typeface.DEFAULT_BOLD);
        licenseName.setTextSize(18);
        LinearLayout.LayoutParams licenseNameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        licenseNameLp.setMargins(0, dp(6), 0, dp(6));
        licenseName.setLayoutParams(licenseNameLp);
        license.addView(licenseName);
        license.addView(smallText("Kurz gesagt: Du darfst die Software teilen und weiterentwickeln, solange du sie nicht kommerziell nutzt und deine Änderungen unter derselben Lizenz weitergibst."));
        addView(license);

        LinearLayout contribute = card();
        contribute.addView(label("Mitentwickeln"));
        contribute.addView(smallText("Willst du Mitentwickeln?"));
        Button github = primaryButton("GitHub Repository öffnen");
        github.setOnClickListener(v -> openGitHubRepo());
        contribute.addView(github);
        addView(contribute);
    }

    private void openGitHubRepo() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hamujuls/Steda"));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            toast("Kein Browser gefunden.");
        }
    }

    private void openDeveloperEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:j.hackermueller@gmail.com"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"j.hackermueller@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Steda – Feedback");
        try {
            startActivity(Intent.createChooser(intent, "E-Mail an den Entwickler"));
        } catch (android.content.ActivityNotFoundException e) {
            toast("Keine E-Mail-App gefunden.");
        }
    }

    private void confirmResetApp() {
        new AlertDialog.Builder(this)
                .setTitle("App wirklich zurücksetzen?")
                .setMessage("Alle lokalen Challenge-Daten werden gelöscht. Dieser Schritt kann nicht rückgängig gemacht werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Zurücksetzen", (dialog, which) -> {
                    stopTribeListener();
                    stopChatListener();
                    data = new HabitData();
                    saveData();
                    toast("App zurückgesetzt");
                    showStorageChoice();
                })
                .show();
    }

    private LinearLayout userIdBox(String id) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(inputBackground());
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(8));
        row.setLayoutParams(rowLp);
        row.setOnClickListener(v -> copyUserId());

        TextView t = new TextView(this);
        t.setText(id == null ? "" : id);
        t.setTextColor(ink);
        t.setTextSize(14);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t.setSingleLine(false);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = new TextView(this);
        copy.setText("📋");
        copy.setTextColor(Color.WHITE);
        copy.setTextSize(20);
        copy.setGravity(Gravity.CENTER);
        copy.setTypeface(Typeface.DEFAULT_BOLD);
        copy.setBackground(rippleRound(darkBlue, dp(16)));
        copy.setOnClickListener(v -> copyUserId());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(42), dp(42));
        cp.setMargins(dp(10), 0, 0, 0);
        row.addView(copy, cp);
        return row;
    }

    private void copyUserId() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Habit Challenge Benutzer ID", data.userId));
            toast("Benutzer ID kopiert");
        }
    }

    private void confirmChangeUserId() {
        new AlertDialog.Builder(this)
                .setTitle("Benutzer ID ändern?")
                .setMessage("Möchtest du die Benutzer ID wirklich ändern? Mit einer bestehenden ID kannst du Daten von einem anderen Gerät wiederherstellen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Ja", (dialog, which) -> showChangeUserIdDialog())
                .show();
    }

    private void showChangeUserIdDialog() {
        EditText input = edit("", "Benutzer ID eingeben");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Benutzer ID eingeben")
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen", (dialog, which) -> {
                    String newId = input.getText().toString().trim();
                    if (newId.isEmpty()) {
                        toast("Bitte eine Benutzer ID eingeben.");
                        return;
                    }
                    changeUserIdAndRestore(newId);
                })
                .show();
    }

    private void changeUserIdAndRestore(String newId) {
        autosaveSetupDraft(true);
        if (firestore == null) {
            data.userId = newId;
            saveData();
            toast("Benutzer ID geändert");
            renderSettings();
            return;
        }
        firestore.collection("users").document(newId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("dataJson") != null) {
                        data = HabitData.fromJson(doc.getString("dataJson"));
                        data.userId = newId;
                        saveData();
                        selectedWeek = getCurrentWeek();
                        toast("Daten wiederhergestellt");
                    } else {
                        data.userId = newId;
                        saveData();
                        toast("Benutzer ID geändert");
                    }
                    renderSettings();
                })
                .addOnFailureListener(e -> {
                    data.userId = newId;
                    saveData();
                    toast("Benutzer ID geändert. Online-Wiederherstellung aktuell nicht erreichbar.");
                    renderSettings();
                });
    }

    private Spinner privacySpinner(int selected) {
        Spinner spinner = new Spinner(this);
        String[] items = new String[]{"Nur Fortschritt anzeigen", "Habit-Details für heute anzeigen"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(clamp(selected, 0, items.length - 1));
        return spinner;
    }

    private Spinner targetSpinner(int selected) {
        Spinner spinner = new Spinner(this);
        String[] items = new String[]{"0  ▾", "1  ▾", "2  ▾", "3  ▾", "4  ▾", "5  ▾", "6  ▾", "7  ▾"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int safeSelected = clamp(selected, 0, 7);
        spinner.setSelection(safeSelected);
        spinner.setBackground(inputBackground());
        spinner.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(38));
        lp.setMargins(0, dp(3), 0, dp(4));
        spinner.setLayoutParams(lp);
        return spinner;
    }

    private View compactWeekSpinner(String labelText, int initial, final WeekSelection callback) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(boxLp);

        Button chooser = neutralButton("Woche auswählen  ▾");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(170), dp(42));
        clp.setMargins(0, 0, 0, 0);
        chooser.setLayoutParams(clp);
        chooser.setTextSize(15);
        chooser.setOnClickListener(v -> showWeekPickerDialog(clamp(initial, 0, Math.max(0, activeWeeks() - 1)), callback));
        box.addView(chooser);
        return box;
    }

    private void showWeekPickerDialog(int initial, final WeekSelection callback) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(18), dp(18), dp(18));
        sheet.setBackground(cardBackground());
        sheet.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("Woche auswählen");
        title.setTextColor(ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        sheet.addView(title);

        TextView subtitle = smallText("Wähle die Woche, die du anzeigen möchtest.");
        subtitle.setPadding(0, dp(6), 0, dp(14));
        sheet.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < activeWeeks(); i++) {
            final int week = i;
            Button item = new Button(this);
            item.setAllCaps(false);
            item.setText("Woche " + (i + 1));
            item.setTextSize(15);
            boolean active = (i == initial);
            item.setTextColor(Color.WHITE);
            item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setBackground(tabBackground(active));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            ilp.setMargins(0, 0, 0, dp(8));
            item.setLayoutParams(ilp);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onSelect(week);
            });
            list.addView(item);
        }

        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button cancel = softButton("Abbrechen");
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        clp2.setMargins(0, dp(10), 0, 0);
        cancel.setLayoutParams(clp2);
        sheet.addView(cancel);

        outer.addView(sheet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setView(outer);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new GradientDrawable());
        }
    }

    private View weekSpinner(String labelText, int initial, final WeekSelection callback) {
        LinearLayout box = card();
        Spinner spinner = new Spinner(this);
        String[] items = new String[activeWeeks()];
        for (int i = 0; i < activeWeeks(); i++) items[i] = "Woche " + (i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(clamp(initial, 0, activeWeeks() - 1));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) {
                    first = false;
                    return;
                }
                callback.onSelect(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinner.setBackground(inputBackground());
        spinner.setPadding(dp(10), 0, dp(10), 0);
        box.addView(spinner);
        return box;
    }

    private interface WeekSelection { void onSelect(int week); }
    private interface DurationSelection { void onSelect(int weeks); }

    private void showDurationPickerDialog(int initial, final DurationSelection callback) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(18), dp(18), dp(18));
        sheet.setBackground(cardBackground());
        sheet.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("Challenge-Dauer");
        title.setTextColor(ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        sheet.addView(title);

        TextView subtitle = smallText("Wische nach oben oder unten, um die Wochenzahl auszuwählen.");
        subtitle.setPadding(0, dp(6), 0, dp(14));
        sheet.addView(subtitle);

        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(WEEKS);
        picker.setValue(clamp(initial, 1, WEEKS));
        picker.setWrapSelectorWheel(false);
        picker.setBackground(inputBackground());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        plp.setMargins(0, 0, 0, dp(12));
        sheet.addView(picker, plp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = softButton("Abbrechen");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1f));
        actions.addView(space(dp(8)), new LinearLayout.LayoutParams(dp(8), 1));
        Button apply = primaryButton("Übernehmen");
        apply.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onSelect(picker.getValue());
        });
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(48), 1f));
        sheet.addView(actions);

        outer.addView(sheet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setView(outer);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new GradientDrawable());
        }
    }

    private interface AfterDatePicked { void onDone(); }

    private void pickStartDate(AfterDatePicked afterDatePicked) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(data.startDateMillis);
        DatePickerDialog dlg = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, year);
            chosen.set(Calendar.MONTH, month);
            chosen.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            chosen.set(Calendar.HOUR_OF_DAY, 0);
            chosen.set(Calendar.MINUTE, 0);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            data.startDateMillis = chosen.getTimeInMillis();
            saveData();
            toast("Startdatum gespeichert");
            afterDatePicked.onDone();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private void exportJson() {
        saveData();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, exportFileName());
        startActivityForResult(intent, REQ_EXPORT);
    }

    private String exportFileName() {
        String userId = data != null && data.userId != null ? data.userId.trim() : "";
        userId = userId.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (userId.isEmpty()) userId = "unknown";
        return "habit_tracker_" + userId + ".json";
    }

    private void importJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode != RESULT_OK || resultData == null || resultData.getData() == null) return;
        Uri uri = resultData.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) os.write(data.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
                }
                toast("Export gespeichert");
            } else if (requestCode == REQ_IMPORT) {
                String json = readUri(uri);
                data = HabitData.fromJson(json);
                saveData();
                selectedWeek = getCurrentWeek();
                toast("Import erfolgreich");
                if (!data.configured || activeHabitCount() == 0) showSetup(false);
                else showMainSection(SECTION_OVERVIEW);
            }
        } catch (Exception e) {
            toast("Fehler: " + e.getMessage());
        }
    }

    private String readUri(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void sendChatMessage(String message) {
        if (!data.syncEnabled || firestore == null || auth == null) {
            toast("Chat-Sync nicht bereit.");
            return;
        }
        if (auth.getCurrentUser() == null) {
            ensureSignedInAndSync(false);
            toast("Sync wird vorbereitet. Bitte gleich nochmal senden.");
            return;
        }
        String group = sanitizedGroupCode();
        if (group.isEmpty()) {
            toast("Gruppen-Code fehlt.");
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", auth.getCurrentUser().getUid());
        payload.put("displayName", safe(data.displayName, "Unbenannt"));
        payload.put("text", message);
        payload.put("createdAtClient", System.currentTimeMillis());
        payload.put("createdAt", FieldValue.serverTimestamp());
        firestore.collection("challenges")
                .document(group)
                .collection("messages")
                .add(payload)
                .addOnSuccessListener(unused -> startChatListener())
                .addOnFailureListener(e -> toast("Chat-Fehler: " + e.getMessage()));
    }

    private void startChatListener() {
        if (firestore == null || !data.syncEnabled || sanitizedGroupCode().isEmpty()) return;
        String group = sanitizedGroupCode();
        if (group.equals(listeningChatGroup) && chatRegistration != null) return;
        stopChatListener();
        listeningChatGroup = group;
        chatRegistration = firestore.collection("challenges")
                .document(group)
                .collection("messages")
                .orderBy("createdAtClient")
                .limit(80)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        if (SECTION_CHAT.equals(currentSection)) toast("Chat-Update Fehler: " + e.getMessage());
                        return;
                    }
                    chatMessages.clear();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            chatMessages.add(ChatMessage.from(doc));
                        }
                    }
                    if (SECTION_CHAT.equals(currentSection)) renderChat();
                });
    }

    private void stopChatListener() {
        if (chatRegistration != null) {
            chatRegistration.remove();
            chatRegistration = null;
        }
        listeningChatGroup = "";
        chatMessages.clear();
    }

    private void setupFirebase() {
        try {
            String projectId = getString(R.string.firebase_project_id).trim();
            String appId = getString(R.string.firebase_application_id).trim();
            String apiKey = getString(R.string.firebase_api_key).trim();
            if (projectId.startsWith("PASTE_") || appId.startsWith("PASTE_") || apiKey.startsWith("PASTE_") || projectId.isEmpty() || appId.isEmpty() || apiKey.isEmpty()) {
                firebaseStatus = "Firebase-Konfiguration fehlt";
                return;
            }
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .build();
                FirebaseApp.initializeApp(this, options);
            }
            auth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
            firebaseStatus = "Firebase bereit";
        } catch (Exception e) {
            firebaseStatus = "Firebase-Fehler: " + e.getMessage();
        }
    }

    private void ensureSignedInAndSync(boolean showToast) {
        if (!data.syncEnabled) return;
        if (firestore == null || auth == null) {
            firebaseStatus = "Firebase nicht verbunden";
            if (showToast) toast(firebaseStatus);
            return;
        }
        if (sanitizedGroupCode().isEmpty() || data.displayName.trim().isEmpty()) {
            firebaseStatus = "Name oder Gruppen-Code fehlt";
            if (showToast) toast(firebaseStatus);
            return;
        }
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            pushOwnData(showToast);
            startTribeListener();
            if (SECTION_CHAT.equals(currentSection)) startChatListener();
            return;
        }
        firebaseStatus = "Anonyme Anmeldung läuft…";
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    firebaseStatus = "Angemeldet · Sync aktiv";
                    pushOwnData(showToast);
                    startTribeListener();
                    if (SECTION_CHAT.equals(currentSection)) startChatListener();
                })
                .addOnFailureListener(e -> {
                    firebaseStatus = "Login fehlgeschlagen: " + e.getMessage();
                    if (showToast) toast(firebaseStatus);
                    if (SECTION_TRIBE.equals(currentSection)) renderTribe();
                });
    }

    private void pushOwnData(boolean showToast) {
        if (firestore == null || auth == null || auth.getCurrentUser() == null || !data.syncEnabled) return;
        String group = sanitizedGroupCode();
        if (group.isEmpty()) return;
        String firebaseUid = auth.getCurrentUser().getUid();
        String selfId = data.userId != null && !data.userId.trim().isEmpty() ? data.userId.trim() : firebaseUid;
        int today = dayIndexForToday();
        int week = getCurrentWeek();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", selfId);
        payload.put("displayName", safe(data.displayName, "Unbenannt"));
        payload.put("groupCode", group);
        payload.put("updatedAt", FieldValue.serverTimestamp());
        payload.put("updatedAtClient", System.currentTimeMillis());
        payload.put("todayDone", dayDone(today));
        payload.put("todayDenom", dayDenom(today));
        payload.put("weekPercent", percentInt(weekDone(week), weekDenom(week)));
        payload.put("overallPercent", percentInt(totalDone(), totalDenom()));
        payload.put("streak", getStreak());
        payload.put("weekGoals", weekGoalsReached(week));
        payload.put("activeHabits", activeHabitCount());
        payload.put("privacyMode", data.privacyMode);
        payload.put("appVersion", 3);

        if (data.privacyMode == 1) {
            List<Map<String, Object>> details = new ArrayList<>();
            for (int h = 0; h < HABITS; h++) {
                if (!isHabitActive(h)) continue;
                Map<String, Object> item = new HashMap<>();
                item.put("name", data.habitNames[h]);
                item.put("state", data.states[today][h]);
                item.put("stateText", stateText(data.states[today][h]));
                details.add(item);
            }
            payload.put("todayDetails", details);
        }

        firestore.collection("challenges")
                .document(group)
                .collection("participants")
                .document(selfId)
                .set(payload)
                .addOnSuccessListener(unused -> {
                    firebaseStatus = "Synchronisiert";
                    if (showToast) toast("Synchronisiert");
                    if (SECTION_TRIBE.equals(currentSection)) renderTribe();
                })
                .addOnFailureListener(e -> {
                    firebaseStatus = "Sync-Fehler: " + e.getMessage();
                    if (showToast) toast(firebaseStatus);
                    if (SECTION_TRIBE.equals(currentSection)) renderTribe();
                });

        // Alt-Dokument aufräumen, das frühere Versionen unter der Firebase-UID angelegt haben.
        if (!firebaseUid.equals(selfId)) {
            firestore.collection("challenges")
                    .document(group)
                    .collection("participants")
                    .document(firebaseUid)
                    .delete();
        }
    }

    private void startTribeListener() {
        if (firestore == null || !data.syncEnabled || sanitizedGroupCode().isEmpty()) return;
        String group = sanitizedGroupCode();
        if (group.equals(listeningGroup) && tribeRegistration != null) return;
        stopTribeListener();
        listeningGroup = group;
        tribeRegistration = firestore.collection("challenges")
                .document(group)
                .collection("participants")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        firebaseStatus = "Live-Update Fehler: " + e.getMessage();
                        if (SECTION_TRIBE.equals(currentSection)) renderTribe();
                        return;
                    }
                    tribeMembers.clear();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            tribeMembers.add(TribeMember.from(doc));
                        }
                    }
                    firebaseStatus = "Live verbunden";
                    if (SECTION_TRIBE.equals(currentSection)) renderTribe();
                });
    }

    private void stopTribeListener() {
        if (tribeRegistration != null) {
            tribeRegistration.remove();
            tribeRegistration = null;
        }
        listeningGroup = "";
        tribeMembers.clear();
    }

    private String sanitizedGroupCode() {
        if (data == null || data.groupCode == null) return "";
        return data.groupCode.trim().toLowerCase(Locale.ROOT).replace("/", "-");
    }

    private String timeAgo(long timestamp) {
        if (timestamp <= 0) return "unbekannt";
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long minutes = diff / 60000L;
        if (minutes < 1) return "gerade eben";
        if (minutes < 60) return "vor " + minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return "vor " + hours + " h";
        long days = hours / 24;
        return "vor " + days + " Tagen";
    }

    private long dayKeyOf(long millis) {
        if (millis <= 0) return 0;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String chatDateLabel(long millis) {
        if (millis <= 0) return "Früher";
        long day = dayKeyOf(millis);
        long today = todayMidnight();
        long oneDay = 24L * 60L * 60L * 1000L;
        if (day == today) return "Heute";
        if (day == today - oneDay) return "Gestern";
        return formatDate(millis);
    }

    private View chatDateSeparator(long millis) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(12));
        wrap.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(chatDateLabel(millis));
        t.setTextColor(Color.rgb(184, 194, 206));
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(13), dp(5), dp(13), dp(5));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(55, 255, 255, 255));
        gd.setCornerRadius(dp(13));
        t.setBackground(gd);
        wrap.addView(t);
        return wrap;
    }

    private void saveData() {
        if (data != null && (data.userId == null || data.userId.trim().isEmpty())) data.userId = generateUserId();
        prefs.edit().putString("data", data.toJson().toString()).apply();
        pushUserBackup();
        if (data != null && data.configured && data.syncEnabled) {
            ensureSignedInAndSync(false);
        }
    }

    private void pushUserBackup() {
        if (firestore == null || data == null || data.userId == null || data.userId.trim().isEmpty()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", data.userId);
        payload.put("dataJson", data.toJson().toString());
        payload.put("updatedAt", FieldValue.serverTimestamp());
        payload.put("updatedAtClient", System.currentTimeMillis());
        firestore.collection("users").document(data.userId).set(payload);
    }

    private static String generateUserId() {
        String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return "HC-" + raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
    }

    private int activeHabitCount() {
        int count = 0;
        for (int i = 0; i < HABITS; i++) if (isHabitActive(i)) count++;
        return count;
    }

    private boolean isHabitActive(int h) {
        return data.habitNames[h] != null && !data.habitNames[h].trim().isEmpty();
    }

    private int dayDone(int d) {
        int n = 0;
        for (int h = 0; h < HABITS; h++) if (isHabitActive(h) && data.states[d][h] == 1) n++;
        return n;
    }

    private int dayDenom(int d) {
        int n = 0;
        for (int h = 0; h < HABITS; h++) if (isHabitActive(h) && (data.states[d][h] == 1 || data.states[d][h] == 2)) n++;
        return n;
    }

    private int habitWeekDone(int w, int h) {
        int n = 0;
        for (int d = w * 7; d < w * 7 + 7; d++) if (data.states[d][h] == 1) n++;
        return n;
    }

    private int habitWeekDenom(int w, int h) {
        int n = 0;
        for (int d = w * 7; d < w * 7 + 7; d++) if (data.states[d][h] == 1 || data.states[d][h] == 2) n++;
        return n;
    }

    private boolean habitWeekFullyRated(int w, int h) {
        for (int d = w * 7; d < w * 7 + 7; d++) {
            if (data.states[d][h] == 0) return false;
        }
        return true;
    }

    private boolean habitWeekAllX(int w, int h) {
        for (int d = w * 7; d < w * 7 + 7; d++) {
            if (data.states[d][h] != 2) return false;
        }
        return true;
    }

    private int weekDone(int w) {
        int n = 0;
        for (int d = w * 7; d < w * 7 + 7; d++) n += dayDone(d);
        return n;
    }

    private int weekDenom(int w) {
        int n = 0;
        for (int d = w * 7; d < w * 7 + 7; d++) n += dayDenom(d);
        return n;
    }

    private int weekGoalsReached(int w) {
        int n = 0;
        for (int h = 0; h < HABITS; h++) {
            if (!isHabitActive(h)) continue;
            if (data.targets[h] <= 0 || habitWeekDone(w, h) >= data.targets[h]) n++;
        }
        return n;
    }

    private int totalDone() {
        int n = 0;
        for (int d = 0; d < activeDays(); d++) n += dayDone(d);
        return n;
    }

    private int totalDenom() {
        int n = 0;
        for (int d = 0; d < activeDays(); d++) n += dayDenom(d);
        return n;
    }

    private int getStreak() {
        int today = dayIndexForToday();
        int streak = 0;
        for (int d = today; d >= 0; d--) {
            int denom = dayDenom(d);
            if (denom == 0) break;
            if (dayDone(d) == denom) streak++;
            else break;
        }
        return streak;
    }

    private int dayIndexForToday() {
        long diff = todayMidnight() - data.startDateMillis;
        int day = (int) (diff / (24L * 60L * 60L * 1000L));
        return clamp(day, 0, activeDays() - 1);
    }

    private int getCurrentWeek() {
        return clamp(dayIndexForToday() / 7, 0, activeWeeks() - 1);
    }

    private int activeWeeks() {
        if (data == null) return 10;
        return clamp(data.durationWeeks, 1, WEEKS);
    }

    private int activeDays() {
        return activeWeeks() * 7;
    }

    private long getDateForDay(int day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(data.startDateMillis);
        c.add(Calendar.DAY_OF_YEAR, day);
        return c.getTimeInMillis();
    }

    private String formatDate(long millis) {
        return dateFormat.format(millis);
    }

    private static long todayMidnight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String stateText(int state) {
        if (state == 1) return "✓";
        if (state == 2) return "✗";
        if (state == 3) return "–";
        return "leer";
    }

    private int stateColor(int state) {
        if (state == 1) return green;
        if (state == 2) return red;
        if (state == 3) return yellow;
        return Color.rgb(130, 136, 144);
    }

    private String percentText(int done, int denom) {
        if (denom <= 0) return "–";
        return Math.round((done * 100f) / denom) + "%";
    }

    private int percentInt(int done, int denom) {
        if (denom <= 0) return 0;
        return Math.round((done * 100f) / denom);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(18), dp(18), dp(18));
        l.setBackground(cardBackground());
        l.setElevation(dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout emptyStateCard(int iconRes, String title, String subtitle) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(24), dp(34), dp(24), dp(30));

        FrameLayout iconWrap = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(26, 255, 255, 255));
        circle.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        iconWrap.setBackground(circle);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(150, 160, 173));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(34), dp(34));
        ilp.gravity = Gravity.CENTER;
        iconWrap.addView(icon, ilp);
        LinearLayout.LayoutParams iwlp = new LinearLayout.LayoutParams(dp(74), dp(74));
        iwlp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(iconWrap, iwlp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(ink);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, dp(16), 0, 0);
        c.addView(t, tlp);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(muted);
        s.setTextSize(13);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, dp(6), 0, 0);
        c.addView(s, slp);
        return c;
    }

    private LinearLayout heroCard() {
        LinearLayout l = card();
        l.setBackground(heroBackground());
        return l;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(27, 31, 38), Color.rgb(19, 22, 28)});
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(1), Color.argb(48, 255, 255, 255));
        return gd;
    }

    private GradientDrawable heroBackground() {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(31, 45, 61), Color.rgb(18, 24, 33)});
        gd.setCornerRadius(dp(32));
        gd.setStroke(dp(1), Color.argb(78, 205, 223, 255));
        return gd;
    }

    private GradientDrawable podiumCardBackground(int medalColor) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{blend(medalColor, Color.rgb(24, 28, 35), 0.18f), Color.rgb(19, 22, 28)});
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(2), withAlpha(medalColor, 165));
        return gd;
    }

    private int blend(int a, int b, float ratio) {
        float inv = 1f - ratio;
        return Color.rgb(
                (int) (Color.red(a) * ratio + Color.red(b) * inv),
                (int) (Color.green(a) * ratio + Color.green(b) * inv),
                (int) (Color.blue(a) * ratio + Color.blue(b) * inv));
    }

    private GradientDrawable subCardBackground() {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(34, 38, 46), Color.rgb(28, 32, 39)});
        gd.setCornerRadius(dp(22));
        gd.setStroke(dp(1), Color.argb(34, 255, 255, 255));
        return gd;
    }

    private GradientDrawable chatComposerBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.TRANSPARENT);
        gd.setStroke(0, Color.TRANSPARENT);
        return gd;
    }

    private GradientDrawable chatInputBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(30, 35, 42));
        gd.setCornerRadius(dp(22));
        gd.setStroke(dp(1), Color.argb(65, 255, 255, 255));
        return gd;
    }

    private GradientDrawable chatBubbleBackground(boolean mine) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                mine
                        ? new int[]{Color.rgb(50, 102, 73), Color.rgb(38, 78, 56)}
                        : new int[]{Color.rgb(33, 38, 46), Color.rgb(27, 31, 37)});
        float r = dp(20);
        float s = dp(6);
        if (mine) gd.setCornerRadii(new float[]{r, r, r, r, s, s, r, r});
        else gd.setCornerRadii(new float[]{r, r, r, r, r, r, s, s});
        gd.setStroke(dp(1), Color.argb(mine ? 90 : 45, 255, 255, 255));
        return gd;
    }

    private GradientDrawable bottomNavBackground() {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(20, 24, 30), Color.rgb(14, 17, 22)});
        gd.setCornerRadius(dp(24));
        gd.setStroke(dp(1), Color.argb(30, 255, 255, 255));
        return gd;
    }

    private GradientDrawable tabBackground(boolean active) {
        GradientDrawable gd;
        if (active) {
            gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.rgb(88, 175, 123), Color.rgb(63, 144, 99)});
            gd.setCornerRadius(dp(22));
            gd.setStroke(dp(1), Color.argb(110, 230, 255, 236));
        } else {
            gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(40, 255, 255, 255), Color.argb(24, 255, 255, 255)});
            gd.setCornerRadius(dp(20));
            gd.setStroke(dp(1), Color.argb(24, 255, 255, 255));
        }
        return gd;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        gd.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return gd;
    }

    private android.graphics.drawable.Drawable withRipple(android.graphics.drawable.Drawable content, int radius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(80, 255, 255, 255)), content, mask);
    }

    private android.graphics.drawable.Drawable rippleRound(int color, int radius) {
        return withRipple(round(color, radius), radius);
    }

    private GradientDrawable inputBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(28, 32, 39));
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), Color.rgb(61, 68, 78));
        return gd;
    }

    private int dayHeatColor(int day) {
        int rated = 0;
        int positive = 0;
        int negative = 0;
        int skipped = 0;
        for (int h = 0; h < HABITS; h++) {
            if (!isHabitActive(h)) continue;
            int s = data.states[day][h];
            if (s != 0) rated++;
            if (s == 1) positive++;
            else if (s == 2) negative++;
            else if (s == 3) skipped++;
        }
        if (rated == 0) return Color.rgb(72, 78, 86);
        if (positive == 0 && negative > 0 && skipped == 0) return red;
        if (positive > 0 && negative == 0) return green;
        return yellow;
    }

    private RingChartView ringChart(int percent, String label) {
        RingChartView ring = new RingChartView(this, dp(10),
                Color.argb(38, 255, 255, 255), green, Color.WHITE, Color.rgb(206, 218, 230),
                dp(23), dp(11), dp(2));
        ring.setLabel(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(114), dp(114));
        ring.setLayoutParams(lp);
        final int target = clamp(percent, 0, 100);
        ring.post(() -> ring.setPercent(target, true));
        return ring;
    }

    private interface StateSelection { void onSelect(int state); }

    private LinearLayout stateSelector(int current, boolean animate, StateSelection callback) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.addView(selectorChip("✓", current == 1, animate, green, () -> callback.onSelect(1)));
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp1.setMargins(dp(6), 0, 0, 0);
        wrap.addView(selectorChip("✗", current == 2, animate, red, () -> callback.onSelect(2)), sp1);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.setMargins(dp(6), 0, 0, 0);
        wrap.addView(selectorChip("–", current == 3, animate, yellow, () -> callback.onSelect(3)), sp2);
        return wrap;
    }

    private TextView selectorChip(String label, boolean active, boolean animate, int activeColor, Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(15);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(16));
        gd.setColor(active ? activeColor : Color.rgb(55, 61, 69));
        gd.setStroke(dp(1), active ? Color.argb(120, 255, 255, 255) : Color.argb(55, 255, 255, 255));
        t.setBackground(withRipple(gd, dp(16)));
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setMinWidth(dp(46));
        if (active && animate) {
            t.setScaleX(0.6f);
            t.setScaleY(0.6f);
            t.post(() -> t.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                    .setDuration(260).start());
        }
        t.setOnClickListener(v -> {
            v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                    .start();
            action.run();
        });
        return t;
    }

    private ProgressBar progressBar(int max, int progress, int color) {
        return progressBar(max, progress, color, true);
    }

    private ProgressBar progressBar(int max, int progress, int color, boolean animate) {
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(max);
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor(Color.rgb(41, 46, 52));
        bgDrawable.setCornerRadius(dp(999));
        GradientDrawable progressDrawable = new GradientDrawable();
        progressDrawable.setColor(color);
        progressDrawable.setCornerRadius(dp(999));
        ClipDrawable clip = new ClipDrawable(progressDrawable, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{bgDrawable, clip});
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        pb.setProgressDrawable(layer);
        pb.setMinHeight(dp(8));
        final int target = clamp(progress, 0, max);
        if (animate) {
            pb.setProgress(0);
            pb.post(() -> pb.setProgress(target, true));
        } else {
            pb.setProgress(target);
        }
        return pb;
    }

    private TextView smallCaps(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setTextColor(Color.rgb(171, 196, 226));
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView bigValue(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ink);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView animatedPercent(int done, int denom) {
        if (denom <= 0) return bigValue("–");
        final TextView t = bigValue("0%");
        final int target = percentInt(done, denom);
        t.post(() -> {
            ValueAnimator va = ValueAnimator.ofInt(0, target);
            va.setDuration(900);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(a -> t.setText(a.getAnimatedValue() + "%"));
            va.start();
        });
        return t;
    }

    private LinearLayout metricPill(String title, String value) {
        return metricPill(title, value, 0, 0);
    }

    private LinearLayout metricPill(String title, String value, int accent, int iconRes) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(13), dp(12), dp(13), dp(12));
        if (accent != 0) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(withAlpha(accent, 30));
            gd.setCornerRadius(dp(22));
            gd.setStroke(dp(1), withAlpha(accent, 95));
            l.setBackground(gd);
        } else {
            l.setBackground(subCardBackground());
        }

        TextView top = new TextView(this);
        top.setText(title);
        top.setTextColor(accent != 0 ? withAlpha(accent, 220) : muted);
        top.setTextSize(12);
        l.addView(top);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setPadding(0, dp(4), 0, 0);
        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setColorFilter(accent != 0 ? accent : ink);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(16), dp(16));
            ilp.setMargins(0, 0, dp(5), 0);
            valueRow.addView(icon, ilp);
        }
        TextView bottom = new TextView(this);
        bottom.setText(value);
        bottom.setTextColor(accent != 0 ? accent : ink);
        bottom.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.setTextSize(18);
        valueRow.addView(bottom);
        l.addView(valueRow);
        return l;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView pillText(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(11), dp(6), dp(11), dp(6));
        t.setBackground(round(color, dp(16)));
        return t;
    }

    private Button chipButton(String textValue, int color) {
        Button b = new Button(this);
        b.setText(textValue);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(color, dp(19)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), dp(40));
        b.setLayoutParams(lp);
        return b;
    }

    private int tribeAccentColor(String name) {
        int[] palette = new int[]{
                Color.rgb(90, 170, 255),
                Color.rgb(110, 196, 117),
                Color.rgb(255, 159, 90),
                Color.rgb(192, 126, 255),
                Color.rgb(255, 111, 145),
                Color.rgb(88, 210, 198),
                Color.rgb(232, 196, 92),
                Color.rgb(255, 122, 122)
        };
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        int idx = Math.abs(key.hashCode()) % palette.length;
        return palette[idx];
    }

    private TextView initialCircle(String name, int color) {
        TextView t = new TextView(this);
        String trimmed = name == null ? "?" : name.trim();
        String initial = trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
        t.setText(initial);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(17);
        t.setBackground(round(color, dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        t.setLayoutParams(lp);
        return t;
    }

    private View space(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return v;
    }

    private View legendItem(int color, String text) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), dp(16), dp(2));
        l.setLayoutParams(lp);

        View dot = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        dot.setBackground(gd);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(11), dp(11));
        dlp.setMargins(0, 0, dp(7), 0);
        l.addView(dot, dlp);

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(muted);
        t.setTextSize(12);
        l.addView(t);
        return l;
    }

    private void addSectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(ink);
        t.setGravity(Gravity.START);
        t.setPadding(dp(2), dp(10), dp(2), dp(14));
        content.addView(t);
    }

    private void addSubsectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.rgb(232, 236, 241));
        t.setGravity(Gravity.START);
        t.setPadding(dp(2), dp(6), dp(2), dp(10));
        content.addView(t);
    }

    private void animateSectionTransition() {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(10));
            child.animate().alpha(1f).translationY(0f).setDuration(240).setStartDelay(Math.min(i * 22L, 120L)).start();
        }
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(ink);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(17);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView smallText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(muted);
        t.setTextSize(13);
        t.setPadding(0, dp(3), 0, dp(5));
        return t;
    }

    private TextView bigMetric(String key, String value) {
        TextView t = new TextView(this);
        t.setText(key + ": " + value);
        t.setTextColor(ink);
        t.setTextSize(16);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setTextSize(15);
        e.setSingleLine(false);
        e.setTextColor(ink);
        e.setHintTextColor(Color.rgb(135, 140, 148));
        e.setBackground(inputBackground());
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(15);
        b.setBackground(rippleRound(green, dp(20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        b.setElevation(dp(2));
        return b;
    }

    private Button neutralButton(String label) {
        Button b = primaryButton(label);
        b.setBackground(rippleRound(darkBlue, dp(18)));
        return b;
    }

    private Button dangerButton(String label) {
        Button b = primaryButton(label);
        b.setBackground(rippleRound(red, dp(18)));
        return b;
    }

    private Button softButton(String label) {
        Button b = primaryButton(label);
        b.setTextColor(ink);
        b.setBackground(rippleRound(Color.rgb(56, 61, 69), dp(18)));
        return b;
    }

    private void addView(View v) { content.addView(v); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String safe(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s.trim(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public static class RingChartView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private final float strokeWidth;
        private final float inset;
        private float animatedPercent = 0f;
        private String label = "";
        private ValueAnimator animator;

        RingChartView(Context context, float strokeWidth, int trackColor, int arcColor,
                      int valueColor, int labelColor, float valueTextSize, float labelTextSize, float inset) {
            super(context);
            this.strokeWidth = strokeWidth;
            this.inset = inset;
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(strokeWidth);
            trackPaint.setColor(trackColor);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(strokeWidth);
            arcPaint.setColor(arcColor);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            valuePaint.setColor(valueColor);
            valuePaint.setTextAlign(Paint.Align.CENTER);
            valuePaint.setTextSize(valueTextSize);
            valuePaint.setFakeBoldText(true);
            labelPaint.setColor(labelColor);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(labelTextSize);
            labelPaint.setFakeBoldText(true);
        }

        void setLabel(String label) {
            this.label = label == null ? "" : label;
        }

        void setPercent(int percent, boolean animate) {
            final float target = Math.max(0, Math.min(100, percent));
            if (animator != null) animator.cancel();
            if (animate) {
                animator = ValueAnimator.ofFloat(animatedPercent, target);
                animator.setDuration(950);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(a -> {
                    animatedPercent = (float) a.getAnimatedValue();
                    invalidate();
                });
                animator.start();
            } else {
                animatedPercent = target;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = strokeWidth / 2f + inset;
            arcRect.set(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawArc(arcRect, 0, 360, false, trackPaint);
            float sweep = 360f * (animatedPercent / 100f);
            canvas.drawArc(arcRect, -90, sweep, false, arcPaint);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            String value = Math.round(animatedPercent) + "%";
            boolean hasLabel = !label.isEmpty();
            Paint.FontMetrics vm = valuePaint.getFontMetrics();
            float valueY = cy - (vm.ascent + vm.descent) / 2f;
            if (hasLabel) valueY -= (labelPaint.getTextSize() * 0.7f);
            canvas.drawText(value, cx, valueY, valuePaint);
            if (hasLabel) {
                canvas.drawText(label.toUpperCase(Locale.ROOT), cx, valueY + labelPaint.getTextSize() * 1.7f, labelPaint);
            }
        }
    }

    public static class ChatMessage {
        String userId = "";
        String displayName = "Unbenannt";
        String text = "";
        long createdAtClient = 0;

        static ChatMessage from(DocumentSnapshot doc) {
            ChatMessage m = new ChatMessage();
            Object v = doc.get("userId"); if (v != null) m.userId = String.valueOf(v);
            v = doc.get("displayName"); if (v != null) m.displayName = String.valueOf(v);
            v = doc.get("text"); if (v != null) m.text = String.valueOf(v);
            Long l = doc.getLong("createdAtClient"); if (l != null) m.createdAtClient = l;
            return m;
        }
    }

    public static class TribeMember {
        String id = "";
        String userId = "";
        String displayName = "Unbenannt";
        int todayDone = 0;
        int todayDenom = 0;
        int weekPercent = 0;
        int overallPercent = 0;
        int streak = 0;
        int weekGoals = 0;
        int activeHabits = 0;
        long updatedAtClient = 0;
        String details = "";

        static TribeMember from(DocumentSnapshot doc) {
            TribeMember m = new TribeMember();
            m.id = doc.getId();
            String uid = doc.getString("userId");
            if (uid != null) m.userId = uid.trim();
            String name = doc.getString("displayName");
            if (name != null && !name.trim().isEmpty()) m.displayName = name.trim();
            Long l;
            l = doc.getLong("todayDone"); if (l != null) m.todayDone = l.intValue();
            l = doc.getLong("todayDenom"); if (l != null) m.todayDenom = l.intValue();
            l = doc.getLong("weekPercent"); if (l != null) m.weekPercent = l.intValue();
            l = doc.getLong("overallPercent"); if (l != null) m.overallPercent = l.intValue();
            l = doc.getLong("streak"); if (l != null) m.streak = l.intValue();
            l = doc.getLong("weekGoals"); if (l != null) m.weekGoals = l.intValue();
            l = doc.getLong("activeHabits"); if (l != null) m.activeHabits = l.intValue();
            l = doc.getLong("updatedAtClient"); if (l != null) m.updatedAtClient = l;
            Object detailsObj = doc.get("todayDetails");
            if (detailsObj instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object obj : (List<?>) detailsObj) {
                    if (obj instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) obj;
                        Object n = map.get("name");
                        Object st = map.get("stateText");
                        if (n != null && st != null) {
                            if (sb.length() > 0) sb.append(" · ");
                            sb.append(n).append(": ").append(st);
                        }
                    }
                }
                m.details = sb.toString();
            }
            return m;
        }
    }

    public static class HabitData {
        boolean configured = false;
        boolean syncEnabled = false;
        int storageMode = STORAGE_MODE_UNSET;
        boolean privacyAccepted = false;
        String displayName = "";
        String groupCode = "";
        int privacyMode = 1;
        int durationWeeks = 10;
        String userId = generateUserId();
        long startDateMillis = todayMidnight();
        String[] habitNames = new String[HABITS];
        int[] targets = new int[HABITS];
        int[][] states = new int[DAYS][HABITS];
        String[][] reflections = new String[WEEKS][4];

        HabitData() {
            for (int i = 0; i < HABITS; i++) {
                habitNames[i] = "";
                targets[i] = 5;
            }
            for (int w = 0; w < WEEKS; w++) for (int i = 0; i < 4; i++) reflections[w][i] = "";
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("version", 3);
                o.put("configured", configured);
                o.put("syncEnabled", syncEnabled);
                o.put("storageMode", storageMode);
                o.put("privacyAccepted", privacyAccepted);
                o.put("displayName", displayName);
                o.put("groupCode", groupCode);
                o.put("privacyMode", privacyMode);
                o.put("durationWeeks", durationWeeks);
                o.put("userId", userId);
                o.put("startDateMillis", startDateMillis);
                JSONArray h = new JSONArray();
                JSONArray t = new JSONArray();
                for (int i = 0; i < HABITS; i++) {
                    h.put(habitNames[i]);
                    t.put(targets[i]);
                }
                o.put("habitNames", h);
                o.put("targets", t);
                JSONArray days = new JSONArray();
                for (int d = 0; d < DAYS; d++) {
                    JSONArray row = new JSONArray();
                    for (int i = 0; i < HABITS; i++) row.put(states[d][i]);
                    days.put(row);
                }
                o.put("states", days);
                JSONArray ci = new JSONArray();
                for (int w = 0; w < WEEKS; w++) {
                    JSONArray row = new JSONArray();
                    for (int i = 0; i < 4; i++) row.put(reflections[w][i]);
                    ci.put(row);
                }
                o.put("reflections", ci);
            } catch (JSONException ignored) { }
            return o;
        }

        static HabitData fromJson(String json) {
            HabitData d = new HabitData();
            if (json == null || json.trim().isEmpty()) return d;
            try {
                JSONObject o = new JSONObject(json);
                d.configured = o.optBoolean("configured", false);
                d.syncEnabled = o.optBoolean("syncEnabled", false);
                d.storageMode = o.optInt("storageMode", STORAGE_MODE_UNSET);
                d.privacyAccepted = o.optBoolean("privacyAccepted", false);
                d.displayName = o.optString("displayName", d.displayName);
                d.groupCode = o.optString("groupCode", d.groupCode);
                d.privacyMode = Math.max(0, Math.min(1, o.optInt("privacyMode", d.privacyMode)));
                d.durationWeeks = Math.max(1, Math.min(WEEKS, o.optInt("durationWeeks", d.durationWeeks)));
                d.userId = o.optString("userId", d.userId);
                if (d.userId == null || d.userId.trim().isEmpty()) d.userId = generateUserId();
                d.startDateMillis = o.optLong("startDateMillis", d.startDateMillis);
                JSONArray h = o.optJSONArray("habitNames");
                if (h != null) for (int i = 0; i < Math.min(HABITS, h.length()); i++) d.habitNames[i] = h.optString(i, d.habitNames[i]);
                JSONArray t = o.optJSONArray("targets");
                if (t != null) for (int i = 0; i < Math.min(HABITS, t.length()); i++) d.targets[i] = Math.max(0, Math.min(7, t.optInt(i, d.targets[i])));
                JSONArray statesJson = o.optJSONArray("states");
                if (statesJson != null) {
                    for (int day = 0; day < Math.min(DAYS, statesJson.length()); day++) {
                        JSONArray row = statesJson.optJSONArray(day);
                        if (row == null) continue;
                        for (int i = 0; i < Math.min(HABITS, row.length()); i++) d.states[day][i] = Math.max(0, Math.min(3, row.optInt(i, 0)));
                    }
                }

                JSONArray refl = o.optJSONArray("reflections");
                if (refl == null) refl = o.optJSONArray("checkins");
                if (refl != null) {
                    for (int w = 0; w < Math.min(WEEKS, refl.length()); w++) {
                        JSONArray row = refl.optJSONArray(w);
                        if (row == null) continue;
                        for (int i = 0; i < Math.min(4, row.length()); i++) d.reflections[w][i] = row.optString(i, "");
                    }
                }

                if (!o.has("configured")) {
                    for (int i = 0; i < HABITS; i++) {
                        if (d.habitNames[i] != null && !d.habitNames[i].trim().isEmpty()) {
                            d.configured = true;
                            break;
                        }
                    }
                }

                if (d.configured && d.storageMode == STORAGE_MODE_UNSET) {
                    d.storageMode = d.syncEnabled ? STORAGE_MODE_ONLINE : STORAGE_MODE_LOCAL;
                    if (d.storageMode == STORAGE_MODE_ONLINE) d.privacyAccepted = true;
                }
            } catch (JSONException ignored) { }
            return d;
        }
    }
}
