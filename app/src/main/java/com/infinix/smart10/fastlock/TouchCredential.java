package com.infinix.smart10.fastlock;

import android.view.MotionEvent;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TouchCredential {
    private TouchCredential() {}

    public static final class Session {
        private final List<Float> xs = new ArrayList<>();
        private final List<Float> ys = new ArrayList<>();
        private long start;
        private long end;

        public void begin(MotionEvent e) {
            xs.clear(); ys.clear();
            start = System.currentTimeMillis();
            add(e);
        }

        public void add(MotionEvent e) {
            xs.add(e.getX());
            ys.add(e.getY());
            end = System.currentTimeMillis();
        }

        public String finish() {
            if (xs.size() < 8) return "";
            float minX=xs.get(0), maxX=minX, minY=ys.get(0), maxY=minY;
            for (int i=1;i<xs.size();i++) {
                minX=Math.min(minX,xs.get(i)); maxX=Math.max(maxX,xs.get(i));
                minY=Math.min(minY,ys.get(i)); maxY=Math.max(maxY,ys.get(i));
            }
            float w=Math.max(1f,maxX-minX), h=Math.max(1f,maxY-minY);
            float path=0f;
            for (int i=1;i<xs.size();i++) {
                float dx=(xs.get(i)-xs.get(i-1))/w;
                float dy=(ys.get(i)-ys.get(i-1))/h;
                path += (float)Math.sqrt(dx*dx+dy*dy);
            }
            StringBuilder q=new StringBuilder();
            int samples=16;
            for(int i=0;i<samples;i++){
                int idx=Math.min(xs.size()-1, Math.round(i*(xs.size()-1)/(float)(samples-1)));
                int gx=Math.max(0,Math.min(15,Math.round(((xs.get(idx)-minX)/w)*15f)));
                int gy=Math.max(0,Math.min(15,Math.round(((ys.get(idx)-minY)/h)*15f)));
                q.append(String.format(Locale.US,"%02d%02d",gx,gy));
            }
            long duration=Math.max(1,end-start);
            String raw=String.format(Locale.US,"%s|%.5f|%.5f|%d|%.5f|%.5f",
                    q, w/h, h/w, Math.min(duration,10000L),
                    path, xs.size()/1000f);
            return sha256(raw);
        }
    }

    public static float similarity(String a, String b) {
        if (a==null || b==null || a.isEmpty() || b.isEmpty()) return 0f;
        if (a.equals(b)) return 1f;
        // The signature is intentionally an app-owned touch credential, not a fingerprint template.
        int same=0, total=Math.min(a.length(),b.length());
        for(int i=0;i<total;i++) if(a.charAt(i)==b.charAt(i)) same++;
        return total==0?0f:(float)same/Math.max(a.length(),b.length());
    }

    public static boolean matchesAny(String candidate, String stored) {
        if(candidate.isEmpty() || stored==null || stored.isEmpty()) return false;
        for(String s: stored.split(",")) {
            if(similarity(candidate,s.trim()) >= 0.82f) return true;
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            byte[] out=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();
            for(byte b:out) sb.append(String.format(Locale.US,"%02x",b));
            return sb.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
