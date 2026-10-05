package com.infinix.smart10.fastlock;

import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TouchCredential {
    private TouchCredential() {}

    /*
     * FastLock uses an app-owned touch credential, not Android biometric data.
     * The raw fingerprint template is never available to this app.
     *
     * v3 deliberately keeps absolute sensor position and touch characteristics.
     * The old v2 normalized the whole touch to its own bounding box, which meant
     * a simple stationary touch could collapse into almost the same credential
     * for every finger.
     */
    public static final class Session {
        private final List<Float> xs = new ArrayList<>();
        private final List<Float> ys = new ArrayList<>();
        private final List<Float> pressures = new ArrayList<>();
        private final List<Float> majors = new ArrayList<>();
        private final List<Float> minors = new ArrayList<>();
        private long start;
        private long end;

        public void begin(MotionEvent e) {
            xs.clear();
            ys.clear();
            pressures.clear();
            majors.clear();
            minors.clear();
            start = System.currentTimeMillis();
            add(e);
        }

        public void add(MotionEvent e) {
            xs.add(e.getX());
            ys.add(e.getY());
            pressures.add(clamp01(e.getPressure()));
            majors.add(clamp01(e.getTouchMajor() / 210f));
            minors.add(clamp01(e.getTouchMinor() / 210f));
            end = System.currentTimeMillis();

            // Keep the credential compact while preserving the beginning/end and
            // representative motion samples.
            if (xs.size() > 32) {
                xs.remove(1);
                ys.remove(1);
                pressures.remove(1);
                majors.remove(1);
                minors.remove(1);
            }
        }

        private static float clamp01(float v) {
            if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
            return Math.max(0f, Math.min(1f, v));
        }

        public long durationMs() {
            return Math.max(0L, end - start);
        }

        public String finish() {
            if (xs.size() < 2) return "";

            StringBuilder q = new StringBuilder("v3;");
            int samples = 16;

            for (int i = 0; i < samples; i++) {
                int idx = Math.min(xs.size() - 1,
                        Math.round(i * (xs.size() - 1) / (float)(samples - 1)));

                int gx = Math.max(0, Math.min(99, Math.round((xs.get(idx) / 210f) * 99f)));
                int gy = Math.max(0, Math.min(99, Math.round((ys.get(idx) / 210f) * 99f)));
                int pr = Math.max(0, Math.min(99, Math.round(pressures.get(idx) * 99f)));
                int ma = Math.max(0, Math.min(99, Math.round(majors.get(idx) * 99f)));
                int mi = Math.max(0, Math.min(99, Math.round(minors.get(idx) * 99f)));

                if (i > 0) q.append('.');
                q.append(String.format(Locale.US, "%02d%02d%02d%02d%02d",
                        gx, gy, pr, ma, mi));
            }

            long duration = Math.min(durationMs(), 10000L);
            q.append(";d=").append(duration);
            return q.toString();
        }
    }

    public static float similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0f;
        if (!a.startsWith("v3;") || !b.startsWith("v3;")) return 0f;

        try {
            String[] ap = a.split(";");
            String[] bp = b.split(";");
            if (ap.length < 3 || bp.length < 3) return 0f;

            String[] pa = ap[1].split("\\.");
            String[] pb = bp[1].split("\\.");
            int n = Math.min(pa.length, pb.length);
            if (n < 8) return 0f;

            double position = 0d;
            double pressure = 0d;
            double major = 0d;
            double minor = 0d;

            for (int i = 0; i < n; i++) {
                if (pa[i].length() != 10 || pb[i].length() != 10) return 0f;

                int ax = Integer.parseInt(pa[i].substring(0, 2));
                int ay = Integer.parseInt(pa[i].substring(2, 4));
                int apress = Integer.parseInt(pa[i].substring(4, 6));
                int ama = Integer.parseInt(pa[i].substring(6, 8));
                int ami = Integer.parseInt(pa[i].substring(8, 10));

                int bx = Integer.parseInt(pb[i].substring(0, 2));
                int by = Integer.parseInt(pb[i].substring(2, 4));
                int bpress = Integer.parseInt(pb[i].substring(4, 6));
                int bma = Integer.parseInt(pb[i].substring(6, 8));
                int bmi = Integer.parseInt(pb[i].substring(8, 10));

                double dx = ax - bx;
                double dy = ay - by;
                position += Math.max(0d, 1d - Math.sqrt(dx * dx + dy * dy) / 28d);
                pressure += Math.max(0d, 1d - Math.abs(apress - bpress) / 55d);
                major += Math.max(0d, 1d - Math.abs(ama - bma) / 55d);
                minor += Math.max(0d, 1d - Math.abs(ami - bmi) / 55d);
            }

            position /= n;
            pressure /= n;
            major /= n;
            minor /= n;

            long da = parseDuration(ap[2]);
            long db = parseDuration(bp[2]);
            double duration = Math.max(0d,
                    1d - Math.min(1d, Math.abs(da - db) / 1200d));

            return (float)(position * 0.55d
                    + pressure * 0.20d
                    + major * 0.10d
                    + minor * 0.05d
                    + duration * 0.10d);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private static long parseDuration(String part) {
        if (part == null || !part.startsWith("d=")) return 0L;
        return Long.parseLong(part.substring(2));
    }

    public static boolean matchesAny(String candidate, String stored) {
        if (candidate == null || candidate.isEmpty()
                || stored == null || stored.isEmpty()) return false;

        for (String s : stored.split(",")) {
            String saved = s.trim();
            if (similarity(candidate, saved) >= 0.82f) return true;
        }
        return false;
    }
}
