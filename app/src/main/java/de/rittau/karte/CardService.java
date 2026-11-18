package de.rittau.karte;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

/**
 * Lässt das Handy wie einen NFC-Tag (NFC Forum Type 4) wirken.
 * Ein anderes Handy liest dann die Visitenkarte, als würde es einen Sticker antippen.
 */
public class CardService extends HostApduService {

    public interface Listener {
        void onSent();

        void onEvent(String text);
    }

    static volatile Listener listener;

    /** Letztes Ereignis, damit die App zeigen kann, wie weit das andere Handy gekommen ist. */
    static volatile String lastEvent = null;
    static volatile long lastEventAt = 0;

    private void log(String text) {
        lastEvent = text;
        lastEventAt = System.currentTimeMillis();
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onEvent(text);
        });
    }

    private static final byte[] NDEF_AID = {
            (byte) 0xD2, 0x76, 0x00, 0x00, (byte) 0x85, 0x01, 0x01
    };
    private static final byte[] OK = {(byte) 0x90, 0x00};
    private static final byte[] NOT_FOUND = {0x6A, (byte) 0x82};
    private static final byte[] WRONG_OFFSET = {0x6B, 0x00};
    private static final byte[] INS_NOT_SUPPORTED = {0x6D, 0x00};

    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean appSelected;
    private boolean notified;
    private byte[] cc;
    private byte[] ndef;
    private byte[] current;

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        try {
            return handle(apdu);
        } catch (Exception e) {
            log("Fehler in der App: " + e.getClass().getSimpleName());
            return new byte[]{0x6F, 0x00};
        }
    }

    private byte[] handle(byte[] apdu) {
        if (apdu == null || apdu.length < 4) return NOT_FOUND;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;

        // SELECT
        if (ins == 0xA4) {
            if (p1 == 0x04) { // Auswahl per AID
                if (apdu.length >= 5) {
                    int lc = apdu[4] & 0xFF;
                    if (lc == NDEF_AID.length && apdu.length >= 5 + lc
                            && Arrays.equals(Arrays.copyOfRange(apdu, 5, 5 + lc), NDEF_AID)) {
                        appSelected = true;
                        notified = false;
                        current = null;
                        prepare();
                        log("Anderes Handy verbunden, liest die Karte …");
                        return OK;
                    }
                }
                appSelected = false;
                log("Anderes Handy fragt etwas anderes ab (kein NFC-Tag-Leser).");
                return NOT_FOUND;
            }
            if (p1 == 0x00 && appSelected && apdu.length >= 7 && (apdu[4] & 0xFF) == 2) {
                int fid = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
                if (fid == 0xE103) { current = cc; return OK; }
                if (fid == 0xE104) { current = ndef; return OK; }
            }
            return NOT_FOUND;
        }

        // READ BINARY
        if (ins == 0xB0) {
            if (current == null) return NOT_FOUND;
            int offset = ((p1 & 0x7F) << 8) | p2;
            int le = apdu.length > 4 ? (apdu[4] & 0xFF) : 0;
            if (le == 0) le = 256;
            if (offset > current.length) return WRONG_OFFSET;
            int end = Math.min(current.length, offset + le);

            byte[] out = new byte[end - offset + 2];
            System.arraycopy(current, offset, out, 0, end - offset);
            out[out.length - 2] = (byte) 0x90;
            out[out.length - 1] = 0x00;

            if (current == ndef && end == ndef.length && end > 2 && !notified) {
                notified = true;
                main.post(() -> {
                    Listener l = listener;
                    if (l != null) l.onSent();
                });
            }
            return out;
        }

        log("Unbekannter Befehl vom anderen Handy (" + String.format("%02X", ins) + ").");
        return INS_NOT_SUPPORTED;
    }

    private void prepare() {
        byte[] msg = Card.load(this).ndefMessage();
        ndef = new byte[msg.length + 2];
        ndef[0] = (byte) (msg.length >> 8);
        ndef[1] = (byte) msg.length;
        System.arraycopy(msg, 0, ndef, 2, msg.length);

        int max = Math.min(0x7FFF, Math.max(ndef.length, 0x0100));
        cc = new byte[]{
                0x00, 0x0F,             // CC-Länge
                0x20,                   // Mapping-Version 2.0
                0x00, (byte) 0xFF,      // max. Lesegröße
                0x00, (byte) 0xFF,      // max. Schreibgröße
                0x04, 0x06,             // NDEF-File-Control-TLV
                (byte) 0xE1, 0x04,      // Datei-ID
                (byte) (max >> 8), (byte) max,
                0x00,                   // lesen erlaubt
                (byte) 0xFF             // schreiben gesperrt
        };
    }

    @Override
    public void onDeactivated(int reason) {
        if (appSelected && !notified) log("Verbindung abgerissen, bevor alles gelesen war.");
        appSelected = false;
        current = null;
    }
}
