package de.rittau.karte;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements CardService.Listener {

    private Card card;
    private NfcAdapter nfc;
    private boolean reducedMotion;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView diag;
    private TextView name, statement, status, chip, hint, nfcSettings, modeContact, modeLink, modeInstagram, modeCv, editToggle;
    private LinearLayout meta, editBox;
    private ImageView shape;
    private ScrollView scroll;
    private EditText fName, fRole, fCompany, fUnit, fEmail, fPhone, fWeb, fCity, fInstagram, fLinkedin, fCv;
    private ObjectAnimator breathing;

    private final Runnable backToReady = this::updateNfcState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        card = Card.load(this);
        nfc = NfcAdapter.getDefaultAdapter(this);
        reducedMotion = Settings.Global.getFloat(getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;

        scroll = findViewById(R.id.scroll);
        name = findViewById(R.id.name);
        statement = findViewById(R.id.statement);
        meta = findViewById(R.id.meta);
        status = findViewById(R.id.status);
        diag = findViewById(R.id.diag);
        chip = findViewById(R.id.chip);
        hint = findViewById(R.id.hint);
        shape = findViewById(R.id.shape);
        nfcSettings = findViewById(R.id.nfcSettings);
        modeContact = findViewById(R.id.modeContact);
        modeLink = findViewById(R.id.modeLink);
        modeInstagram = findViewById(R.id.modeInstagram);
        modeCv = findViewById(R.id.modeCv);
        editToggle = findViewById(R.id.editToggle);
        editBox = findViewById(R.id.editBox);
        fName = findViewById(R.id.fName);
        fRole = findViewById(R.id.fRole);
        fCompany = findViewById(R.id.fCompany);
        fUnit = findViewById(R.id.fUnit);
        fEmail = findViewById(R.id.fEmail);
        fPhone = findViewById(R.id.fPhone);
        fWeb = findViewById(R.id.fWeb);
        fCity = findViewById(R.id.fCity);
        fInstagram = findViewById(R.id.fInstagram);
        fLinkedin = findViewById(R.id.fLinkedin);
        fCv = findViewById(R.id.fCv);

        setupLogo();

        modeContact.setOnClickListener(v -> setMode(Card.MODE_CONTACT));
        modeLink.setOnClickListener(v -> setMode(Card.MODE_LINK));
        modeInstagram.setOnClickListener(v -> setMode(Card.MODE_INSTAGRAM));
        modeCv.setOnClickListener(v -> setMode(Card.MODE_CV));
        findViewById(R.id.shareCv).setOnClickListener(v -> shareCv());
        nfcSettings.setOnClickListener(v -> openNfcSettings());
        editToggle.setOnClickListener(v -> openEditor());
        findViewById(R.id.cancel).setOnClickListener(v -> closeEditor());
        findViewById(R.id.save).setOnClickListener(v -> saveEditor());

        bindCard();

        ((TextView) findViewById(R.id.version)).setText("Version " + BuildConfig.VERSION_NAME);
        findViewById(R.id.updateBtn).setOnClickListener(v -> openDownload());
        checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CardService.listener = this;
        if (nfc != null) {
            try {
                CardEmulation.getInstance(nfc).setPreferredService(this,
                        new ComponentName(this, CardService.class));
            } catch (Exception ignored) {
                // Ohne bevorzugten Dienst funktioniert es meist trotzdem.
            }
        }
        updateNfcState();
        showDiag();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CardService.listener = null;
        handler.removeCallbacks(backToReady);
        if (nfc != null) {
            try {
                CardEmulation.getInstance(nfc).unsetPreferredService(this);
            } catch (Exception ignored) {
            }
        }
        stopBreathing();
    }

    // ---------- Anzeige ----------

    private void setupLogo() {
        ImageView logo = findViewById(R.id.logo);
        // Das Logo kommt als Datei res/drawable-nodpi/logo.png ins Projekt (macht der Build-Workflow).
        int id = getResources().getIdentifier("logo", "drawable", getPackageName());
        if (id != 0) {
            logo.setImageResource(id);
        } else {
            logo.setVisibility(View.GONE);
            findViewById(R.id.logoPlaceholder).setVisibility(View.VISIBLE);
        }
    }

    private void bindCard() {
        name.setText(card.name.trim().isEmpty() ? "Platzhalter" : card.name.trim() + ".");

        int ink = getColor(R.color.ink);
        int ink3 = getColor(R.color.ink_3);
        SpannableStringBuilder s = new SpannableStringBuilder();
        String role = card.role.trim();
        String company = card.company.trim();
        String first = role.isEmpty() ? company : (company.isEmpty() ? role : role + " bei " + company);
        if (!first.isEmpty()) {
            s.append(first).append(".");
            s.setSpan(new ForegroundColorSpan(ink), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String second = join(" · ", card.unit.trim(), card.city.trim());
        if (!second.isEmpty()) {
            if (s.length() > 0) s.append(" ");
            int start = s.length();
            s.append(second).append(".");
            s.setSpan(new ForegroundColorSpan(ink3), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        statement.setText(s);
        statement.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);

        meta.removeAllViews();
        meta.addView(line(R.color.ink));
        boolean any = false;
        if (!card.email.trim().isEmpty()) {
            any |= addRow("E-Mail", card.email.trim(),
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + card.email.trim())));
        }
        if (!card.phone.trim().isEmpty()) {
            any |= addRow("Telefon", card.phone.trim(),
                    new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + card.phone.trim())));
        }
        if (card.hasLink()) {
            any |= addRow("Web", card.linkLabel(),
                    new Intent(Intent.ACTION_VIEW, Uri.parse(card.linkUrl())));
        }
        if (card.hasLinkedin()) {
            any |= addRow("LinkedIn", card.linkedinLabel(),
                    new Intent(Intent.ACTION_VIEW, Uri.parse(card.linkedinUrl())));
        }
        if (card.hasInstagram()) {
            any |= addRow("Instagram", "@" + card.instagramHandle(),
                    new Intent(Intent.ACTION_VIEW, Uri.parse(card.instagramUrl())));
        }
        if (card.hasCv()) {
            any |= addRow("Lebenslauf", "PDF ansehen",
                    new Intent(Intent.ACTION_VIEW, Uri.parse(card.cvUrl())));
        }
        if (!card.company.trim().isEmpty()) {
            any |= addRow("Firma", join(" – ", card.company.trim(), card.unit.trim()), null);
        }
        if (!any) addRow("Kontakt", "Noch keine Daten – unten eintragen.", null);
        meta.removeViewAt(meta.getChildCount() - 1); // letzte Haarlinie
        meta.addView(line(R.color.ink));

        updateModeUi();
    }

    private boolean addRow(String key, String value, Intent action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        TextView k = new TextView(this);
        k.setText(key.toUpperCase());
        k.setTypeface(Typeface.MONOSPACE);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        k.setLetterSpacing(0.06f);
        k.setTextColor(getColor(R.color.ink_3));
        row.addView(k, new LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(this);
        v.setText(action != null ? value + "  →" : value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        v.setTextColor(getColor(R.color.ink));
        row.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (action != null) {
            row.setBackgroundResource(R.drawable.row_bg);
            row.setOnClickListener(x -> {
                try {
                    startActivity(action);
                } catch (ActivityNotFoundException e) {
                    showStatus("Dafür ist keine App installiert.", "HINWEIS");
                }
            });
        }
        meta.addView(row);
        meta.addView(line(R.color.paper_3));
        return true;
    }

    private View line(int color) {
        View l = new View(this);
        l.setBackgroundColor(getColor(color));
        l.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return l;
    }

    // ---------- Modus ----------

    private void setMode(String mode) {
        card.mode = mode;
        card.save(this);
        updateModeUi();
    }

    private void updateModeUi() {
        boolean link = Card.MODE_LINK.equals(card.mode);
        boolean insta = Card.MODE_INSTAGRAM.equals(card.mode);
        boolean cv = Card.MODE_CV.equals(card.mode);
        modeContact.setSelected(!link && !insta && !cv);
        modeLink.setSelected(link);
        modeInstagram.setSelected(insta);
        modeCv.setSelected(cv);
        findViewById(R.id.shareCv).setVisibility(card.hasCv() ? View.VISIBLE : View.GONE);
        if (cv && !card.hasCv()) {
            hint.setText("Es ist noch kein Lebenslauf-Link eingetragen. Bis dahin geht dein Kontakt raus – trag ihn unten unter „Deine Daten“ ein.");
        } else if (cv) {
            hint.setText("Das andere Handy öffnet deinen Lebenslauf als PDF – den von deinem Portfolio, ohne persönliche Angaben. Klappt mit iPhone (ab XS) und Android.");
        } else if (link && !card.hasLink()) {
            hint.setText("Es ist noch keine Website eingetragen. Bis dahin geht dein Kontakt raus – trag sie unten unter „Deine Daten“ ein.");
        } else if (link) {
            hint.setText("Das andere Handy öffnet " + card.linkLabel()
                    + ". Klappt mit iPhone (ab XS) und Android.");
        } else if (insta && !card.hasInstagram()) {
            hint.setText("Es ist noch kein Instagram eingetragen. Bis dahin geht dein Kontakt raus – trag es unten unter „Deine Daten“ ein.");
        } else if (insta) {
            hint.setText("Das andere Handy öffnet dein Instagram-Profil @" + card.instagramHandle()
                    + " – direkt in der Instagram-App, wenn sie installiert ist. Klappt mit iPhone (ab XS) und Android.");
        } else {
            hint.setText("Android-Handys bieten direkt an, dich als Kontakt zu speichern"
                    + (card.hasLinkedin() ? " – inklusive LinkedIn-Profil" : "") + ". "
                    + "iPhones zeigen eine Kontaktkarte per NFC nicht von selbst an – nimm dafür „Website“ oder „Instagram“.");
        }
    }

    /** Teilt den Link zum Lebenslauf über das Android-Teilen-Menü (WhatsApp, Mail, Quick Share …). */
    private void shareCv() {
        if (!card.hasCv()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Lebenslauf " + card.name.trim());
        send.putExtra(Intent.EXTRA_TEXT, "Hallöchen :) Hier ist mein Lebenslauf: " + card.cvUrl());
        startActivity(Intent.createChooser(send, "Lebenslauf teilen"));
    }

    // ---------- NFC ----------

    private void updateNfcState() {
        handler.removeCallbacks(backToReady);
        boolean hce = getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        if (nfc == null) {
            showStatus("Dein Handy hat kein NFC. Senden geht hier leider nicht.", "KEIN NFC");
            nfcSettings.setVisibility(View.GONE);
            stopBreathing();
        } else if (!hce) {
            showStatus("Dein Handy kann NFC lesen, aber keine Karte senden.", "NICHT MÖGLICH");
            nfcSettings.setVisibility(View.GONE);
            stopBreathing();
        } else if (!nfc.isEnabled()) {
            showStatus("NFC ist aus. Schalt es ein, dann kann’s losgehen.", "NFC AUS");
            nfcSettings.setVisibility(View.VISIBLE);
            stopBreathing();
        } else {
            showStatus("Bereit. Halt dein Handy Rücken an Rücken an ein anderes – das muss entsperrt sein und NFC an haben.", "BEREIT");
            nfcSettings.setVisibility(View.GONE);
            startBreathing();
        }
    }

    private void showStatus(String text, String chipText) {
        status.setText(text);
        chip.setText(chipText);
    }

    @Override
    public void onEvent(String text) {
        showDiag();
    }

    private void showDiag() {
        String e = CardService.lastEvent;
        if (e == null) {
            diag.setText("Letzter Kontakt: noch keiner seit dem Start");
            return;
        }
        long sec = Math.max(0, (System.currentTimeMillis() - CardService.lastEventAt) / 1000);
        String when = sec < 60 ? "vor " + sec + " s" : "vor " + (sec / 60) + " min";
        diag.setText("Letzter Kontakt (" + when + "): " + e);
    }

    @Override
    public void onSent() {
        showStatus("Gesendet. Auf dem anderen Handy ist jetzt deine Karte.", "GESENDET");
        shape.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        CardService.lastEvent = "Karte komplett gelesen.";
        CardService.lastEventAt = System.currentTimeMillis();
        showDiag();
        wobble();
        handler.removeCallbacks(backToReady);
        handler.postDelayed(backToReady, 4000);
    }

    private void openNfcSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    // ---------- Bewegung der Logo-Form ----------

    private void startBreathing() {
        if (reducedMotion || breathing != null) return;
        breathing = ObjectAnimator.ofPropertyValuesHolder(shape,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.045f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.97f));
        breathing.setDuration(1600);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.setInterpolator(new AccelerateDecelerateInterpolator());
        breathing.start();
    }

    private void stopBreathing() {
        if (breathing != null) {
            breathing.cancel();
            breathing = null;
        }
        shape.setScaleX(1f);
        shape.setScaleY(1f);
    }

    private void wobble() {
        if (reducedMotion) return;
        stopBreathing();
        shape.animate().scaleX(1.14f).scaleY(0.88f).setDuration(140)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> shape.animate().scaleX(1f).scaleY(1f).setDuration(650)
                        .setInterpolator(new OvershootInterpolator(5f)).start())
                .start();
    }

    // ---------- Bearbeiten ----------

    private void openEditor() {
        fName.setText(card.name);
        fRole.setText(card.role);
        fCompany.setText(card.company);
        fUnit.setText(card.unit);
        fEmail.setText(card.email);
        fPhone.setText(card.phone);
        fWeb.setText(card.web);
        fCity.setText(card.city);
        fInstagram.setText(card.instagram);
        fLinkedin.setText(card.linkedin);
        fCv.setText(card.cv);
        editToggle.setVisibility(View.GONE);
        editBox.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.smoothScrollTo(0, editBox.getTop()));
    }

    private void closeEditor() {
        editBox.setVisibility(View.GONE);
        editToggle.setVisibility(View.VISIBLE);
        View f = getCurrentFocus();
        if (f != null) {
            f.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    private void saveEditor() {
        String n = fName.getText().toString().trim();
        if (n.isEmpty()) {
            fName.setError("Der Name fehlt – ohne ihn weiß das andere Handy nicht, wen es speichern soll.");
            fName.requestFocus();
            return;
        }
        card.name = n;
        card.role = fRole.getText().toString().trim();
        card.company = fCompany.getText().toString().trim();
        card.unit = fUnit.getText().toString().trim();
        card.email = fEmail.getText().toString().trim();
        card.phone = fPhone.getText().toString().trim();
        card.web = fWeb.getText().toString().trim();
        card.city = fCity.getText().toString().trim();
        card.instagram = fInstagram.getText().toString().trim();
        card.linkedin = fLinkedin.getText().toString().trim();
        card.cv = fCv.getText().toString().trim();
        card.save(this);
        closeEditor();
        bindCard();
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }

    // ---------- Updates ----------

    /** Fragt bei GitHub nach, ob es einen neueren Build gibt. Ohne Internet passiert einfach nichts. */
    private void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://api.github.com/repos/" + BuildConfig.REPO + "/releases/latest");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) body.append(line);
                }
                String tag = new JSONObject(body.toString()).optString("tag_name", "");
                Matcher m = Pattern.compile("(\\d+)$").matcher(tag);
                if (!m.find()) return;
                int latest = Integer.parseInt(m.group(1));
                if (latest > BuildConfig.VERSION_CODE) {
                    runOnUiThread(() -> showUpdate(latest));
                }
            } catch (Exception ignored) {
                // Kein Netz oder GitHub nicht erreichbar – dann eben beim nächsten Start.
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private void showUpdate(int latest) {
        ((TextView) findViewById(R.id.updateText)).setText("Version 1." + latest
                + " ist da (du hast " + BuildConfig.VERSION_NAME + "). "
                + "Lade sie und installier sie einfach über die alte – deine Daten bleiben erhalten.");
        findViewById(R.id.updateBox).setVisibility(View.VISIBLE);
    }

    private void openDownload() {
        String url = "https://github.com/" + BuildConfig.REPO + "/releases/latest/download/rittau-karte.apk";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            showStatus("Kein Browser gefunden. Lade das Update hier: " + url, "HINWEIS");
        }
    }

    // ---------- Hilfen ----------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }
}
