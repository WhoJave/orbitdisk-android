package dev.orbitdisk.app;

import android.app.*;
import android.os.*;
import android.provider.Settings;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final int BG=0xff101421, CARD=0xff1a2030, TEXT=0xfff4f5fb, MUTED=0xff9ba4ba, GREEN=0xffa5f0cf;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AtomicBoolean cancel=new AtomicBoolean();
    private StorageScanner.Result result;
    private StorageScanner.Node current;
    private final ArrayDeque<StorageScanner.Node> history=new ArrayDeque<>();
    private LinearLayout body,rows;
    private TextView status,path,summary,capacity;
    private Button scanButton,upButton;
    private SunburstView chart;
    private boolean scanning=false,largest=false,alive=true;
    private String query="";
    public static String bytes(long n){if(n<1024)return n+" B";String[] u={"KB","MB","GB","TB"};double v=n;int i=-1;do{v/=1024;i++;}while(v>=1024&&i<3);return String.format(Locale.US,v>=100?"%.0f %s":"%.1f %s",v,u[i]);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);build();updateCapacity();}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setAllCaps(false);b.setTextSize(13);b.setOnClickListener(v->action.run());return b;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(22),dp(16),dp(22),dp(32));scroll.addView(body);setContentView(scroll);
        TextView brand=label("◌  OrbitDisk",28,TEXT);brand.setTypeface(null,Typeface.BOLD);body.addView(brand);body.addView(label("让存储空间，一目了然",14,MUTED));
        capacity=label("",14,GREEN);capacity.setPadding(0,dp(20),0,dp(8));body.addView(capacity);
        summary=label("设备占用包含系统、应用及其他用户数据",12,MUTED);body.addView(summary);
        chart=new SunburstView(this);body.addView(chart,new LinearLayout.LayoutParams(-1,dp(300)));chart.listener=this::select;
        status=label("本地扫描 · 不上传文件 · 不自动删除",12,MUTED);body.addView(status);
        LinearLayout actions=new LinearLayout(this);scanButton=button("扫描共享存储",()->{if(scanning){cancel.set(true);return;}if(access())startScan();else requestAccess();});actions.addView(scanButton,new LinearLayout.LayoutParams(0,dp(54),1));actions.addView(button("访问授权",this::requestAccess),new LinearLayout.LayoutParams(dp(104),dp(54)));body.addView(actions);
        TextView notice=label("Android 安全限制：即使授予所有文件访问权限，其他应用的私有数据与部分 Android/data 仍不可读取。环图仅表示本次可访问文件的逻辑大小，不等于设备已用空间。",12,MUTED);notice.setPadding(0,dp(10),0,dp(18));body.addView(notice);
        LinearLayout nav=new LinearLayout(this);upButton=button("↑ 上层",this::goUp);nav.addView(upButton,new LinearLayout.LayoutParams(0,dp(48),1));nav.addView(button("目录",()->{largest=false;render();}),new LinearLayout.LayoutParams(0,dp(48),1));nav.addView(button("大文件 TOP100",()->{largest=true;render();}),new LinearLayout.LayoutParams(0,dp(48),1));body.addView(nav);
        path=label("尚未扫描",12,GREEN);path.setPadding(0,dp(12),0,dp(12));path.setTextIsSelectable(true);body.addView(path);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(TEXT);search.setTextSize(14);search.setHintTextColor(MUTED);search.setHint("筛选当前列表中的名称");body.addView(search);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(android.text.Editable e){}});
        rows=new LinearLayout(this);rows.setOrientation(1);body.addView(rows);render();
    }
    private boolean access(){return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void requestAccess(){if(Build.VERSION.SDK_INT>=30){try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}}else requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},10);}
    private void updateCapacity(){try{android.os.StatFs s=new android.os.StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());long total=s.getTotalBytes(),free=s.getAvailableBytes();capacity.setText(bytes(total-free)+" 已用 / "+bytes(total)+" 总容量");summary.setText(bytes(free)+" 可用  ·  设备存储卷统计（含系统与应用）");}catch(Exception e){capacity.setText("无法读取设备容量");}}
    private void startScan(){scanning=true;scanButton.setText("取消扫描");status.setText("正在扫描…可随时取消");cancel=new AtomicBoolean();AtomicBoolean token=cancel;
        worker.submit(()->{try{StorageScanner.Result r=new StorageScanner().scan(Environment.getExternalStorageDirectory(),token,(count,p)->runOnUiThread(()->{if(alive)status.setText("已发现 "+count+" 个文件…");}));runOnUiThread(()->{if(!alive)return;scanning=false;scanButton.setText("重新扫描");if(r.root==null){status.setText("扫描失败：存储根目录不可访问");return;}result=r;current=r.root;history.clear();largest=false;updateCapacity();status.setText(r.files+" 个文件 · "+r.folders+" 个目录 · "+r.skipped+" 项未读取/已跳过");render();});}catch(Exception e){runOnUiThread(()->{if(!alive)return;scanning=false;scanButton.setText("重新扫描");status.setText(e instanceof CancellationException?"扫描已取消，保留上次结果":"扫描失败："+e.getClass().getSimpleName());});}});
    }
    private void select(StorageScanner.Node n){if(n.directory){if(current!=null)history.push(current);current=n;largest=false;render();}else showFile(n);}
    private void goUp(){if(largest){largest=false;render();}else if(!history.isEmpty()){current=history.pop();render();}}
    @Override public void onBackPressed(){if(largest||!history.isEmpty())goUp();else super.onBackPressed();}
    private void render(){if(rows==null)return;rows.removeAllViews();chart.setRoot(current);upButton.setEnabled(largest||!history.isEmpty());path.setText(largest?"全盘大文件 · 按逻辑大小排序":current==null?"尚未扫描":current.path);
        if(result==null||current==null){rows.addView(label("授予访问权限后，点击扫描查看真实空间分布。",14,MUTED));return;}
        java.util.List<StorageScanner.Node> list=largest?result.largest:current.children;int index=0,shown=0;
        for(StorageScanner.Node n:list){int color=SunburstView.COLORS[index++%SunburstView.COLORS.length];if(!n.name.toLowerCase(Locale.ROOT).contains(query))continue;if(shown++>=250)continue;
            LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(shape(CARD));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);rows.addView(row,rp);
            TextView title=label((n.directory?"▸  ":"·  ")+n.name,15,TEXT);title.setMaxLines(2);row.addView(title);double percent=current.size==0?0:100.0*n.size/current.size;
            row.addView(label(bytes(n.size)+(largest?"  ·  "+n.path:String.format(Locale.US,"  ·  %.1f%%",percent)),12,MUTED));
            if(!largest){View bar=new View(this);bar.setBackground(shape(color));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp((float)Math.max(2,Math.min(240,percent*2.4))),dp(3));bp.topMargin=dp(9);row.addView(bar,bp);}
            row.setOnClickListener(v->select(n));
        }
        if(shown==0)rows.addView(label("没有匹配的文件或目录",14,MUTED));if(shown>250)rows.addView(label("显示前 250 项；使用名称筛选缩小范围",12,MUTED));
    }
    private void showFile(StorageScanner.Node n){new AlertDialog.Builder(this).setTitle(n.name).setMessage(bytes(n.size)+"\n\n"+n.path+"\n\n为避免误删，此版本仅分析空间，不提供删除操作。").setPositiveButton("复制路径",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("文件路径",n.path));Toast.makeText(this,"路径已复制",Toast.LENGTH_SHORT).show();}).setNegativeButton("关闭",null).show();}
    @Override protected void onDestroy(){alive=false;cancel.set(true);worker.shutdownNow();super.onDestroy();}
}
