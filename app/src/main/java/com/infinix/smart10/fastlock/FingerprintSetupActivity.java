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
        root.setPadding(24,32,24,24);
        root.setBackgroundColor(0xFF080A0F);

        TextView title=new TextView(this);
        title.setText(verifyMode ? "FastLock Fingerprint" : "Add FastLock Fingerprint");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info=new TextView(this);
        info.setText(verifyMode
                ? "\nTouch, lift, then touch again. Repeat until the scan completes.\n\nThis is FastLock's own touch credential, separate from Android biometrics."
                : "\nPlace your finger on the screen, lift it, then press again. Repeat with slightly different positions until the scan is full.\n\nUp to 5 FastLock touch credentials can be saved. This does not copy your real Android fingerprint.");
        info.setTextColor(0xFFB9BEC8);
        info.setGravity(Gravity.CENTER);
        root.addView(info);

        scan=new ScanView(this);
        scan.listener=percent -> runOnUiThread(() -> {
            progress.setText(percent>=100 ? "Fingerprint added" : "Scan progress: "+percent+"%");
            if(!verifyMode && percent>=100 && !scan.saved) finishScan();
        });
        root.addView(scan,new LinearLayout.LayoutParams(-1,0,1));

        progress=new TextView(this);
        progress.setText("Scan progress: 0%");
        progress.setTextColor(0xFFD8B35A);
        progress.setTextSize(16);
        progress.setGravity(Gravity.CENTER);
        root.addView(progress,new LinearLayout.LayoutParams(-1,-2));

        Button action=new Button(this);
        action.setText(verifyMode ? "Verify" : "Finish");
        action.setOnClickListener(v->finishScan());
        root.addView(action,new LinearLayout.LayoutParams(-1,-2));

        Button clear=new Button(this);
        clear.setText("Start again");
        clear.setOnClickListener(v->scan.clear());
        root.addView(clear,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
    }

    private void finishScan(){
        if(scan.saved) return;
        String sig=scan.signature();
        if(sig.length()<12 || scan.pressCount<8){
            Toast.makeText(this,"Keep pressing and lifting your finger until the scan is full.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!verifyMode){
            int target=-1;
            for(int s=0;s<5;s++) if(!prefs.contains("finger_"+s)){target=s;break;}
            if(target<0){
                Toast.makeText(this,"All 5 FastLock fingerprint slots are already used.",Toast.LENGTH_LONG).show();
                return;
            }
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
        private final ArrayList<PointF> presses=new ArrayList<>();
        private final boolean[] cells=new boolean[20*30];
        private int covered=0;
        int pressCount=0;
        boolean fingerDown=false;
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
            p.setTextSize(16);
            c.drawText(fingerDown ? "LIFT FINGER" : "PRESS FINGER",cx,cy+6,p);

            p.setColor(0xFFFFFFFF);
            for(PointF pt:presses) c.drawCircle(pt.x,pt.y,10,p);

            p.setColor(0xFF202631);
            c.drawRoundRect(24,getHeight()-38,getWidth()-24,getHeight()-18,10,10,p);
            p.setColor(0xFFD8B35A);
            c.drawRoundRect(24,getHeight()-38,24+(getWidth()-48)*coverage(),getHeight()-18,10,10,p);

            p.setColor(0xFFB9BEC8);
            p.setTextSize(14);
            c.drawText(pressCount+" / 10 touches",cx,getHeight()-52,p);
        }

        public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                fingerDown=true;
                addPress(e.getX(),e.getY());
                invalidate();
                return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                fingerDown=false;
                invalidate();
                return true;
            }
            return true;
        }

        private void addPress(float x,float y){
            if(pressCount>=10) return;
            pressCount++;
            presses.add(new PointF(x,y));

            int gx=Math.max(0,Math.min(19,(int)(x/Math.max(1,getWidth())*20)));
            int gy=Math.max(0,Math.min(29,(int)(y/Math.max(1,getHeight())*30)));
            int radius=2;
            for(int yy=gy-radius;yy<=gy+radius;yy++){
                for(int xx=gx-radius;xx<=gx+radius;xx++){
                    if(xx<0||xx>=20||yy<0||yy>=30) continue;
                    if((xx-gx)*(xx-gx)+(yy-gy)*(yy-gy)<=radius*radius){
                        int idx=yy*20+xx;
                        if(!cells[idx]){cells[idx]=true;covered++;}
                    }
                }
            }
            invalidate();
            if(listener!=null) listener.onCoverage((int)(coverage()*100));
        }

        float coverage(){ return Math.min(1f,covered/(float)cells.length); }

        public void clear(){
            presses.clear();
            java.util.Arrays.fill(cells,false);
            covered=0;
            pressCount=0;
            fingerDown=false;
            saved=false;
            invalidate();
            if(listener!=null) listener.onCoverage(0);
        }

        public String signature(){
            if(pressCount<8)return "";
            byte[] out=new byte[(cells.length+7)/8];
            for(int i=0;i<cells.length;i++) if(cells[i]) out[i/8]|=(byte)(1<<(i%8));
            return android.util.Base64.encodeToString(out,android.util.Base64.NO_WRAP);
        }
    }
}
