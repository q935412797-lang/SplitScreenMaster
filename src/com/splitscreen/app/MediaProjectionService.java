package com.splitscreen.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaProjectionService extends Service {

    private static final String TAG = "MediaProjectionService";
    private static final String CHANNEL_ID = "splitscreen_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.splitscreen.app.START_PROJECTION";
    public static final String ACTION_STOP = "com.splitscreen.app.STOP_PROJECTION";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    public static MediaProjection mediaProjection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logToFile("Service onStartCommand");

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        logToFile("action=" + action);

        try {
            if (ACTION_START.equals(action)) {
                startForeground(NOTIFICATION_ID, buildNotification());
                logToFile("startForeground 调用成功！");

                int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
                Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                logToFile("resultCode=" + resultCode + ", resultData=" + (resultData != null ? "not null" : "null"));

                if (resultCode != 0 && resultData != null) {
                    MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    mediaProjection = manager.getMediaProjection(resultCode, resultData);
                    logToFile("MediaProjection获取成功: " + (mediaProjection != null ? "not null" : "null"));

                    Intent broadcast = new Intent("com.splitscreen.app.PROJECTION_READY");
                    sendBroadcast(broadcast);
                    logToFile("已发送PROJECTION_READY广播");
                }

            } else if (ACTION_STOP.equals(action)) {
                logToFile("停止服务");
                if (mediaProjection != null) {
                    mediaProjection.stop();
                    mediaProjection = null;
                }
                stopSelf();
            }
        } catch (Exception e) {
            logToFile("异常: " + e.getMessage());
            logToFile("异常堆栈: " + Log.getStackTraceString(e));
            e.printStackTrace();
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "分屏大师服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于创建虚拟显示的前台服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("分屏大师")
                .setContentText("虚拟显示服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true);

        return builder.build();
    }

    private void logToFile(String msg) {
        try {
            File dir = new File(android.os.Environment.getExternalStorageDirectory(), "SplitScreenLogs");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "service_log.txt");
            FileWriter writer = new FileWriter(file, true);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            writer.write("[" + time + "] " + msg + "\n");
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
