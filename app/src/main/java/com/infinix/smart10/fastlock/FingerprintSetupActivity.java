package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import java.security.MessageDigest;
import java.util.ArrayList;

public class FingerprintSetupActivity extends Activity {
    private ScanView scan; private boolean verifyMode; private int slot; private android.content.SharedPreferences prefs;
    @Override public void onCreate(Bundle b){
        super.onCreate(b); prefs=getSharedPreferences("fastlock",MODE_PRIVATE); verifyMode=getIntent().getBooleanExtra("verify",false); slot=getIntent().getIntExtra("slot",0);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(32,40,32,32); root.setBackgroundColor(0xFF080A0F);
        TextView title=new TextView(this); title.setText(verifyMode?"FastLock Fingerprint":"Add FastLock Fingerprint"); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView info=new TextView(this); info.setText(verifyMode?"\nTrace the same pattern for any registered FastLock finger.\n\nThis works only inside FastLockApp.":"\nAdd a FastLock finger by tracing a pattern below. You can register up to five.\n\nNothing is enrolled into Android."); info.setTextColor(0xFFB9BEC8); info.setGravity(Gravity.CENTER); root.addView(info);
        scan=new ScanView(this); root.addView(scan,new LinearLayout.LayoutParams(-1,0,1));
        Button action=new Button(this); action.setText(verifyMode?"Verify FastLock Fingerprint":"Register FastLock Fingerprint"); action.setOnClickListener(v->finishScan()); root.addView(action);
        Button clear=new Button(this); clear.setText("Clear scan"); clear.setOnClickListener(v->scan.clear()); root.addView(clear); setContentView(root);
    }
    private void finishScan(){
        String sig=scan.signature(); if(sig.length()<12){Toast.makeText(this,"Trace the pattern first",Toast.LENGTH_SHORT).show();return;}
        if(!verifyMode){prefs.edit().putString("finger_"+slot,sig).putBoolean("app_fingerprint_set",true).apply();Toast.makeText(this,"FastLock Fingerprint "+(slot+1)+" added",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}
        else{boolean ok=false;for(int s=0;s<5;s++){String saved=prefs.getString("finger_"+s,"");if(!saved.isEmpty()&&similarity(saved,sig)>=0.48){ok=true;break;}} if(ok){Toast.makeText(this,"FastLock Fingerprint verified",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}else{Toast.makeText(this,"No FastLock fingerprint matched",Toast.LENGTH_SHORT).show();scan.clear();}}
    }
    private double similarity(String a,String b){try{byte[] x=android.util.Base64.decode(a,android.util.Base64.NO_WRAP),y=android.util.Base64.decode(b,android.util.Base64.NO_WRAP);int inter=0,union=0;for(int i=0;i<Math.min(x.length,y.length);i++){int aa=x[i]&255,bb=y[i]&255;inter+=Integer.bitCount(aa&bb);union+=Integer.bitCount(aa|bb);}return union==0?0:(double)inter/union;}catch(Exception e){return 0;}}
    public static class ScanView extends View {
        private final Paint p=new Paint(3); private final ArrayList<PointF> points=new ArrayList<>();
        public ScanView(Context c){super(c);p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);setBackgroundColor(0xFF0D1017);}
        protected void onDraw(Canvas c){super.onDraw(c);p.setColor(0xFFD8B35A);p.setStrokeWidth(3);float cx=getWidth()/2f,cy=getHeight()/2f;for(int i=0;i<7;i++)c.drawOval(cx-170+i*18,cy-220+i*22,cx+170-i*18,cy+220-i*22,p);p.setColor(Color.WHITE);p.setStrokeWidth(7);for(int i=1;i<points.size();i++)c.drawLine(points.get(i-1).x,points.get(i-1).y,points.get(i).x,points.get(i).y,p);}
        public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){points.add(new PointF(e.getX(),e.getY()));invalidate();return true;}return true;}
        public void clear(){points.clear();invalidate();}
        public String signature(){boolean[] cells=new boolean[16*24];float w=getWidth(),h=getHeight();for(PointF q:points){int gx=Math.max(0,Math.min(15,(int)(q.x/w*16))),gy=Math.max(0,Math.min(23,(int)(q.y/h*24)));cells[gy*16+gx]=true;}int count=0;for(boolean v:cells)if(v)count++;if(count<5)return "";byte[] out=new byte[48];for(int i=0;i<cells.length;i++)if(cells[i])out[i/8]|=(byte)(1<<(i%8));return android.util.Base64.encodeToString(out,android.util.Base64.NO_WRAP);}catch(Exception e){return "";}}
    }
}