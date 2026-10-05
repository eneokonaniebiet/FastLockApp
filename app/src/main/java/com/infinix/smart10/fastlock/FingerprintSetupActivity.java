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
    private ScanView scan; private boolean verifyMode; private android.content.SharedPreferences prefs;
    @Override public void onCreate(Bundle b){
        super.onCreate(b); prefs=getSharedPreferences("fastlock",MODE_PRIVATE); verifyMode=getIntent().getBooleanExtra("verify",false);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(32,40,32,32); root.setBackgroundColor(0xFF080A0F);
        TextView title=new TextView(this); title.setText(verifyMode?"FastLock Fingerprint":"Set up FastLock Fingerprint"); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView info=new TextView(this); info.setText(verifyMode?"\nTrace the same pattern you registered for FastLock.\n\nThis works only inside FastLockApp. It does not use or change your phone fingerprint.":"\nTouch and slowly trace the fingerprint pattern below with one finger.\n\nFastLock stores only an app-owned mathematical touch pattern. Nothing is enrolled into Android."); info.setTextColor(0xFFB9BEC8); info.setGravity(Gravity.CENTER); root.addView(info);
        scan=new ScanView(this); root.addView(scan,new LinearLayout.LayoutParams(-1,0,1));
        Button action=new Button(this); action.setText(verifyMode?"Verify FastLock Fingerprint":"Register FastLock Fingerprint"); action.setOnClickListener(v->finishScan()); root.addView(action);
        Button clear=new Button(this); clear.setText("Clear scan"); clear.setOnClickListener(v->scan.clear()); root.addView(clear); setContentView(root);
    }
    private void finishScan(){
        String sig=scan.signature(); if(sig.length()<12){Toast.makeText(this,"Trace the pattern first",Toast.LENGTH_SHORT).show();return;}
        if(!verifyMode){prefs.edit().putString("app_fingerprint_signature",sig).putBoolean("app_fingerprint_set",true).apply();Toast.makeText(this,"FastLock Fingerprint registered",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}
        else{String saved=prefs.getString("app_fingerprint_signature",""); if(similarity(saved,sig)>=0.58){Toast.makeText(this,"FastLock Fingerprint verified",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}else{Toast.makeText(this,"FastLock Fingerprint pattern not matched",Toast.LENGTH_SHORT).show();scan.clear();}}
    }
    private double similarity(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;int n=Math.min(a.length(),b.length()),same=0;for(int i=0;i<n;i++)if(a.charAt(i)==b.charAt(i))same++;return (double)same/Math.max(a.length(),b.length());}
    public static class ScanView extends View {
        private final Paint p=new Paint(3); private final ArrayList<PointF> points=new ArrayList<>();
        public ScanView(Context c){super(c);p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);setBackgroundColor(0xFF0D1017);}
        protected void onDraw(Canvas c){super.onDraw(c);p.setColor(0xFFD8B35A);p.setStrokeWidth(3);float cx=getWidth()/2f,cy=getHeight()/2f;for(int i=0;i<7;i++)c.drawOval(cx-170+i*18,cy-220+i*22,cx+170-i*18,cy+220-i*22,p);p.setColor(Color.WHITE);p.setStrokeWidth(7);for(int i=1;i<points.size();i++)c.drawLine(points.get(i-1).x,points.get(i-1).y,points.get(i).x,points.get(i).y,p);}
        public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){points.add(new PointF(e.getX(),e.getY()));invalidate();return true;}return true;}
        public void clear(){points.clear();invalidate();}
        public String signature(){if(points.size()<8)return "";StringBuilder s=new StringBuilder();float w=getWidth(),h=getHeight();for(PointF q:points){int gx=Math.max(0,Math.min(15,(int)(q.x/w*16)));int gy=Math.max(0,Math.min(23,(int)(q.y/h*24)));s.append((char)('A'+gx)).append((char)('a'+gy));}try{return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes("UTF-8")),android.util.Base64.NO_WRAP);}catch(Exception e){return "";}}
    }
}