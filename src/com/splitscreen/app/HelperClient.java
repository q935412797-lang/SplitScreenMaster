package com.splitscreen.app;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;

import java.lang.reflect.Method;

public class HelperClient {

    private static final String SERVICE_NAME = "splitscreen_helper";
    private static final String INTERFACE_TOKEN = "com.splitscreen.app.IHelper";

    // Transact codes (跟AutoTark HelperBinderProtocol一致)
    private static final int TX_PING = 0x1;
    private static final int TX_CREATE_VIRTUAL_DISPLAY = 0x6;
    private static final int TX_RELEASE_VIRTUAL_DISPLAY = 0x7;
    private static final int TX_LAUNCH_APP_ON_DISPLAY = 0x36;
    private static final int TX_INPUT_TAP = 0x1c;
    private static final int TX_INPUT_SWIPE = 0x1d;
    private static final int TX_INPUT_KEY = 0x1f;
    private static final int TX_EXEC_SHELL = 0x1a;

    private IBinder binder;
    private Callback callback;

    public interface Callback {
        void onLog(String msg);
        void onConnected();
        void onDisconnected();
    }

    public HelperClient(Callback callback) {
        this.callback = callback;
    }

    // 跟AutoTark一致：通过ServiceManager.getService获取Binder
    public boolean connect() {
        try {
            log("通过ServiceManager获取服务: " + SERVICE_NAME);
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            binder = (IBinder) getService.invoke(null, SERVICE_NAME);

            if (binder == null) {
                log("获取Binder失败！请确认Helper守护进程已启动");
                return false;
            }

            log("Binder获取成功: " + binder.getClass().getName());

            // 测试PING
            String pong = ping();
            if ("PONG".equals(pong)) {
                log("连接成功！PING -> PONG");
                if (callback != null) callback.onConnected();
                return true;
            } else {
                log("PING失败: " + pong);
                return false;
            }
        } catch (Exception e) {
            log("连接异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        return binder != null && binder.pingBinder();
    }

    public String ping() {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            binder.transact(TX_PING, data, reply, 0);
            int code = reply.readInt();
            String result = reply.readString();
            data.recycle();
            reply.recycle();
            return code == 0 ? result : "ERROR: " + result;
        } catch (Exception e) {
            return "EXCEPTION: " + e.getMessage();
        }
    }

    // 跟AutoTark一致：通过Binder传递Surface创建虚拟显示
    public int createVirtualDisplay(String name, int width, int height, int density, int flags, Surface surface) {
        try {
            log("创建虚拟显示: " + name + " " + width + "x" + height);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeString(name);
            data.writeInt(width);
            data.writeInt(height);
            data.writeInt(density);
            data.writeInt(flags);

            // 跟AutoTark一致：Surface通过Parcel传递
            if (surface != null) {
                surface.writeToParcel(data, 0);
            } else {
                data.writeStrongBinder(null);
            }

            binder.transact(TX_CREATE_VIRTUAL_DISPLAY, data, reply, 0);
            int code = reply.readInt();
            int displayId = -1;
            if (code == 0) {
                displayId = reply.readInt();
                log("虚拟显示创建成功! ID=" + displayId);
            } else {
                String err = reply.readString();
                log("虚拟显示创建失败: " + err);
            }
            data.recycle();
            reply.recycle();
            return displayId;
        } catch (Exception e) {
            log("创建虚拟显示异常: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public void releaseVirtualDisplay(int displayId) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeInt(displayId);
            binder.transact(TX_RELEASE_VIRTUAL_DISPLAY, data, reply, 0);
            data.recycle();
            reply.recycle();
            log("释放虚拟显示: " + displayId);
        } catch (Exception e) {
            log("释放异常: " + e.getMessage());
        }
    }

    public String launchApp(int displayId, String packageName) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeInt(displayId);
            data.writeString(packageName);
            binder.transact(TX_LAUNCH_APP_ON_DISPLAY, data, reply, 0);
            int code = reply.readInt();
            String result = reply.readString();
            data.recycle();
            reply.recycle();
            log("启动应用 " + packageName + ": " + (code == 0 ? "成功" : "失败 - " + result));
            return result;
        } catch (Exception e) {
            return "EXCEPTION: " + e.getMessage();
        }
    }

    public void inputTap(int displayId, int x, int y) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeInt(displayId);
            data.writeInt(x);
            data.writeInt(y);
            binder.transact(TX_INPUT_TAP, data, reply, 0);
            data.recycle();
            reply.recycle();
        } catch (Exception e) {
            log("Tap异常: " + e.getMessage());
        }
    }

    public void inputSwipe(int displayId, int x1, int y1, int x2, int y2, int duration) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeInt(displayId);
            data.writeInt(x1);
            data.writeInt(y1);
            data.writeInt(x2);
            data.writeInt(y2);
            data.writeInt(duration);
            binder.transact(TX_INPUT_SWIPE, data, reply, 0);
            data.recycle();
            reply.recycle();
        } catch (Exception e) {
            log("Swipe异常: " + e.getMessage());
        }
    }

    public void inputKey(int displayId, String keyCode) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeInt(displayId);
            data.writeString(keyCode);
            binder.transact(TX_INPUT_KEY, data, reply, 0);
            data.recycle();
            reply.recycle();
        } catch (Exception e) {
            log("Key异常: " + e.getMessage());
        }
    }

    public String execShell(String cmd) {
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken(INTERFACE_TOKEN);
            data.writeString(cmd);
            binder.transact(TX_EXEC_SHELL, data, reply, 0);
            int code = reply.readInt();
            String result = reply.readString();
            data.recycle();
            reply.recycle();
            return result;
        } catch (Exception e) {
            return "EXCEPTION: " + e.getMessage();
        }
    }

    private void log(String msg) {
        if (callback != null) callback.onLog(msg);
    }
}
