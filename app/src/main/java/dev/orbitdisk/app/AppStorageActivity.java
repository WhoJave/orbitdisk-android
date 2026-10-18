package dev.orbitdisk.app;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Independent from file scanning and its persistent snapshots. */
public final class AppStorageActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<AppStorageEntry> entries=new ArrayList<>();
    private final Map<String,Drawable> icons=new HashMap<>();
    private boolean alive=true,busy,granted,initialized,includeSystem;
    private int generation;
    private long completed;
    private String query="", failure="";
    private TextView permission,status;
    private Button refresh;
    private ProgressBar progress;
    private BaseAdapter adapter;
    private final ArrayList<AppStorageEntry> visible=new ArrayList<>();
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xfff4f5fb);t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff101421);getWindow().setNavigationBarColor(0xff101421);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(8));root.setBackgroundColor(0xff101421);setContentView(root);
        root.addView(button("← 返回文件分析",this::finish));root.addView(text("应用占用",26));
        root.addView(text("官方 StorageStats 口径：总量 = 应用代码 + 数据；缓存是数据的子集，不重复相加。与文件扫描独立，不能相加（可能重叠）。",12));
        permission=text("",13);root.addView(permission);
        root.addView(button("授权使用情况访问",()->{try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(ActivityNotFoundException e){Toast.makeText(this,"系统没有提供使用情况访问设置",Toast.LENGTH_LONG).show();}}));
        refresh=button("刷新应用统计",this::load);root.addView(refresh);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(0xfff4f5fb);search.setHintTextColor(0xff9ba4ba);search.setHint("搜索应用名称或包名");root.addView(search);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();render();}});
        CheckBox system=new CheckBox(this);system.setText("包含系统应用（默认仅用户应用）");system.setTextColor(0xfff4f5fb);system.setOnCheckedChangeListener((v,c)->{includeSystem=c;render();});root.addView(system);
        status=text("尚未统计",12);root.addView(status);
        root.addView(text("范围：PackageManager 对当前用户可见的已安装应用（已声明广泛查询权限）；系统/OEM仍可能限制，不含其他用户/工作资料。不可查询项保留为不可用，不视为零。点按打开系统应用详情。",12));
        ListView list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View recycled,android.view.ViewGroup parent){
                AppStorageEntry e=visible.get(p);LinearLayout row=new LinearLayout(AppStorageActivity.this);row.setPadding(0,dp(8),0,dp(8));
                ImageView icon=new ImageView(AppStorageActivity.this);icon.setImageDrawable(icons.get(e.packageName));icon.setContentDescription(e.name+" 图标");row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
                LinearLayout detail=new LinearLayout(AppStorageActivity.this);detail.setOrientation(LinearLayout.VERTICAL);row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                detail.addView(text(e.name+(e.system?" · 系统":""),16));detail.addView(text(e.packageName,12));
                detail.addView(text(e.available()?"总量 "+MainActivity.bytes(e.total())+"\n应用代码 "+MainActivity.bytes(e.appBytes)+" · 数据 "+MainActivity.bytes(e.dataBytes)+"\n缓存 "+MainActivity.bytes(e.cacheBytes)+"（已含于数据）":"不可用 · "+e.error,13));return row;
            }};
        list.setAdapter(adapter);list.setOnItemClickListener((a,v,p,id)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+visible.get(p).packageName)));}catch(ActivityNotFoundException e){Toast.makeText(this,"无法打开系统应用详情",Toast.LENGTH_SHORT).show();}});
    }
    private boolean access(){
        // UsageStatsManager is the usage-access service; AppOps is the official gate for its special access.
        UsageStatsManager usage=getSystemService(UsageStatsManager.class);
        AppOpsManager ops=getSystemService(AppOpsManager.class);
        if(usage==null||ops==null)return false;
        int mode=ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName());
        return mode==AppOpsManager.MODE_ALLOWED || (mode==AppOpsManager.MODE_DEFAULT && checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)==PackageManager.PERMISSION_GRANTED);
    }
    @Override protected void onResume(){super.onResume();boolean now=access();boolean changed=initialized&&now!=granted;granted=now;
        permission.setText(now?"使用情况访问：已授权（个别应用仍可能查询失败）":"使用情况访问：未授权 · 应用占用不可用，请先授权");
        if(!initialized||changed){initialized=true;load();}
    }
    private void load(){
        // Invalidate the callback before cancelling. The single executor never runs queries in parallel.
        final int request=++generation;busy=true;completed=0;failure="";entries.clear();icons.clear();render();refresh.setEnabled(false);progress.setVisibility(View.VISIBLE);
        final boolean allowed=access();final Context app=getApplicationContext();
        worker.submit(()->{
            ArrayList<AppStorageEntry> result=new ArrayList<>();Map<String,Drawable> pictures=new HashMap<>();String error="";
            try{
                PackageManager pm=app.getPackageManager();StorageStatsManager stats=app.getSystemService(StorageStatsManager.class);
                List<ApplicationInfo> apps=pm.getInstalledApplications(0);
                for(ApplicationInfo info:apps){
                    if(Thread.currentThread().isInterrupted())return;
                    String name=info.packageName;try{name=pm.getApplicationLabel(info).toString();}catch(RuntimeException ignored){}
                    try{pictures.put(info.packageName,pm.getApplicationIcon(info));}catch(RuntimeException ignored){}
                    boolean system=(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
                    long code=-1,data=-1,cache=-1;String reason=allowed?null:"未授予使用情况访问";
                    if(allowed){try{
                        if(stats==null)throw new IllegalStateException("StorageStats unavailable");
                        StorageStats s=stats.queryStatsForPackage(info.storageUuid!=null?info.storageUuid:android.os.storage.StorageManager.UUID_DEFAULT,info.packageName,android.os.Process.myUserHandle());
                        code=s.getAppBytes();data=s.getDataBytes();cache=s.getCacheBytes();
                        if(code<0||data<0||cache<0||code>Long.MAX_VALUE-data)reason="系统返回无效计量";
                    }catch(Exception e){reason=e instanceof SecurityException?"系统拒绝查询":e.getClass().getSimpleName();}}
                    result.add(new AppStorageEntry(info.packageName,name,system,code,data,cache,reason));
                }
                result.sort(AppStorageEntry.ORDER);
            }catch(Exception e){error="应用枚举失败："+e.getClass().getSimpleName();}
            final String message=error;final long time=System.currentTimeMillis();
            main.post(()->{if(!alive||generation!=request)return;busy=false;entries.addAll(result);icons.putAll(pictures);completed=time;failure=message;refresh.setEnabled(true);progress.setVisibility(View.GONE);render();});
        });
    }
    private void render(){
        if(adapter==null)return;visible.clear();int ok=0,shownOk=0;
        for(AppStorageEntry e:entries){if(e.available())ok++;if(e.matches(query,includeSystem)){visible.add(e);if(e.available())shownOk++;}}
        String when=completed==0?"":new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(completed));
        status.setText((busy?"后台查询中…":"统计截至 "+when+"（非实时，独立刷新）")+"\n可见已安装 "+entries.size()+" · 成功 "+ok+" / 不可用 "+(entries.size()-ok)+"\n当前筛选 "+visible.size()+" · 成功 "+shownOk+" / 不可用 "+(visible.size()-shownOk)+(failure.isEmpty()?"":"\n"+failure));adapter.notifyDataSetChanged();
    }
    @Override protected void onDestroy(){alive=false;generation++;worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
