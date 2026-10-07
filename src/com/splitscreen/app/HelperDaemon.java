package com.splitscreen.app;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class HelperDaemon extends Binder {

    private static final String SERVICE_NAME = "splitscreen_helper";
    private static final String INTERFACE_TOKEN = "com.splitscreen.app.IHelper";

    private static final int TX_PING = 0x1;
    private static final int TX_CREATE_VIRTUAL_DISPLAY = 0x6;
    private static final int TX_RELEASE_VIRTUAL_DISPLAY = 0x7;
    private static final int TX_LAUNCH_APP_ON_DISPLAY = 0x36;
    private static final int TX_INPUT_TAP = 0x1c;
    private static final int TX_INPUT_SWIPE = 0x1d;
    private static final int TX_INPUT_KEY = 0x1f;
    private static final int TX_EXEC_SHELL = 0x1a;

    private static Context appContext;
    private static Context shellContext;
    private static DisplayManager displayManager;
    private static final ConcurrentHashMap<Integer, VirtualDisplay> virtualDisplays = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        log("========================================");
        log("分屏大师 HelperDaemon v17.1 (Binder架构)");
        log("跟AutoTark完全一致: ServiceManager系统服务");
        log("========================================");

        try {
            log("初始化ActivityThread...");
            Class<?> looperClass = Class.forName("android.os.Looper");
            looperClass.getMethod("prepare").invoke(null);
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object activityThread = atClass.getMethod("systemMain").invoke(null);
            appContext = (Context) activityThread.getClass().getMethod("getApplication").invoke(activityThread);
            log("Application Context: " + appContext.getPackageName());

            log("创建shell Context...");
            shellContext = appContext.createPackageContext("com.android.shell", 0);
            displayManager = (DisplayManager) shellContext.getSystemService("display");
            log("shell Context + DisplayManager 就绪");

            HelperDaemon daemon = new HelperDaemon();
            log("注册系统服务: " + SERVICE_NAME);
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method addService = smClass.getMethod("addService", String.class, IBinder.class);
            addService.invoke(null, SERVICE_NAME, daemon);
            log("系统服务注册成功！等待客户端连接...");

            looperClass.getMethod("loop").invoke(null);

        } catch (Exception e) {
            log("启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        try {
            data.enforceInterface(INTERFACE_TOKEN);

            switch (code) {
                case TX_PING:
                    log("TX_PING");
                    reply.writeInt(0);
                    reply.writeString("PONG");
                    return true;

                case TX_CREATE_VIRTUAL_DISPLAY:
                    return handleCreateVirtualDisplay(data, reply);

                case TX_RELEASE_VIRTUAL_DISPLAY:
                    return handleReleaseVirtualDisplay(data, reply);

                case TX_LAUNCH_APP_ON_DISPLAY:
                    return handleLaunchApp(data, reply);

                case TX_INPUT_TAP:
                    return handleInputTap(data, reply);

                case TX_INPUT_SWIPE:
                    return handleInputSwipe(data, reply);

                case TX_INPUT_KEY:
                    return handleInputKey(data, reply);

                case TX_EXEC_SHELL:
                    return handleExecShell(data, reply);

                default:
                    log("未知transact code: " + code);
                    return super.onTransact(code, data, reply, flags);
            }
        } catch (Exception e) {
            log("onTransact异常: " + e.getMessage());
            e.printStackTrace();
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleCreateVirtualDisplay(Parcel data, Parcel reply) {
        try {
            String name = data.readString();
            int width = data.readInt();
            int height = data.readInt();
            int density = data.readInt();
            int flags = data.readInt();

            Surface surface = Surface.CREATOR.createFromParcel(data);

            log("创建虚拟显示: " + name + " " + width + "x" + height + " density=" + density + " flags=" + flags);

            if (surface == null) {
                reply.writeInt(-1);
                reply.writeString("Surface is null");
                return true;
            }

            VirtualDisplay vd = displayManager.createVirtualDisplay(
                    name, width, height, density, surface, flags);

            if (vd == null) {
                log("createVirtualDisplay返回null!");
                reply.writeInt(-1);
                reply.writeString("createVirtualDisplay returned null");
                return true;
            }

            int displayId = vd.getDisplay().getDisplayId();
            virtualDisplays.put(displayId, vd);
            log("虚拟显示创建成功! ID=" + displayId);

            reply.writeInt(0);
            reply.writeInt(displayId);
            return true;

        } catch (Exception e) {
            log("创建虚拟显示异常: " + e.getMessage());
            e.printStackTrace();
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleReleaseVirtualDisplay(Parcel data, Parcel reply) {
        try {
            int displayId = data.readInt();
            VirtualDisplay vd = virtualDisplays.remove(displayId);
            if (vd != null) {
                vd.release();
                log("释放虚拟显示: " + displayId);
            }
            reply.writeInt(0);
            return true;
        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleLaunchApp(Parcel data, Parcel reply) {
        try {
            int displayId = data.readInt();
            String packageName = data.readString();
            log("启动应用: " + packageName + " -> display " + displayId);

            execShell("am force-stop " + packageName);
            Thread.sleep(200);

            String cmd = "am start --display " + displayId +
                    " --windowingMode 1 --activity-multiple-task -f 0x10000000 " +
                    packageName;

            String lastResult = "";
            for (int retry = 0; retry < 26; retry++) {
                String result = execShell(cmd);
                lastResult = result;
                if (result.contains("Starting") || result.contains("ok")) {
                    log("启动成功 (重试" + retry + "次)");
                    reply.writeInt(0);
                    reply.writeString(result);
                    return true;
                }
                if (retry < 25) Thread.sleep(500);
            }

            log("启动失败: " + lastResult);
            reply.writeInt(-1);
            reply.writeString(lastResult);
            return true;

        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleInputTap(Parcel data, Parcel reply) {
        try {
            int displayId = data.readInt();
            int x = data.readInt();
            int y = data.readInt();
            String result = execSimple("input", "-d", String.valueOf(displayId), "tap", String.valueOf(x), String.valueOf(y));
            reply.writeInt(0);
            reply.writeString(result);
            return true;
        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleInputSwipe(Parcel data, Parcel reply) {
        try {
            int displayId = data.readInt();
            int x1 = data.readInt();
            int y1 = data.readInt();
            int x2 = data.readInt();
            int y2 = data.readInt();
            int duration = data.readInt();
            String result = execSimple("input", "-d", String.valueOf(displayId), "swipe",
                    String.valueOf(x1), String.valueOf(y1),
                    String.valueOf(x2), String.valueOf(y2),
                    String.valueOf(duration));
            reply.writeInt(0);
            reply.writeString(result);
            return true;
        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleInputKey(Parcel data, Parcel reply) {
        try {
            int displayId = data.readInt();
            String keyCode = data.readString();
            String result = execSimple("input", "-d", String.valueOf(displayId), "keyevent", keyCode);
            reply.writeInt(0);
            reply.writeString(result);
            return true;
        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private boolean handleExecShell(Parcel data, Parcel reply) {
        try {
            String cmd = data.readString();
            String result = execShell(cmd);
            reply.writeInt(0);
            reply.writeString(result);
            return true;
        } catch (Exception e) {
            reply.writeInt(-1);
            reply.writeString(e.getMessage());
            return true;
        }
    }

    private static String execShell(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static String execSimple(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static void log(String msg) {
        System.out.println("[HelperDaemon] " + msg);
    }
}
