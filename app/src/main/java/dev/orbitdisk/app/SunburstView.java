package dev.orbitdisk.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public final class SunburstView extends View {
    public interface Listener { void select(StorageScanner.Node node); }
    public Listener listener;
    public Runnable onCenterTap;
    private StorageScanner.Node root, preview;
    private android.animation.ValueAnimator animator;
    private float reveal=1f;
    private float downX,downY;
    private boolean moved,longPressed;
    private final Runnable hold=()->{if(preview!=null){longPressed=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate();}};
    private final Paint paint=new Paint(3);
    private final ArrayList<Sector> sectors=new ArrayList<>();
    public static final int[] COLORS={0xffa5f0cf,0xffb5a1ff,0xff83c9fa,0xfff4bf80,0xfff493b5,0xff88dfde,0xffcedb87};
    private float cx,cy,inner,band;
    private static class Sector { StorageScanner.Node n; float start,sweep,r1,r2; Sector(StorageScanner.Node n,float s,float w,float a,float b){this.n=n;start=s;sweep=w;r1=a;r2=b;} }
    public SunburstView(Context c){super(c);setContentDescription("空间分布环形图：点击目录进入，按住扇区预览，点击中心返回；下方列表提供相同导航");}
    public void setRoot(StorageScanner.Node n){
        if(root==n){invalidate();return;}
        root=n;preview=null;if(animator!=null)animator.cancel();
        animator=android.animation.ValueAnimator.ofFloat(0f,1f);animator.setDuration(460);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(hold);if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c); cx=getWidth()/2f;cy=getHeight()/2f;float radius=Math.min(cx,cy)-18;inner=radius*.43f;band=(radius-inner)/3;sectors.clear();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(band-4);paint.setColor(0xff252b3c);
        for(int i=0;i<3;i++)c.drawCircle(cx,cy,inner+band*(i+.5f),paint);
        c.save();float scale=.93f+.07f*reveal;c.scale(scale,scale,cx,cy);
        if(root!=null&&root.size>0)drawChildren(c,root,-90,360*reveal,0,-1);
        c.restore();
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xfff4f5fb);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(30*getResources().getDisplayMetrics().scaledDensity);
        StorageScanner.Node shown=preview!=null?preview:root;
        c.drawText(shown==null?"Orbit":MainActivity.bytes(shown.size),cx,cy+2,paint);
        paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setColor(0xff9ba4ba);
        String name=preview!=null?preview.name:root==null?"等待扫描":"可访问文件 · 点中心返回";
        String fitted=android.text.TextUtils.ellipsize(name,new android.text.TextPaint(paint),Math.max(1,inner*1.8f),android.text.TextUtils.TruncateAt.END).toString();
        c.drawText(fitted,cx,cy+26*getResources().getDisplayMetrics().density,paint);
    }
    private void drawChildren(Canvas c,StorageScanner.Node n,float start,float sweep,int depth,int inherited){
        if(depth>=3||n.size<=0)return;float angle=start;int index=0;
        for(StorageScanner.Node child:n.children){float w=(float)((double)child.size/n.size*sweep);int color=inherited<0?COLORS[index++%COLORS.length]:inherited;
            if(w>.3f){float a=inner+band*depth,b=a+band-3;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(b-a);paint.setColor(color);paint.setAlpha(preview==null||preview==child?255-depth*36:75);float r=(a+b)/2;c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),angle+Math.min(.6f,w/4),Math.max(.1f,w-Math.min(1.2f,w/2)),false,paint);sectors.add(new Sector(child,angle,w,a,b));if(child.directory)drawChildren(c,child,angle,w,depth+1,color);}
            angle+=w;
        }paint.setAlpha(255);
    }
    private StorageScanner.Node hit(float px,float py){
        float scale=.93f+.07f*reveal,x=(px-cx)/scale,y=(py-cy)/scale,r=(float)Math.hypot(x,y),a=(float)Math.toDegrees(Math.atan2(y,x));
        while(a< -90)a+=360;for(Sector s:sectors)if(r>=s.r1&&r<=s.r2&&a>=s.start&&a<=s.start+s.sweep)return s.n;return null;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(reveal<.98f)return false;
                downX=e.getX();downY=e.getY();moved=false;longPressed=false;preview=hit(downX,downY);postDelayed(hold,ViewConfiguration.getLongPressTimeout());invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){moved=true;removeCallbacks(hold);preview=null;invalidate();}return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);StorageScanner.Node tapped=preview;preview=null;invalidate();
                if(!moved&&!longPressed){performClick();if(Math.hypot(e.getX()-cx,e.getY()-cy)<inner){if(onCenterTap!=null)onCenterTap.run();}else if(tapped!=null&&listener!=null){performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);listener.select(tapped);}}return true;
            case MotionEvent.ACTION_CANCEL:removeCallbacks(hold);preview=null;invalidate();return true;
            default:return super.onTouchEvent(e);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
