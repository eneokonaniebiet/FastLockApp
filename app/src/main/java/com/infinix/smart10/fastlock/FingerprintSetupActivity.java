package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import java.util.ArrayList;

public class FingerprintSetupActivity extends Activity {
    private ScanView scan;
    private boolean verifyMode;
    private android.content.SharedPreferences prefs;
    private TextView progress;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("fastlock",MODE_PRIVATE);
        verifyMode=getIntent().getBooleanExtra("verify",false);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24,28,24,24);
        root.setBackgroundColor(0xFF080A0F);

        TextView build=new TextView(this);
        build.setText("FastLock v2.0 • TOUCH BUILD");
        build.setTextColor(0xFFD8B35A);
        build.setTextSize(12);
        build.setGravity(Gravity.CENTER);
        root.addView(build);

        TextView title=new TextView(this);
        title.setText(verifyMode ? "FastLock Fingerprint" : "Add FastLock Fingerprint");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info=new TextView(this);
        info.setText(verifyMode
                ? "\nPlace your finger anywhere inside the scan area and trace until the circle fills.\n\nThis is an app-owned FastLock credential."
                : "\nTouch and move your finger around the scan area until it fills.\n\nYou can save up to 5 FastLock fingers. This does not copy or enroll your real Android fingerprint.");
        info.setTextColor(0xFFB9BEC8);
        info.setGravity(Gravity.CENTER);
        root.addView(info);

        scan=new ScanView(this);
        scan.listener=percent -> runOnUiThread(() -> {
            progress.setText("Scan coverage: "+percent+"%");
            if(!verifyMode && percent>=100 && !scan.saved) finishScan();
        });
        root.addView(scan,new LinearLayout.LayoutParams(-1,0,1));

        progress=new TextView(this);
        progress.setText("Scan coverage: 0%");
        progress.setTextColor(0xFFD8B35A);
        progress.setTextSize(15);
        progress.setGravity(Gravity.CENTER);
        root.addView(progress,new LinearLayout.LayoutParams(-1,-2));

        Button action=new Button(this);
        action.setText(verifyMode ? "Verify Fingerprint" : "Save Fingerprint");
        action.setOnClickListener(v->finishScan());
        root.addView(action,new LinearLayout.LayoutParams(-1,-2));

        Button clear=new Button(this);
        clear.setText("Clear / start again");
        clear.setOnClickListener(v->scan.clear());
        root.addView(clear,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
    }

    private void finishScan(){
        if(scan.saved) return;
        String sig=scan.signature();
        if(sig.length()<12 || scan.coverage()<0.60f){
            Toast.makeText(this,"Keep touching and moving around the scan area until it is full.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!verifyMode){
            int target=-1;
            for(int s=0;s<5;s++) if(!prefs.contains("finger_"+s)){target=s;break;}
            if(target<0){Toast.makeText(this,"All 5 FastLock fingerprint slots are already used.",Toast.LENGTH_LONG).show();return;}
            scan.saved=true;
            prefs.edit().putString("finger_"+target,sig).putBoolean("app_fingerprint_set",true).apply();
            Toast.makeText(this,"FastLock Fingerprint "+(target+1)+" saved",Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            boolean ok=false;
            for(int s=0;s<5;s++){
                String saved=prefs.getString("finger_"+s,"");
                if(!saved.isEmpty() && similarity(saved,sig)>=0.42){ok=true;break;}
            }
            if(ok){
                scan.saved=true;
                Toast.makeText(this,"FastLock Fingerprint verified",Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }else{
                Toast.makeText(this,"Fingerprint not matched. Try again.",Toast.LENGTH_SHORT).show();
                scan.clear();
            }
        }
    }

    private double similarity(String a,String b){
        try{
            byte[] x=android.util.Base64.decode(a,android.util.Base64.NO_WRAP);
            byte[] y=android.util.Base64.decode(b,android.util.Base64.NO_WRAP);
            int inter=0,union=0;
            for(int i=0;i<Math.min(x.length,y.length);i++){
                int aa=x[i]&255,bb=y[i]&255;
                inter+=Integer.bitCount(aa&bb);
                union+=Integer.bitCount(aa|bb);
            }
            return union==0?0:(double)inter/union;
        }catch(Exception e){return 0;}
    }

    public static class ScanView extends View {
        interface Listener { void onCoverage(int percent); }
        private final Paint p=new Paint(3);
        private final ArrayList<PointF> points=new ArrayList<>();
        private final boolean[] cells=new boolean[20*30];
        private int covered=0;
        boolean saved=false;
        Listener listener;

        public ScanView(Context c){
            super(c);
            setBackgroundColor(0xFF0D1017);
            setFocusable(true);
            setClickable(true);
        }

        protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(0xFFD8B35A);
            for(int i=0;i<7;i++)
                c.drawOval(cx-170+i*18,cy-220+i*22,cx+170-i*18,cy+220-i*22,p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFD8B35A);
            c.drawCircle(cx,cy,Math.min(getWidth(),getHeight())*0.16f,p);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(18);
            c.drawText("TOUCH & MOVE",cx,cy+7,p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(Color.WHITE);
            for(int i=1;i<points.size();i++)
                c.drawLine(points.get(i-1).x,points.get(i-1).y,points.get(i).x,points.get(i).y,p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF202631);
            c.drawRect(24,getHeight()-34,getWidth()-24,getHeight()-20,p);
            p.setColor(0xFFD8B35A);
            c.drawRect(24,getHeight()-34,24+(getWidth()-48)*coverage(),getHeight()-20,p);
        }

        public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN || e.getActionMasked()==MotionEvent.ACTION_MOVE || e.getActionMasked()==MotionEvent.ACTION_UP){
                addPoint(e.getX(),e.getY());
                return true;
            }
            return true;
        }

        private void addPoint(float x,float y){
            points.add(new PointF(x,y));
            int gx=Math.max(0,Math.min(19,(int)(x/Math.max(1,getWidth())*20)));
            int gy=Math.max(0,Math.min(29,(int)(y/Math.max(1,getHeight())*30)));
            int idx=gy*20+gx;
            if(!cells[idx]){cells[idx]=true;covered++;}
            invalidate();
            if(listener!=null) listener.onCoverage((int)(coverage()*100));
        }

        float coverage(){ return covered/(float)cells.length; }

        public void clear(){
            points.clear();
            java.util.Arrays.fill(cells,false);
            covered=0;
            saved=false;
            invalidate();
            if(listener!=null) listener.onCoverage(0);
        }

        public String signature(){
            if(covered<10)return "";
            byte[] out=new byte[(cells.length+7)/8];
            for(int i=0;i<cells.length;i++) if(cells[i]) out[i/8]|=(byte)(1<<(i%8));
            return android.util.Base64.encodeToString(out,android.util.Base64.NO_WRAP);
        }
    }
}
