package com.wxnotify;

import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "wx_notify_alert";
    private static final int NOTIFICATION_ID = 1001;

    private EditText etKeywords;
    private Button btnSave, btnCheckService;
    private TextView tvStatus;

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
        tvStatus = findViewById(R.id.tv_status);

        btnSave.setOnClickListener(v -> saveKeywords());
        btnCheckService.setOnClickListener(v -> openNotificationAccess());
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
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});

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
    }

    private void saveKeywords() {
        String keywords = etKeywords.getText().toString().trim();
        getSharedPreferences(WxNotifyService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(WxNotifyService.KEY_KEYWORDS, keywords)
                .apply();
        showToast("关键词已保存: " + keywords.replace(",", " | "));
    }

    /**
     * 检查通知监听服务是否已授权
     */
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
        if (flat != null) {
            return flat.contains(cn.flattenToString());
        }
        return false;
    }

    /**
     * 打开系统通知访问权限设置页面
     */
    private void openNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "请在设置中找到「微信通知监控」并开启", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception e) {
                // 某些设备路径不同，尝试通用方式
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
     * 触发提醒：通知 + 震动 + 铃声
     */
    private void triggerAlert(String title, String content, String keyword) {
        // 更新主界面显示
        tvStatus.setText("关键词命中！[" + keyword + "]\n来自: " + title);
        tvStatus.setTextColor(0xFFE91E63);

        // 发送 App 内通知
        sendLocalNotification(title, content, keyword);

        // 震动
        vibrate();

        // 播放提示音
        playSound();
    }

    private void sendLocalNotification(String title, String content, String keyword) {
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("微信关键词命中: " + keyword)
                .setContentText(content.length() > 50 ? content.substring(0, 50) + "..." : content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(defaultSound)
                .setVibrate(new long[]{0, 500, 200, 500});

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            // 没有通知权限
            showToast("请授予通知权限以接收提醒");
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

    private void playSound() {
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

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkServiceStatus();
    }
}
