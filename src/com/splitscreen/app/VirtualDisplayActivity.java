package com.splitscreen.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VirtualDisplayActivity extends Activity {
    private String[] presetPkgs;

    private static final String TAG = "TripleDisplay";

    private static final int VD_WIDTH = 600;
    private static final int VD_HEIGHT = 800;
    private static final int VD_DENSITY = 240;

    private TextureView[] textureViews = new TextureView[3];
    private SurfaceTexture[] surfaceTextures = new SurfaceTexture[3];
    private Surface[] surfaces = new Surface[3];
    private VirtualDisplay[] virtualDisplays = new VirtualDisplay[3];
    private int[] displayIds = {-1, -1, -1};
    private boolean[] displayReady = {false, false, false};
    private String[] currentPackages = {null, null, null};

    private boolean isLandscape = false;
    private List<AppInfo> installedApps;

    private float[] windowFractions = {1f/3f, 1f/3f, 1f/3f};
    private static final float MIN_FRACTION = 0.15f;

    private LinearLayout mainLayout;
    private FrameLayout[] windowFrames = new FrameLayout[3];
    private View[] dividers = new View[2];

    private int maximizedWindow = -1;
    private float[] savedFractions = {1f/3f, 1f/3f, 1f/3f};
    private long lastDoubleClickTime = 0;

    private static class AppInfo {
        String name;
        String packageName;
        AppInfo(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int orientation = getResources().getConfiguration().orientation;
        isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        Log.d(TAG, "屏幕方向: " + (isLandscape ? "横屏" : "竖屏"));

        loadInstalledApps();

        try {
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(0xFF000000);

            mainLayout = new LinearLayout(this);
            if (isLandscape) {
                mainLayout.setOrientation(LinearLayout.HORIZONTAL);
            } else {
                mainLayout.setOrientation(LinearLayout.VERTICAL);
            }
            mainLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            for (int i = 0; i < 3; i++) {
                windowFrames[i] = createWindowFrame(i);
                updateWindowLayoutParams(i);
                mainLayout.addView(windowFrames[i]);

                if (i < 2) {
                    dividers[i] = createDraggableDivider(i);
                    mainLayout.addView(dividers[i]);
                }
            }

            root.addView(mainLayout);

            TextView hintText = new TextView(this);
            hintText.setText("三分屏 v17.0 - 双击窗口最大化/还原 | 拖动调大小 | ⋮选APP");
            hintText.setTextColor(0x80FFFFFF);
            hintText.setTextSize(10);
            hintText.setPadding(8, 4, 8, 4);
            hintText.setBackgroundColor(0x40000000);
            FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            hintParams.gravity = Gravity.TOP | Gravity.LEFT;
            hintParams.setMargins(8, 8, 0, 0);
            hintText.setLayoutParams(hintParams);
            root.addView(hintText);

            LinearLayout bottomBar = new LinearLayout(this);
            bottomBar.setOrientation(LinearLayout.HORIZONTAL);
            bottomBar.setBackgroundColor(0xE01A1A2E);
            bottomBar.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, 100);
            bottomParams.gravity = Gravity.BOTTOM;
            bottomBar.setLayoutParams(bottomParams);

            final int[] selectedWindow = {0};

            String[] btnNames = {"关闭APP", "主页", "返回", "W1", "W2", "W3"};
            String[] btnColors = {"#F44336", "#4CAF50", "#2196F3", "#FF9800", "#FF9800", "#FF9800"};
            for (int i = 0; i < btnNames.length; i++) {
                final int btnIndex = i;
                Button btn = new Button(this);
                btn.setText(btnNames[i]);
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(12);
                btn.setBackgroundColor(Color.parseColor(btnColors[i]));
                btn.setPadding(8, 4, 8, 4);
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                btnParams.setMargins(2, 4, 2, 4);
                btn.setLayoutParams(btnParams);

                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int winIdx = selectedWindow[0];
                        if (displayIds[winIdx] < 0) {
                            Toast.makeText(VirtualDisplayActivity.this, "请先选择有内容的窗口", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        switch (btnIndex) {
                            case 0:
                                if (currentPackages[winIdx] != null) {
                                    sendCommand("SHELL|am force-stop --display " + displayIds[winIdx] + " " + currentPackages[winIdx]);
                                    currentPackages[winIdx] = null;
                                    updateWindowLabel(winIdx);
                                    Toast.makeText(VirtualDisplayActivity.this, "已关闭窗口" + (winIdx+1) + "的APP", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            case 1:
                                sendCommand("KEYEVENT_DISPLAY|" + displayIds[winIdx] + "|3");
                                break;
                            case 2:
                                sendCommand("KEYEVENT_DISPLAY|" + displayIds[winIdx] + "|4");
                                break;
                            case 3: case 4: case 5:
                                selectedWindow[0] = btnIndex - 3;
                                Toast.makeText(VirtualDisplayActivity.this, "已选择窗口" + (selectedWindow[0]+1), Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                });
                bottomBar.addView(btn);
            }
            root.addView(bottomBar);

            setContentView(root);

            final String presetName = getIntent().getStringExtra("preset_name");
            presetPkgs = getIntent().getStringArrayExtra("preset_packages");
            final float[] presetFracs = getIntent().getFloatArrayExtra("preset_fractions");

            if (presetFracs != null && presetFracs.length == 3) {
                System.arraycopy(presetFracs, 0, windowFractions, 0, 3);
                for (int i = 0; i < 3; i++) updateWindowLayoutParams(i);
                mainLayout.requestLayout();
            }

            final android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    launchAppSequentially(0, handler);
                }
            }, 3000);

        } catch (Throwable e) {
            Log.e(TAG, "onCreate异常", e);
            e.printStackTrace();
            Toast.makeText(this, "界面创建异常: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateWindowLayoutParams(int index) {
        if (windowFrames[index] == null) return;
        float fraction = windowFractions[index];
        if (isLandscape) {
            windowFrames[index].setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fraction));
        } else {
            windowFrames[index].setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, fraction));
        }
    }

    private View createDraggableDivider(final int dividerIndex) {
        final View divider = new View(this);
        divider.setBackgroundColor(0xFF444444);

        final int dividerSize = 8;
        if (isLandscape) {
            divider.setLayoutParams(new LinearLayout.LayoutParams(dividerSize, LinearLayout.LayoutParams.MATCH_PARENT));
        } else {
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerSize));
        }

        divider.setOnTouchListener(new View.OnTouchListener() {
            private float startPos = 0;
            private float[] startFractions = new float[3];
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();

                if (action == MotionEvent.ACTION_DOWN) {
                    isDragging = true;
                    if (isLandscape) {
                        startPos = event.getRawX();
                    } else {
                        startPos = event.getRawY();
                    }
                    System.arraycopy(windowFractions, 0, startFractions, 0, 3);
                    divider.setBackgroundColor(0xFF00FF00);
                    return true;
                }

                if (action == MotionEvent.ACTION_MOVE && isDragging) {
                    float currentPos;
                    if (isLandscape) {
                        currentPos = event.getRawX();
                    } else {
                        currentPos = event.getRawY();
                    }

                    float screenSize;
                    if (isLandscape) {
                        screenSize = getResources().getDisplayMetrics().widthPixels;
                    } else {
                        screenSize = getResources().getDisplayMetrics().heightPixels;
                    }

                    float delta = (currentPos - startPos) / screenSize;

                    int leftIndex = dividerIndex;
                    int rightIndex = dividerIndex + 1;

                    float newLeft = startFractions[leftIndex] + delta;
                    float newRight = startFractions[rightIndex] - delta;

                    if (newLeft >= MIN_FRACTION && newRight >= MIN_FRACTION) {
                        windowFractions[leftIndex] = newLeft;
                        windowFractions[rightIndex] = newRight;
                        updateWindowLayoutParams(leftIndex);
                        updateWindowLayoutParams(rightIndex);
                        mainLayout.requestLayout();
                    }
                    return true;
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isDragging = false;
                    divider.setBackgroundColor(0xFF444444);
                    normalizeFractions();
                    Log.d(TAG, "窗口比例: " + windowFractions[0] + ", " + windowFractions[1] + ", " + windowFractions[2]);
                    return true;
                }

                return false;
            }
        });

        return divider;
    }

    private void normalizeFractions() {
        float sum = windowFractions[0] + windowFractions[1] + windowFractions[2];
        if (sum > 0) {
            for (int i = 0; i < 3; i++) {
                windowFractions[i] /= sum;
            }
        }
    }

    private void loadInstalledApps() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            installedApps = new ArrayList<>();
            for (ApplicationInfo app : apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    String name = app.loadLabel(pm).toString();
                    installedApps.add(new AppInfo(name, app.packageName));
                }
            }
            Collections.sort(installedApps, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "加载应用列表失败", e);
            installedApps = new ArrayList<>();
        }
    }

    private FrameLayout createWindowFrame(final int index) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFF000000);

        textureViews[index] = createTextureView(index);
        textureViews[index].setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(textureViews[index]);

        TextView menuBtn = new TextView(this);
        menuBtn.setText("⋮");
        menuBtn.setTextColor(0xFFFFFFFF);
        menuBtn.setTextSize(18);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setPadding(12, 4, 12, 4);
        menuBtn.setBackgroundColor(0x60000000);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        menuParams.gravity = Gravity.TOP | Gravity.RIGHT;
        menuParams.setMargins(0, 4, 4, 0);
        menuBtn.setLayoutParams(menuParams);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker(index);
            }
        });
        menuBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                saveCurrentPreset();
                return true;
            }
        });
        frame.addView(menuBtn);

        final TextView winLabel = new TextView(this);
        winLabel.setText("W" + (index + 1));
        winLabel.setTextColor(0xFFFFFFFF);
        winLabel.setTextSize(9);
        winLabel.setPadding(6, 3, 6, 3);
        winLabel.setBackgroundColor(0x80000000);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.TOP | Gravity.LEFT;
        labelParams.setMargins(4, 4, 0, 0);
        winLabel.setLayoutParams(labelParams);
        frame.addView(winLabel);

        winLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(VirtualDisplayActivity.this, "已选择窗口" + (index+1), Toast.LENGTH_SHORT).show();
            }
        });

        return frame;
    }

    private void showAppPicker(final int windowIndex) {
        if (installedApps == null || installedApps.isEmpty()) {
            Toast.makeText(this, "未找到已安装应用", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择窗口" + (windowIndex + 1) + "要启动的应用");

        ArrayAdapter<AppInfo> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, installedApps);

        builder.setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                AppInfo app = installedApps.get(which);
                launchAppToDisplay(windowIndex, app.packageName);
                Toast.makeText(VirtualDisplayActivity.this,
                        "正在启动" + app.name + "到窗口" + (windowIndex + 1),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveCurrentPreset() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("输入预设名称（如：导航+音乐+天神之眼）");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setBackgroundColor(0xFF1A1A2E);
        input.setPadding(24, 16, 24, 16);

        new AlertDialog.Builder(this)
            .setTitle("保存分屏预设")
            .setView(input)
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(VirtualDisplayActivity.this, "请输入预设名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PresetManager.Preset preset = new PresetManager.Preset(name);
                    System.arraycopy(currentPackages, 0, preset.packages, 0, 3);
                    System.arraycopy(windowFractions, 0, preset.fractions, 0, 3);
                    PresetManager.addPreset(VirtualDisplayActivity.this, preset);
                    Toast.makeText(VirtualDisplayActivity.this, "预设已保存: " + name, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void launchAppToDisplay(int windowIndex, String packageName) {
        if (displayIds[windowIndex] < 0) return;
        currentPackages[windowIndex] = packageName;
        sendCommand("START_APP_DISPLAY|" + packageName + "|" + displayIds[windowIndex]);
        updateWindowLabel(windowIndex);
    }

    private void launchAppSequentially(final int index, final android.os.Handler handler) {
        if (index >= 3) return;
        if (displayIds[index] >= 0) {
            String pkg = "com.android.settings";
            if (presetPkgs != null && index < presetPkgs.length && presetPkgs[index] != null && !presetPkgs[index].isEmpty()) {
                pkg = presetPkgs[index];
            }
            launchAppToDisplay(index, pkg);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                launchAppSequentially(index + 1, handler);
            }
        }, 800);
    }

    private void updateWindowLabel(int index) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    private TextureView createTextureView(final int index) {
        TextureView tv = new TextureView(this);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surfaceTextures[index] = surface;
                surface.setDefaultBufferSize(VD_WIDTH, VD_HEIGHT);
                displayReady[index] = true;
                tryCreateDisplay(index);
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseDisplay(index);
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        tv.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private long downTime;
            private boolean isMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (displayIds[index] < 0) return false;

                float x = event.getX();
                float y = event.getY();
                float scaleX = (float) VD_WIDTH / v.getWidth();
                float scaleY = (float) VD_HEIGHT / v.getHeight();
                int vdX = (int) (x * scaleX);
                int vdY = (int) (y * scaleY);

                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    downX = x; downY = y;
                    downTime = System.currentTimeMillis();
                    isMoved = false;

                    long now = System.currentTimeMillis();
                    if (now - lastDoubleClickTime < 300) {
                        toggleMaximize(index);
                        lastDoubleClickTime = 0;
                        return true;
                    }
                    lastDoubleClickTime = now;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20) isMoved = true;
                } else if (action == MotionEvent.ACTION_UP) {
                    long duration = System.currentTimeMillis() - downTime;
                    if (isMoved && duration > 100) {
                        int startX = (int) (downX * scaleX);
                        int startY = (int) (downY * scaleY);
                        int swipeDuration = Math.max(100, Math.min((int)duration, 1000));
                        sendCommand("SWIPE_DISPLAY|" + displayIds[index] + "|" + startX + "|" + startY + "|" + vdX + "|" + vdY + "|" + swipeDuration);
                    } else {
                        sendCommand("TAP_DISPLAY|" + displayIds[index] + "|" + vdX + "|" + vdY);
                    }
                }
                return true;
            }
        });

        return tv;
    }

    private synchronized void tryCreateDisplay(int index) {
        try {
            if (displayIds[index] >= 0) return;
            if (!displayReady[index]) return;
            if (MainActivity.helperClient == null || !MainActivity.helperClient.isConnected()) return;

            surfaces[index] = new Surface(surfaceTextures[index]);

            int displayId = MainActivity.helperClient.createVirtualDisplay(
                    "SplitScreen_VD_" + index,
                    VD_WIDTH, VD_HEIGHT, VD_DENSITY,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surfaces[index]);

            if (displayId >= 0) {
                displayIds[index] = displayId;
                Log.d(TAG, "窗口" + index + " 虚拟显示创建成功！ID=" + displayId + " (Binder+DisplayManager方式)");
            } else {
                Log.e(TAG, "窗口" + index + " 虚拟显示创建失败！");
            }
        } catch (Throwable e) {
            Log.e(TAG, "创建窗口" + index + " 异常", e);
        }
    }

    private void sendCommand(String cmd) {
        try {
            if (MainActivity.helperClient != null && MainActivity.helperClient.isConnected()) {
                MainActivity.helperClient.execShell(cmd);
            }
        } catch (Throwable e) {
            Log.e(TAG, "sendCommand异常", e);
        }
    }

    private void releaseDisplay(int index) {
        try {
            if (displayIds[index] >= 0 && MainActivity.helperClient != null) {
                MainActivity.helperClient.releaseVirtualDisplay(displayIds[index]);
            }
            if (surfaces[index] != null) {
                surfaces[index].release();
                surfaces[index] = null;
            }
            displayIds[index] = -1;
            displayReady[index] = false;
        } catch (Exception e) {}
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < 3; i++) releaseDisplay(i);
    }

    private void toggleMaximize(int index) {
        if (maximizedWindow == -1) {
            savedFractions = windowFractions.clone();
            maximizedWindow = index;

            for (int i = 0; i < 3; i++) {
                if (i != index) {
                    windowFrames[i].setVisibility(View.GONE);
                }
            }
            for (int i = 0; i < 2; i++) {
                dividers[i].setVisibility(View.GONE);
            }

            if (isLandscape) {
                windowFrames[index].setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            } else {
                windowFrames[index].setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            }
            mainLayout.requestLayout();
            Toast.makeText(this, "窗口" + (index+1) + "已最大化（双击还原）", Toast.LENGTH_SHORT).show();
        } else {
            maximizedWindow = -1;
            windowFractions = savedFractions.clone();

            for (int i = 0; i < 3; i++) {
                windowFrames[i].setVisibility(View.VISIBLE);
                updateWindowLayoutParams(i);
            }
            for (int i = 0; i < 2; i++) {
                dividers[i].setVisibility(View.VISIBLE);
            }
            mainLayout.requestLayout();
            Toast.makeText(this, "已还原三分屏", Toast.LENGTH_SHORT).show();
        }
    }
}
