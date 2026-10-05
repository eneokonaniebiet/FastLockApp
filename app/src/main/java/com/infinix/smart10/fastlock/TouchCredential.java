package com.infinix.smart10.fastlock;

import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TouchCredential {
    private TouchCredential() {}

    /*
     * FastLock's "fingerprint" is an app-owned touch credential.
     * Android never provides or stores a raw fingerprint template here.
     * The credential is a normalized touch/hold pattern so small changes in
     * finger position and speed do not make a valid enrollment fail.
     */
    public static final class Session {
        private final List<Float> xs = new ArrayList<>();
        private final List<Float> ys = new ArrayList<>();
        private long start;
        private long end;

        public void begin(MotionEvent e) {
            xs.clear();
            ys.clear();
            start = System.currentTimeMillis();
            add(e);
        }

        public void add(MotionEvent e) {
            xs.add(e.getX());
            ys.add(e.getY());
            end = System.currentTimeMillis();
        }

        public long durationMs() {
            return Math.max(0L, end - start);
        }

        public String finish() {
            if (xs.size() < 1) return "";

            float minX=xs.get(0), maxX=minX, minY=ys.get(0), maxY=minY;
            for (int i=1;i<xs.size();i++) {
                minX=Math.min(minX,xs.get(i));
                maxX=Math.max(maxX,xs.get(i));
                minY=Math.min(minY,ys.get(i));
                maxY=Math.max(maxY,ys.get(i));
            }

            float w=Math.max(1f,maxX-minX);
            float h=Math.max(1f,maxY-minY);

            StringBuilder q=new StringBuilder("v2;");
            int samples=24;
            for(int i=0;i<samples;i++){
                int idx=Math.min(xs.size()-1,
                        Math.round(i*(xs.size()-1)/(float)(samples-1)));
                int gx=Math.max(0,Math.min(31,
                        Math.round(((xs.get(idx)-minX)/w)*31f)));
                int gy=Math.max(0,Math.min(31,
                        Math.round(((ys.get(idx)-minY)/h)*31f)));
                if(i>0) q.append('.');
                q.append(String.format(Locale.US,"%02d%02d",gx,gy));
            }

            long duration=Math.min(durationMs(),10000L);
            q.append(";d=").append(duration);
            return q.toString();
        }
    }

    public static float similarity(String a, String b) {
        if (a==null || b==null || a.isEmpty() || b.isEmpty()) return 0f;
        if (!a.startsWith("v2;") || !b.startsWith("v2;")) return 0f;

        try {
            String[] ap=a.split(";");
            String[] bp=b.split(";");
            if (ap.length<3 || bp.length<3) return 0f;

            String[] pa=ap[1].split("\\.");
            String[] pb=bp[1].split("\\.");
            int n=Math.min(pa.length,pb.length);
            if(n==0) return 0f;

            double totalDistance=0d;
            int valid=0;
            for(int i=0;i<n;i++){
                if(pa[i].length()!=4 || pb[i].length()!=4) continue;
                int ax=Integer.parseInt(pa[i].substring(0,2));
                int ay=Integer.parseInt(pa[i].substring(2,4));
                int bx=Integer.parseInt(pb[i].substring(0,2));
                int by=Integer.parseInt(pb[i].substring(2,4));
                double dx=ax-bx, dy=ay-by;
                totalDistance += Math.sqrt(dx*dx+dy*dy);
                valid++;
            }
            if(valid==0) return 0f;

            double positionScore=1d-(totalDistance/valid)/44d;
            positionScore=Math.max(0d,Math.min(1d,positionScore));

            long da=parseDuration(ap[2]);
            long db=parseDuration(bp[2]);
            double durationScore=1d-Math.min(1d,Math.abs(da-db)/1800d);

            return (float)(positionScore*0.90d + durationScore*0.10d);
        } catch(Exception ignored) {
            return 0f;
        }
    }

    private static long parseDuration(String part) {
        if(part==null || !part.startsWith("d=")) return 0L;
        return Long.parseLong(part.substring(2));
    }

    public static boolean matchesAny(String candidate, String stored) {
        if(candidate==null || candidate.isEmpty() || stored==null || stored.isEmpty()) return false;
        for(String s: stored.split(",")) {
            String saved=s.trim();
            if(similarity(candidate,saved) >= 0.70f) return true;
        }
        return false;
    }
}
