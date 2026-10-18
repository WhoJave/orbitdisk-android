package dev.orbitdisk.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public final class SunburstView extends View {
    public interface Listener { void select(StorageScanner.Node node); }
    public Listener listener;
    private StorageScanner.Node root;
    private final Paint paint=new Paint(3);
    private final ArrayList<Sector> sectors=new ArrayList<>();
    public static final int[] COLORS={0xffa5f0cf,0xffb5a1ff,0xff83c9fa,0xfff4bf80,0xfff493b5,0xff88dfde,0xffcedb87};
    private float cx,cy,inner,band;
    private static class Sector { StorageScanner.Node n; float start,sweep,r1,r2; Sector(StorageScanner.Node n,float s,float w,float a,float b){this.n=n;start=s;sweep=w;r1=a;r2=b;} }
    public SunburstView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("空间分布环形图；下方目录列表提供相同导航");}
    public void setRoot(StorageScanner.Node n){root=n;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c); cx=getWidth()/2f;cy=getHeight()/2f;float radius=Math.min(cx,cy)-18;inner=radius*.43f;band=(radius-inner)/3;sectors.clear();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(band-4);paint.setColor(0xff252b3c);
        for(int i=0;i<3;i++)c.drawCircle(cx,cy,inner+band*(i+.5f),paint);
        if(root!=null&&root.size>0)drawChildren(c,root,-90,360,0,-1);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xfff4f5fb);paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(30*getResources().getDisplayMetrics().scaledDensity);
        c.drawText(root==null?"Orbit":MainActivity.bytes(root.size),cx,cy+2,paint);
        paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setColor(0xff9ba4ba);c.drawText(root==null?"等待扫描":"可访问文件",cx,cy+26*getResources().getDisplayMetrics().density,paint);
    }
    private void drawChildren(Canvas c,StorageScanner.Node n,float start,float sweep,int depth,int inherited){
        if(depth>=3||n.size<=0)return;float angle=start;int index=0;
        for(StorageScanner.Node child:n.children){float w=(float)((double)child.size/n.size*sweep);int color=inherited<0?COLORS[index++%COLORS.length]:inherited;
            if(w>.3f){float a=inner+band*depth,b=a+band-3;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(b-a);paint.setColor(color);paint.setAlpha(255-depth*42);float r=(a+b)/2;c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),angle+Math.min(.6f,w/4),Math.max(.1f,w-Math.min(1.2f,w/2)),false,paint);sectors.add(new Sector(child,angle,w,a,b));if(child.directory)drawChildren(c,child,angle,w,depth+1,color);}
            angle+=w;
        }paint.setAlpha(255);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN)return true;if(e.getAction()==MotionEvent.ACTION_UP){performClick();float x=e.getX()-cx,y=e.getY()-cy,r=(float)Math.hypot(x,y),a=(float)Math.toDegrees(Math.atan2(y,x));while(a< -90)a+=360;for(Sector s:sectors)if(r>=s.r1&&r<=s.r2&&a>=s.start&&a<=s.start+s.sweep){if(listener!=null)listener.select(s.n);break;}return true;}return super.onTouchEvent(e);}
    @Override public boolean performClick(){super.performClick();return true;}
}
