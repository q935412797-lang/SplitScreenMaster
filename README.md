# 分屏大师 SplitScreenMaster

比亚迪宋Pro DM-i车机多应用三分屏工具，跟AutoTark架构完全一致。

## 架构

- **通信方式**: Binder系统服务（跟AutoTark一致）
- **虚拟显示**: Helper端 DisplayManager.createVirtualDisplay（shell权限）
- **Surface传递**: Binder Parcel 跨进程传递
- **应用启动**: force-stop + windowingMode + 26次重试

## 版本历史

### v17.0
- Binder架构重写，跟AutoTark完全一致
- Helper通过ServiceManager.addService注册为系统服务
- 不再需要MediaProjection录屏授权
- Surface通过Binder Parcel跨进程传递

### v16.0
- 应用启动改进：force-stop + windowingMode + 26次重试
- 滑动注入改进：带持续时间参数
- HelperDaemon改用createPackageContext方式

## 部署

### 1. 推送Helper守护进程
```bash
adb push helper.dex /sdcard/helper.dex
adb shell CLASSPATH=/sdcard/helper.dex app_process /system/bin com.splitscreen.app.HelperDaemon
```

### 2. 安装APK
安装 SplitScreenMaster_v17.0.apk 到车机/手机

### 3. 使用
- 打开分屏大师，点"连接Helper"
- 点"打开分屏界面"
- 选择三个APP启动

## 技术细节

跟AutoTark v5.0.0 反编译研究结果一致：
- 服务名: `splitscreen_helper`（AutoTark为`autotark_helper`）
- Transact code: TX_CREATE_VIRTUAL_DISPLAY=0x6, TX_LAUNCH_APP_ON_DISPLAY=0x36
- 虚拟显示创建: DisplayManager.createVirtualDisplay(name, width, height, density, surface, flags)
