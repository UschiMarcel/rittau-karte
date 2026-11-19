package de.rittau.karte;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import java.nio.charset.StandardCharsets;

/** Die Visitenkarte: Daten, Speicherung und das NDEF-Format, das per NFC rausgeht. */
public final class Card {

    public static final String MODE_CONTACT = "contact";
    public static final String MODE_LINK = "link";
    public static final String MODE_INSTAGRAM = "instagram";
    public static final String MODE_CV = "cv";
    public static final String DEFAULT_LINKEDIN = "https://www.linkedin.com/in/marcel-rittau-4a46b4281/";
    /** Wird hochgezählt, wenn neue Standardwerte auch bei bestehenden Installationen ankommen sollen. */
    private static final int DEFAULTS_VERSION = 3;
    public static final String DEFAULT_EMAIL = "Rittau.Marcel@gmail.com";
    public static final String DEFAULT_PHONE = "+49 160 96211952";
    public static final String DEFAULT_CV =
            "https://uschimarcel.github.io/Portfolio/dateien/Lebenslauf_Marcel_Rittau.pdf";

    private static final String PREFS = "karte";

    public String name;
    public String role;
    public String company;
    public String unit;
    public String email;
    public String phone;
    public String web;
    public String city;
    public String instagram;
    public String linkedin;
    public String cv;
    public String mode;

    public static Card load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Card k = new Card();
        k.name = p.getString("name", "Marcel Rittau");
        k.role = p.getString("role", "Grafikdesigner");
        k.company = p.getString("company", "nexum");
        k.unit = p.getString("unit", "Unit Content");
        k.email = p.getString("email", DEFAULT_EMAIL);
        k.phone = p.getString("phone", DEFAULT_PHONE);
        if (p.getInt("defaults", 1) < 3) {
            if (k.email.trim().isEmpty()) k.email = DEFAULT_EMAIL;
            if (k.phone.trim().isEmpty()) k.phone = DEFAULT_PHONE;
        }
        k.web = p.getString("web", "https://uschimarcel.github.io/Portfolio/");
        k.city = p.getString("city", "Köln");
        k.instagram = p.getString("instagram", "");
        k.linkedin = p.getString("linkedin", DEFAULT_LINKEDIN);
        if (p.getInt("defaults", 1) < 2 && k.linkedin.trim().isEmpty()) k.linkedin = DEFAULT_LINKEDIN;
        k.cv = p.getString("cv", DEFAULT_CV);
        k.mode = p.getString("mode", MODE_CONTACT);
        return k;
    }

    public void save(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name", name)
                .putString("role", role)
                .putString("company", company)
                .putString("unit", unit)
                .putString("email", email)
                .putString("phone", phone)
                .putString("web", web)
                .putString("city", city)
                .putString("instagram", instagram)
                .putString("linkedin", linkedin)
                .putString("cv", cv)
                .putString("mode", mode)
                .putInt("defaults", DEFAULTS_VERSION)
                .apply();
    }

    public boolean hasLink() {
        return web != null && !web.trim().isEmpty();
    }

    public String linkUrl() {
        String w = web.trim();
        if (!w.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) w = "https://" + w;
        return w;
    }

    /** Anzeigeform der Website ohne https:// und Schrägstrich am Ende. */
    public String linkLabel() {
        return linkUrl().replaceFirst("(?i)^https?://", "").replaceFirst("/$", "");
    }

    // ---------- Instagram ----------

    /** Nur der Nutzername, egal ob als @name, name oder Profil-Link eingetragen. */
    public String instagramHandle() {
        String h = instagram == null ? "" : instagram.trim();
        h = h.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^(www\\.)?instagram\\.com/", "");
        h = h.replaceFirst("^@", "");
        int cut = h.indexOf('/');
        if (cut >= 0) h = h.substring(0, cut);
        cut = h.indexOf('?');
        if (cut >= 0) h = h.substring(0, cut);
        return h.trim();
    }

    public boolean hasInstagram() {
        return !instagramHandle().isEmpty();
    }

    public String instagramUrl() {
        return "https://www.instagram.com/" + instagramHandle() + "/";
    }

    // ---------- Lebenslauf ----------

    public boolean hasCv() {
        return cv != null && !cv.trim().isEmpty();
    }

    public String cvUrl() {
        String c = cv.trim();
        if (!c.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) c = "https://" + c;
        return c;
    }

    // ---------- LinkedIn ----------

    public boolean hasLinkedin() {
        return linkedin != null && !linkedin.trim().isEmpty();
    }

    /** Vollständiger Profil-Link; ein bloßer Name wird zu linkedin.com/in/name. */
    public String linkedinUrl() {
        String l = linkedin.trim();
        int q = l.indexOf('?');            // Tracking wie ?utm_source=… abschneiden
        if (q >= 0) l = l.substring(0, q);
        if (l.matches("(?i)^https?://.*")) return l;
        if (l.matches("(?i)^(www\\.)?linkedin\\.com/.*")) return "https://" + l;
        l = l.replaceFirst("^@", "").replaceFirst("(?i)^in/", "");
        return "https://www.linkedin.com/in/" + l + "/";
    }

    public String linkedinLabel() {
        return linkedinUrl().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "")
                .replaceFirst("/$", "");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }

    public String toVCard() {
        String n = name.trim();
        int i = n.lastIndexOf(' ');
        String first = i > 0 ? n.substring(0, i) : n;
        String last = i > 0 ? n.substring(i + 1) : "";

        StringBuilder b = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\n");
        b.append("N:").append(esc(last)).append(';').append(esc(first)).append(";;;\r\n");
        b.append("FN:").append(esc(n)).append("\r\n");
        if (!company.trim().isEmpty()) {
            b.append("ORG:").append(esc(company.trim()));
            if (!unit.trim().isEmpty()) b.append(';').append(esc(unit.trim()));
            b.append("\r\n");
        }
        if (!role.trim().isEmpty()) b.append("TITLE:").append(esc(role.trim())).append("\r\n");
        if (!email.trim().isEmpty()) b.append("EMAIL;TYPE=INTERNET:").append(email.trim()).append("\r\n");
        if (!phone.trim().isEmpty()) b.append("TEL;TYPE=CELL:").append(phone.trim()).append("\r\n");
        if (hasLink()) b.append("URL:").append(linkUrl()).append("\r\n");
        if (hasLinkedin()) {
            // Android zeigt LinkedIn als Website, iPhone zusätzlich als Social-Profil.
            b.append("URL:").append(linkedinUrl()).append("\r\n");
            b.append("X-SOCIALPROFILE;TYPE=linkedin:").append(linkedinUrl()).append("\r\n");
        }
        if (!city.trim().isEmpty()) b.append("ADR;TYPE=WORK:;;;").append(esc(city.trim())).append(";;;\r\n");
        b.append("END:VCARD\r\n");
        return b.toString();
    }

    /** Die komplette NDEF-Nachricht, die das andere Handy liest. */
    public byte[] ndefMessage() {
        NdefRecord record;
        if (MODE_LINK.equals(mode) && hasLink()) {
            record = NdefRecord.createUri(linkUrl());
        } else if (MODE_CV.equals(mode) && hasCv()) {
            record = NdefRecord.createUri(cvUrl());
        } else if (MODE_INSTAGRAM.equals(mode) && hasInstagram()) {
            record = NdefRecord.createUri(instagramUrl());
        } else {
            record = NdefRecord.createMime("text/vcard", toVCard().getBytes(StandardCharsets.UTF_8));
        }
        return new NdefMessage(record).toByteArray();
    }
}
