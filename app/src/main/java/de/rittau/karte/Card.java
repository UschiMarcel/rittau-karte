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

    private static final String PREFS = "karte";

    public String name;
    public String role;
    public String company;
    public String unit;
    public String email;
    public String phone;
    public String web;
    public String city;
    public String mode;

    public static Card load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Card k = new Card();
        k.name = p.getString("name", "Marcel Rittau");
        k.role = p.getString("role", "Grafikdesigner");
        k.company = p.getString("company", "nexum");
        k.unit = p.getString("unit", "Unit Content");
        k.email = p.getString("email", "");
        k.phone = p.getString("phone", "");
        k.web = p.getString("web", "https://uschimarcel.github.io/Portfolio/");
        k.city = p.getString("city", "Köln");
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
                .putString("mode", mode)
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
        if (!city.trim().isEmpty()) b.append("ADR;TYPE=WORK:;;;").append(esc(city.trim())).append(";;;\r\n");
        b.append("END:VCARD\r\n");
        return b.toString();
    }

    /** Die komplette NDEF-Nachricht, die das andere Handy liest. */
    public byte[] ndefMessage() {
        NdefRecord record;
        if (MODE_LINK.equals(mode) && hasLink()) {
            record = NdefRecord.createUri(linkUrl());
        } else {
            record = NdefRecord.createMime("text/vcard", toVCard().getBytes(StandardCharsets.UTF_8));
        }
        return new NdefMessage(record).toByteArray();
    }
}
