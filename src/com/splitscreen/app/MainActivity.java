package com.splitscreen.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements HelperClient.Callback {

    public static HelperClient helperClient;
    private Handler mainHandler;
    private TextView tvStatus;
    private TextView tvLog;
    private EditText etHost;
    private EditText etPort;
    private Button btnConnect;

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private int virtualDisplayId = -1;
    private BroadcastReceiver vdisplayReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        helperClient = new HelperClient(this);

        vdisplayReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.splitscreen.app.VDISPLAY_CREATED".equals(action)) {
                    virtualDisplayId = intent.getIntExtra("display_id", -1);
                    appendLog("收到广播：虚拟显示创建成功！ID=" + virtualDisplayId);
                    updateStatus();
                    Toast.makeText(MainActivity.this, "虚拟显示创建成功！ID=" + virtualDisplayId, Toast.LENGTH_SHORT).show();
                } else if ("com.splitscreen.app.VDISPLAY_RELEASED".equals(action)) {
                    virtualDisplayId = -1;
                    appendLog("收到广播：虚拟显示已释放");
                    updateStatus();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.splitscreen.app.VDISPLAY_CREATED");
        filter.addAction("com.splitscreen.app.VDISPLAY_RELEASED");
        registerReceiver(vdisplayReceiver, filter);

        buildUI();
        appendLog("APP启动完成");
        appendLog("请先在电脑上运行一键启动Helper.bat");
        appendLog("然后点下面的'连接Helper'按钮");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                appendLog("授权成功！启动前台服务创建虚拟显示...");
                Intent serviceIntent = new Intent(this, MediaProjectionService.class);
                serviceIntent.setAction(MediaProjectionService.ACTION_START);
                serviceIntent.putExtra(MediaProjectionService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(MediaProjectionService.EXTRA_RESULT_DATA, data);
                startService(serviceIntent);
            } else {
                appendLog("授权失败或被取消");
                Toast.makeText(this, "需要授权才能创建虚拟显示", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#16213E"));
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("分屏大师 v17.0 (Binder架构)");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("状态: 未连接 | 虚拟显示: 无");
        tvStatus.setTextColor(Color.RED);
        tvStatus.setTextSize(14);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, 16);
        root.addView(tvStatus);

        LinearLayout addrRow = new LinearLayout(this);
        addrRow.setOrientation(LinearLayout.HORIZONTAL);
        addrRow.setPadding(0, 0, 0, 8);

        etHost = new EditText(this);
        etHost.setText("127.0.0.1");
        etHost.setTextColor(Color.WHITE);
        etHost.setHint("地址");
        etHost.setHintTextColor(Color.GRAY);
        etHost.setBackgroundColor(Color.parseColor("#1A1A2E"));
        etHost.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        addrRow.addView(etHost, hostParams);

        etPort = new EditText(this);
        etPort.setText("3939");
        etPort.setTextColor(Color.WHITE);
        etPort.setHint("端口");
        etPort.setHintTextColor(Color.GRAY);
        etPort.setBackgroundColor(Color.parseColor("#1A1A2E"));
        etPort.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        addrRow.addView(etPort, portParams);

        root.addView(addrRow);

        btnConnect = new Button(this);
        btnConnect.setText("连接Helper");
        btnConnect.setTextColor(Color.WHITE);
        btnConnect.setBackgroundColor(Color.parseColor("#E94560"));
        btnConnect.setPadding(0, 16, 0, 16);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (helperClient.isConnected()) {
                } else {
                    String host = etHost.getText().toString().trim();
                    int port = Integer.parseInt(etPort.getText().toString().trim());
                    appendLog("点击连接: " + host + ":" + port);
                    helperClient.connect();
                }
            }
        });
        root.addView(btnConnect);

        TextView mpTitle = new TextView(this);
        mpTitle.setText("虚拟显示（MediaProjection）");
        mpTitle.setTextColor(Color.parseColor("#00E5FF"));
        mpTitle.setTextSize(16);
        mpTitle.setPadding(0, 20, 0, 8);
        root.addView(mpTitle);

        LinearLayout mpRow1 = new LinearLayout(this);
        mpRow1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnAuthVD = createTestButton("授权并创建虚拟显示", "#00695C");
        btnAuthVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualDisplayId >= 0) {
                    appendLog("虚拟显示已存在，ID=" + virtualDisplayId);
                    return;
                }
                appendLog("请求MediaProjection授权...");
                android.media.projection.MediaProjectionManager manager =
                        (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                Intent intent = manager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
            }
        });
        mpRow1.addView(btnAuthVD);

        Button btnReleaseVD = createTestButton("释放虚拟显示", "#B71C1C");
        btnReleaseVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                releaseVirtualDisplay();
            }
        });
        mpRow1.addView(btnReleaseVD);

        root.addView(mpRow1);

        LinearLayout mpRow2 = new LinearLayout(this);
        mpRow2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnOpenVD = createTestButton("打开分屏界面", "#E91E63");
        btnOpenVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendLog("打开分屏界面...");
                Intent intent = new Intent(MainActivity.this, VirtualDisplayActivity.class);
                startActivity(intent);
            }
        });
        mpRow2.addView(btnOpenVD);

        Button btnPresets = createTestButton("分屏预设", "#9C27B0");
        btnPresets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPresetList();
            }
        });
        mpRow2.addView(btnPresets);
        root.addView(mpRow2);

        TextView vdOpTitle = new TextView(this);
        vdOpTitle.setText("虚拟显示操作");
        vdOpTitle.setTextColor(Color.parseColor("#FFD54F"));
        vdOpTitle.setTextSize(16);
        vdOpTitle.setPadding(0, 16, 0, 8);
        root.addView(vdOpTitle);

        LinearLayout vdRow1 = new LinearLayout(this);
        vdRow1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnStartSettingsVD = createTestButton("设置→虚拟显示", "#1B5E20");
        btnStartSettingsVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualDisplayId < 0) {
                    appendLog("请先创建虚拟显示！");
                    return;
                }
                helperClient.launchApp(virtualDisplayId, "com.android.settings");
            }
        });
        vdRow1.addView(btnStartSettingsVD);

        Button btnTapVD = createTestButton("点击虚拟显示中心", "#1B5E20");
        btnTapVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualDisplayId < 0) {
                    appendLog("请先创建虚拟显示！");
                    return;
                }
                helperClient.inputTap(virtualDisplayId, 300, 400);
            }
        });
        vdRow1.addView(btnTapVD);

        root.addView(vdRow1);

        LinearLayout vdRow2 = new LinearLayout(this);
        vdRow2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSwipeVD = createTestButton("虚拟显示下滑", "#E65100");
        btnSwipeVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualDisplayId < 0) {
                    appendLog("请先创建虚拟显示！");
                    return;
                }
                helperClient.inputSwipe(virtualDisplayId, 300, 50, 300, 600, 300);
            }
        });
        vdRow2.addView(btnSwipeVD);

        Button btnBackVD = createTestButton("虚拟显示返回键", "#E65100");
        btnBackVD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (virtualDisplayId < 0) {
                    appendLog("请先创建虚拟显示！");
                    return;
                }
                helperClient.inputKey(virtualDisplayId, "4");
            }
        });
        vdRow2.addView(btnBackVD);

        root.addView(vdRow2);

        TextView testTitle = new TextView(this);
        testTitle.setText("基础功能测试");
        testTitle.setTextColor(Color.WHITE);
        testTitle.setTextSize(16);
        testTitle.setPadding(0, 16, 0, 8);
        root.addView(testTitle);

        LinearLayout btnRow1 = new LinearLayout(this);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPing = createTestButton("PING", "#0F3460");
        btnPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pong = helperClient.ping(); appendLog("PING: " + pong);
            }
        });
        btnRow1.addView(btnPing);

        Button btnDisplays = createTestButton("显示列表", "#0F3460");
        btnDisplays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String displays = helperClient.execShell("dumpsys display | grep mDisplayId"); appendLog("DISPLAYS: " + displays);
            }
        });
        btnRow1.addView(btnDisplays);

        root.addView(btnRow1);

        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);

        Button btnStartApp = createTestButton("启动设置(主屏)", "#1B5E20");
        btnStartApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helperClient.launchApp(0, "com.android.settings");
            }
        });
        btnRow2.addView(btnStartApp);

        Button btnClear = createTestButton("清空日志", "#533483");
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvLog.setText("");
            }
        });
        btnRow2.addView(btnClear);

        root.addView(btnRow2);

        TextView logTitle = new TextView(this);
        logTitle.setText("运行日志");
        logTitle.setTextColor(Color.WHITE);
        logTitle.setTextSize(18);
        logTitle.setPadding(0, 24, 0, 8);
        root.addView(logTitle);

        ScrollView logScroll = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setTextColor(Color.parseColor("#00FF88"));
        tvLog.setTextSize(12);
        tvLog.setPadding(16, 12, 16, 12);
        tvLog.setBackgroundColor(Color.BLACK);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        logScroll.addView(tvLog);

        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        logParams.setMargins(0, 0, 0, 0);
        root.addView(logScroll, logParams);

        setContentView(root);
    }

    private void releaseVirtualDisplay() {
        try {
            Intent serviceIntent = new Intent(this, MediaProjectionService.class);
            serviceIntent.setAction(MediaProjectionService.ACTION_STOP);
            startService(serviceIntent);
            appendLog("已发送释放虚拟显示命令");
            Toast.makeText(this, "正在释放虚拟显示...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            appendLog("释放虚拟显示失败: " + e.getMessage());
        }
    }

    private void updateStatus() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String conn = helperClient.isConnected() ? "已连接" : "未连接";
                String vd = virtualDisplayId >= 0 ? "虚拟显示ID=" + virtualDisplayId : "虚拟显示: 无";
                tvStatus.setText("状态: " + conn + " | " + vd);
                tvStatus.setTextColor(helperClient.isConnected() ? Color.GREEN : Color.RED);
            }
        });
    }

    private Button createTestButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(color));
        btn.setPadding(0, 12, 0, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(params);
        return btn;
    }

    private void appendLog(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvLog.append(msg + "\n");
            }
        });
    }

    @Override
    public void onLog(String msg) {
        appendLog(msg);
    }

    @Override
    public void onConnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                btnConnect.setText("断开连接");
                Toast.makeText(MainActivity.this, "连接成功！", Toast.LENGTH_SHORT).show();
                updateStatus();
            }
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                btnConnect.setText("连接Helper");
                updateStatus();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vdisplayReceiver != null) {
            unregisterReceiver(vdisplayReceiver);
        }
    }

    private void showPresetList() {
        final java.util.List<PresetManager.Preset> presets = PresetManager.loadPresets(this);
        if (presets.isEmpty()) {
            Toast.makeText(this, "暂无保存的预设\n（在分屏界面长按右上角⋮保存）", Toast.LENGTH_LONG).show();
            return;
        }

        final String[] names = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            PresetManager.Preset p = presets.get(i);
            StringBuilder sb = new StringBuilder(p.name + "\n");
            for (int j = 0; j < 3; j++) {
                if (p.packages[j] != null) sb.append("  W").append(j+1).append(": ").append(p.packages[j]).append("\n");
            }
            names[i] = sb.toString().trim();
        }

        new AlertDialog.Builder(this)
            .setTitle("选择分屏预设（点击启动，长按删除）")
            .setItems(names, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    PresetManager.Preset preset = presets.get(which);
                    appendLog("启动预设: " + preset.name);
                    Intent intent = new Intent(MainActivity.this, VirtualDisplayActivity.class);
                    intent.putExtra("preset_name", preset.name);
                    intent.putExtra("preset_packages", preset.packages);
                    intent.putExtra("preset_fractions", preset.fractions);
                    startActivity(intent);
                }
            })
            .setNeutralButton("删除预设", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    final String[] deleteNames = new String[presets.size()];
                    for (int i = 0; i < presets.size(); i++) deleteNames[i] = presets.get(i).name;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择要删除的预设")
                        .setItems(deleteNames, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                PresetManager.deletePreset(MainActivity.this, deleteNames[w]);
                                Toast.makeText(MainActivity.this, "已删除: " + deleteNames[w], Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
