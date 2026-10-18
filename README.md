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

目录列表每次最多展示 250 项，可通过搜索筛选当前列表；大文件列表是全盘前 100。环图显示当前目录向下最多三层，极小扇区不绘制，可通过列表访问。旋转或关闭页面会取消当前扫描，结果仅保留于当前 Activity 内存，不保存到磁盘。

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

初版刻意不提供误删风险较高的清理操作；后续可以加入 SAF 目录选择、持久化扫描快照与更完善的可访问性支持。
