package com.wxnotify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "wx_notify_alert";
    private static final String PREFS_NAME = "wx_notify_main";
    private static final String KEY_DEBUG = "debug_enabled";
    private static final String KEY_DEBUG_LOG = "debug_log";
    private static final int MAX_LOG = 10;

    private EditText etKeywords;
    private Button btnSave, btnCheckService, btnDebug, btnClearDebug;
    private TextView tvStatus, tvDebug;
    private LinearLayout panelDebug;
    private boolean debugEnabled = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showToast("通知权限已授予");
                } else {
                    showToast("通知权限被拒绝");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        initViews();
        loadSettings();

        // 设置服务监听回调
        WxNotifyService.setListener((title, content, keyword) -> {
            runOnUiThread(() -> triggerAlert(title, content, keyword));
        });

        checkServiceStatus();
    }

    private void initViews() {
        etKeywords = findViewById(R.id.et_keywords);
        btnSave = findViewById(R.id.btn_save);
        btnCheckService = findViewById(R.id.btn_check_service);
        btnDebug = findViewById(R.id.btn_debug);
        btnClearDebug = findViewById(R.id.btn_clear_debug);
        tvStatus = findViewById(R.id.tv_status);
        tvDebug = findViewById(R.id.tv_debug);
        panelDebug = findViewById(R.id.panel_debug);

        btnSave.setOnClickListener(v -> saveKeywords());
        btnCheckService.setOnClickListener(v -> openNotificationAccess());
        btnDebug.setOnClickListener(v -> toggleDebug());
        btnClearDebug.setOnClickListener(v -> clearDebugLog());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "微信关键词提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("当微信消息包含关键词时发出提醒");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void loadSettings() {
        String keywords = getSharedPreferences(WxNotifyService.PREFS_NAME, MODE_PRIVATE)
                .getString(WxNotifyService.KEY_KEYWORDS, "");
        etKeywords.setText(keywords);

        debugEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DEBUG, false);
        updateDebugPanel();
    }

    private void saveKeywords() {
        String keywords = etKeywords.getText().toString().trim();
        getSharedPreferences(WxNotifyService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(WxNotifyService.KEY_KEYWORDS, keywords)
                .apply();
        showToast("关键词已保存: " + keywords.replace(",", " | "));
    }

    private void checkServiceStatus() {
        boolean enabled = isNotificationListenerEnabled();
        if (enabled) {
            tvStatus.setText("状态：服务已开启");
            tvStatus.setTextColor(0xFF4CAF50);
        } else {
            tvStatus.setText("状态：服务未开启，请点击下方按钮授权");
            tvStatus.setTextColor(0xFFFF5722);
        }
    }

    private boolean isNotificationListenerEnabled() {
        ComponentName cn = new ComponentName(this, WxNotifyService.class);
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private void openNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "请在设置中找到「微信通知监控」并开启", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "请手动找到「通知使用权」设置", Toast.LENGTH_LONG).show();
            }
        } else {
            showToast("服务已开启，无需重复授权");
        }
        checkServiceStatus();
    }

    /**
     * 触发提醒：震动 + 铃声（不发通知栏弹窗）
     */
    private void triggerAlert(String title, String content, String keyword) {
        // 更新主界面显示
        tvStatus.setText("关键词命中！[" + keyword + "]\n来自: " + title);
        tvStatus.setTextColor(0xFFE91E63);

        // 震动
        vibrate();

        // 播放系统提示音
        playSystemSound();
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 500, 200, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
            }
        }
    }

    private void playSystemSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(this, notification);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    // ========== 调试面板 ==========

    private void toggleDebug() {
        debugEnabled = !debugEnabled;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_DEBUG, debugEnabled).apply();

        if (debugEnabled) {
            btnDebug.setText("关闭调试");
            btnDebug.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            panelDebug.setVisibility(View.VISIBLE);
        } else {
            btnDebug.setText("调试");
            btnDebug.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9E9E9E));
            panelDebug.setVisibility(View.GONE);
        }
    }

    private void updateDebugPanel() {
        if (!debugEnabled) return;
        String log = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_DEBUG_LOG, "");
        tvDebug.setText(log.isEmpty() ? "暂无记录" : log);
    }

    /** 由 WxNotifyService 调用，在后台线程添加拦截记录 */
    public static void addDebugLog(android.app.Activity activity, String pkg, String title, String content) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String existing = prefs.getString(KEY_DEBUG_LOG, "");
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = time + " [" + pkg + "]\n  " + title + "\n  " + content + "\n";
            String updated = (existing.isEmpty() ? "" : existing + "─────────────────\n") + line;

            // 保留最近 MAX_LOG 条（简单计数）
            int count = 0;
            int pos = 0;
            while (count < MAX_LOG && pos < updated.length()) {
                int sep = updated.indexOf("─────────────────\n", pos);
                if (sep < 0) break;
                pos = sep + 18;
                count++;
            }
            if (count >= MAX_LOG) {
                int cutoff = updated.indexOf("─────────────────\n");
                if (cutoff >= 0) {
                    updated = updated.substring(cutoff + 18);
                }
            }

            prefs.edit().putString(KEY_DEBUG_LOG, updated).apply();

            // 如果 MainActivity 可见，更新显示
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).tvDebug.setText(updated.isEmpty() ? "暂无记录" : updated);
            }
        });
    }

    private void clearDebugLog() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_DEBUG_LOG, "").apply();
        tvDebug.setText("暂无记录");
        showToast("调试日志已清空");
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
    }
}
