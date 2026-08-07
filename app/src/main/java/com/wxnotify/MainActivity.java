package com.wxnotify;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationChannel;
import android.content.ComponentName;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "wx_notify_alert";
    private static final String PREFS_NAME = "wx_notify_main";
    private static final String KEY_DEBUG = "debug_enabled";
    private static final String KEY_DEBUG_LOG = "debug_log";
    private static final String KEY_VIBRATE = "vibrate_on";
    private static final String KEY_RING = "ring_on";

    private EditText etKeywords;
    private Button btnSave, btnCheckService, btnDebug, btnClearDebug;
    private SwitchCompat switchVibrate, switchRing;
    private TextView tvStatus, tvDebug;
    private LinearLayout panelDebug;
    private boolean debugEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        initViews();
        loadSettings();

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
        switchVibrate = findViewById(R.id.switch_vibrate);
        switchRing = findViewById(R.id.switch_ring);

        btnSave.setOnClickListener(v -> saveSettings());
        btnCheckService.setOnClickListener(v -> openNotificationAccess());
        btnDebug.setOnClickListener(v -> toggleDebug());

        SharedPreferences mainPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        switchVibrate.setChecked(mainPrefs.getBoolean(KEY_VIBRATE, true));
        switchRing.setChecked(mainPrefs.getBoolean(KEY_RING, true));

        switchVibrate.setOnCheckedChangeListener((v, isChecked) -> saveSwitches());
        switchRing.setOnCheckedChangeListener((v, isChecked) -> saveSwitches());

        btnClearDebug.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_DEBUG_LOG, "").apply();
            tvDebug.setText("暂无记录");
            showToast("调试日志已清空");
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "微信关键词提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("当微信消息包含关键词时发出提醒");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void loadSettings() {
        SharedPreferences svcPrefs = getSharedPreferences(WxNotifyService.PREFS_NAME, MODE_PRIVATE);
        etKeywords.setText(svcPrefs.getString(WxNotifyService.KEY_KEYWORDS, ""));

        SharedPreferences mainPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        debugEnabled = mainPrefs.getBoolean(KEY_DEBUG, false);
        updateDebugPanel();
    }

    private void saveSettings() {
        String keywords = etKeywords.getText().toString().trim();
        getSharedPreferences(WxNotifyService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(WxNotifyService.KEY_KEYWORDS, keywords)
                .apply();
        showToast("关键词已保存");
    }

    private void saveSwitches() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VIBRATE, switchVibrate.isChecked())
                .putBoolean(KEY_RING, switchRing.isChecked())
                .apply();
    }

    private void checkServiceStatus() {
        boolean enabled = isNotificationListenerEnabled();
        tvStatus.setText(enabled ? "状态：服务已开启" : "状态：服务未开启，请点击开启服务");
        tvStatus.setTextColor(enabled ? 0xFF4CAF50 : 0xFFFF5722);
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
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                Toast.makeText(this, "请手动找到「通知使用权」设置", Toast.LENGTH_LONG).show();
            }
        } else {
            showToast("服务已开启");
        }
        checkServiceStatus();
    }

    private void triggerAlert(String title, String content, String keyword) {
        tvStatus.setText("命中：" + keyword + "\n" + title + "\n" + content);
        tvStatus.setTextColor(0xFFE91E63);

        sendKeywordNotification(keyword, title, content);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_VIBRATE, true)) vibrate();
        if (prefs.getBoolean(KEY_RING, true)) playRing();
    }

    private void sendKeywordNotification(String keyword, String title, String content) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("【" + keyword + "】" + title)
                    .setContentText(content.length() > 60 ? content.substring(0, 60) + "…" : content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle()
                        .bigText(content);
                builder.setStyle(style);
            }

            NotificationManagerCompat.from(this).notify(1, builder.build());
        } catch (Exception e) {
            // 通知失败不影响震动和铃声
        }
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

    private void playRing() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(this, notification);
            if (ringtone != null) ringtone.play();
        } catch (Exception e) { /* ignore */ }
    }

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
        updateDebugPanel();
    }

    private void updateDebugPanel() {
        if (!debugEnabled) return;
        String log = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_DEBUG_LOG, "");
        tvDebug.setText(log.isEmpty() ? "暂无记录" : log);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
        updateDebugPanel();
    }
}
