# OrbitDisk for Android

一个受 DaisyDisk 空间可视化思路启发的独立原生 Android 存储分析器。独立品牌与代码，不使用 DaisyDisk 素材，不隶属于 DaisyDisk。

## 功能

- 真实扫描主共享存储，后台执行、进度提示、取消扫描。
- 三层可交互 Sunburst 环形图，点击扇区进入目录，下钻/返回导航。
- 目录按逻辑大小降序排列、占比条、名称筛选。
- 全局最大 100 个文件；文件详情与路径复制。
- 展示设备卷总量/已用/可用，并独立展示可扫描文件总量。
- 跳过符号链接、不可读项与特殊文件；限制递归深度 128；报告跳过数量。
- 无联网权限、无遥测、不上传文件、不提供删除功能。

## 1.2.0 应用占用（versionCode 4）

- 主页“应用占用”进入独立原生页面，无新增依赖，不改变文件扫描、环图或快照格式。
- PackageManager 枚举当前用户可见的已安装应用；后台使用 StorageStatsManager.queryStatsForPackage 查询各应用所在存储卷，显示图标、名称、包名、代码、数据和缓存。总量严格为 appBytes + dataBytes，cacheBytes 是 dataBytes 的子集，不重复计算。
- 默认仅用户应用，可包含系统/更新系统应用；按总量降序、名称和包名稳定排序，不可用项置底；支持应用名/包名不区分大小写搜索，ListView 展示全部匹配项，点按打开系统应用详情。
- 使用情况访问通过 UsageStatsManager 服务及 AppOps GET_USAGE_STATS 授权状态检查；“授权使用情况访问”打开 ACTION_USAGE_ACCESS_SETTINGS，返回页面重新检查，授权状态变化自动刷新。未授权、拒绝、卸载竞态、卷不可用等逐项显示不可用及原因，绝不使用 APK 长度或零冒充应用总占用。页面同时显示全部可见应用和筛选范围的成功/失败数。
- 查询/图标加载位于后台单线程；刷新禁用按钮，代次校验阻止旧回调覆盖；销毁时中断任务并屏蔽 UI 回调。普通返回页面保留非实时结果；授权变化或手动刷新才重查，Activity 重建重新查询。显示截至时间，不保存应用统计快照，不与文件结果求和（两者可能重叠）。查询不是原子快照，不保证等同系统设置的实时显示，系统包共享数据口径也不应跨包简单相加。
- 声明 PACKAGE_USAGE_STATS（tools:ignore=ProtectedPermissions），它是系统特殊访问，不是运行时弹窗权限；无需 root、Shizuku 或删除权限。文件访问与使用情况访问是独立授权。
- 声明 QUERY_ALL_PACKAGES 以支持核心存储分析功能的应用清单；即便如此，范围仍受当前用户、工作资料、系统/OEM 包可见性策略限制，不能保证列出整部手机全部包。枚举整体失败会显式报错，未取得权限不隐藏可见应用。
- **Google Play 审核：QUERY_ALL_PACKAGES 和既有 MANAGE_EXTERNAL_STORAGE 均为受限权限。广泛查询仅用于核心存储分析正当用途；如提交 Play，需按当时政策证明必要性、提交权限声明并通过审核，不保证获批。** 应用清单与占用只在本机展示，无网络权限、无上传、无清理/卸载功能。
- 验证：7 项扫描测试、3 组快照测试与新增纯 Java 计量/排序/搜索/系统筛选/不可用及溢出测试通过；本地 ARM64 debug 构建与 APK 签名/完整性验证通过。未做真机安装启动、授权往返、厂商差异、图标显示和旋转/后台交互测试；这些仍需人工验收。

## 1.1.1 后台恢复修复（versionCode 3）

- 完整扫描成功后，在后台将目录树、统计和扫描时间写入应用私有目录；启动时异步恢复，并从树重建 TOP100，不自动重扫。
- 界面明确显示“历史快照（非实时）”及扫描时间。文件变化后请手动重新扫描；设备卷容量仍为独立的当前统计。
- SharedPreferences 保存当前目录路径及目录/TOP100 模式；恢复祖先 history，支持正确返回上层。路径不在快照中时安全回到根目录。
- 快照使用流式 DataInput/Output 二进制、版本头、CRC32 校验，限制 500,000 节点、128 层深度和 256 MiB 文件大小；临时文件同步后原子替换。损坏/截断/未知版本安全忽略，写入失败保留旧快照；不使用 Java 对象反序列化。
- 缓存读写与扫描均在后台串行执行，用户开始新扫描会使旧恢复回调失效，避免缓存覆盖新结果。
- 普通退后台不取消扫描；Activity 重建可恢复最近完整快照。进程存活时旧扫描可完成并保存，但未完成扫描不保证跨进程继续；本版本没有 foreground service，也不保证系统长期后台运行。
- 未新增权限或依赖。快照位于应用私有存储，备份已禁用；清除应用数据或卸载会移除快照。

## 1.1.0 交互优化

- 扫描完成及目录切换时，环图执行 460ms 缓出展开 + 轻微缩放（遵循 Android 系统动画时长设置；非 DaisyDisk 官方动画规格）。
- 按住扇区可查看文件名与大小，其他扇区变暗，长按结束不自动打开；拖动时取消选择，避免滚动误触。
- 点击图心返回上层；点击路径可从祖先目录列表快速跳转。
- 目录或模式切换时列表 180ms 淡入；搜索输入不反复触发切换动画。
- 扫描增加不确定进度动画，不伪造完成百分比。
- 优先使用系统硬件加速 Canvas；动画帧率、触控与无障碍仍需真机验证。

## 运行

Android 8.0（API 26）及以上。安装 APK 后，点击“访问授权”，在系统设置允许“所有文件访问权限”，返回应用点击“扫描共享存储”。Android 8–10 使用普通读取权限。

**Android 的限制不是可绕过的扫描结果：** 即使已授权，其他应用 `/data` 私有数据、部分 `Android/data`、工作资料与系统文件仍不可读。扫描范围是 `Environment.getExternalStorageDirectory()` 返回的主共享存储，不包含其他可移除卷。环图只展示可访问文件的逻辑长度；稀疏文件、文件系统元数据、共享/压缩块、权限限制和扫描期间变动会造成它与卷已用量不一致。跳过项数量不是隐藏文件的数量或字节数估计。未读取目录可能显示为 0；目录扫描不是文件系统原子快照。

目录列表每次最多展示 250 项，可通过搜索筛选当前列表；大文件列表是全盘前 100。环图显示当前目录向下最多三层，极小扇区不绘制，可通过列表访问。完整结果会保存为私有本地快照，旋转、退后台或重新启动无需强制重扫。超过快照安全限制或磁盘写入失败时，会提示保存失败，旧的完整快照仍可使用。

## 构建

JDK 17、Android SDK Platform 34、Build Tools 35.0.0、Gradle 8.11.1、AGP 8.9.0。

```sh
# SDK 路径写入本地 local.properties，或设置 ANDROID_HOME
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

ARM64 Alpine/PRoot 需要可运行的 ARM64 AAPT2，而非 Google Linux SDK 的 x86_64 程序。本项目不打包第三方宿主工具；在已有 Minis 开发环境中：

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
gradle -Pandroid.aapt2FromMavenOverride=/opt/android-sdk/build-tools/35.0.0/aapt2 assembleDebug
```

GitHub Actions 使用标准 Ubuntu 构建，并上传 debug APK。仓库不含任何密钥、签名库或本地 SDK 配置。Debug 签名仅用于开发；正式发布需独立签名策略。`MANAGE_EXTERNAL_STORAGE` 属 Google Play 受限权限，发布前需满足文件管理/存储分析用途审核要求。

## 验证状态

- 本地 ARM64 构建已 `BUILD SUCCESSFUL`。
- APK manifest、ZIP 完整性与 APK v2 debug 签名已验证。
- 扫描核心 JVM 测试：`sh scripts/test-scanner.sh`（使用 Android API 的 JVM 文件系统替身，不替代真机权限测试）。
- 未完成真机安装/启动及权限流程测试：当前设备 Shizuku 未运行，Accessibility 服务未启用。

## 结构

- `MainActivity.java`：界面、权限、后台扫描生命周期与导航。
- `SunburstView.java`：原生 Canvas 绘图与扇区命中测试。
- `StorageScanner.java`：目录扫描与 top-K 文件统计。
- `ScanSnapshot.java`：受限流式二进制快照、校验和原子替换。
- `ScanSnapshotTest.java`：往返、TOP100/时间、损坏与截断、深度/文件大小限制、失败保留旧文件测试；与扫描测试共用 `sh scripts/test-scanner.sh`。

本版不提供误删风险较高的清理操作；后续可以加入 SAF 目录选择与更完善的可访问性支持。
