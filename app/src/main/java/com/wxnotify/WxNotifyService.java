package com.wxnotify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Build;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WxNotifyService extends NotificationListenerService {

    private static final String TAG = "WxNotifyService";
    private static final String WX_PACKAGE = "com.tencent.mm";

    // 关键词用独立 prefs 文件（被 MainActivity 读取）
    // 必须和 MainActivity 里创建的渠道 ID 一致
    public static final String CHANNEL_ID = "wx_notify_alert";

    public static final String PREFS_NAME = "wx_notify_prefs";
    public static final String KEY_KEYWORDS = "keywords";
    public static final String KEY_ENABLED = "monitor_enabled";

    // 调试日志写进 MainActivity 共用的 prefs 文件
    private static final String PREFS_MAIN = "wx_notify_main";
    private static final String KEY_DEBUG_LOG = "debug_log";
    private static final String KEY_DEBUG_ENABLED = "debug_enabled";
    private static final int MAX_LOG = 10;

    public static OnKeywordMatchedListener listener;

    public interface OnKeywordMatchedListener {
        void onKeywordMatched(String title, String content, String matchedKeyword);
    }

    public static void setListener(OnKeywordMatchedListener l) {
        listener = l;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WX_PACKAGE.equals(sbn.getPackageName())) return;

        String title = "";
        String content = "";

        try {
            android.os.Bundle extras = sbn.getNotification().extras;
            if (extras != null) {
                CharSequence t = extras.getCharSequence("android.title", null);
                CharSequence c = extras.getCharSequence("android.text", null);
                title = (t != null) ? t.toString() : "";
                content = (c != null) ? c.toString() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "解析失败: " + e.getMessage());
        }

        Log.d(TAG, "微信通知 - " + title + " | " + content);

        // 所有微信通知都写入调试日志
        appendDebugLog(title, content);

        // 关键词匹配
        String matched = matchKeyword(title, content);
        if (matched != null) {
            Log.i(TAG, "命中: " + matched);
            if (listener != null) {
                listener.onKeywordMatched(title, content, matched);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) { }

    @Override
    public void onListenerDisconnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this, WxNotifyService.class));
        }
    }

    private String matchKeyword(String title, String content) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String keywordStr = prefs.getString(KEY_KEYWORDS, "");
        if (keywordStr == null || keywordStr.trim().isEmpty()) return null;

        String[] keywords = keywordStr.split("[,\n]+");
        String text = (title + " " + content).toLowerCase();
        for (String kw : keywords) {
            kw = kw.trim().toLowerCase();
            if (!kw.isEmpty() && text.contains(kw)) return kw;
        }
        return null;
    }

    private void appendDebugLog(String title, String content) {
        try {
            SharedPreferences mainPrefs = getSharedPreferences(PREFS_MAIN, MODE_PRIVATE);
            if (!mainPrefs.getBoolean(KEY_DEBUG_ENABLED, false)) return;

            String existing = mainPrefs.getString(KEY_DEBUG_LOG, "");
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = time + "\n  " + title + "\n  " + content + "\n";
            String updated = line + existing;

            // 保留最近 MAX_LOG 条（数换行符）
            int count = 0, pos = 0;
            while (count < MAX_LOG && pos < updated.length()) {
                int sep = updated.indexOf("\n", pos);
                if (sep < 0) break;
                pos = sep + 1;
                if (pos < updated.length() && updated.charAt(pos) == '\n') {
                    pos++;
                    count++;
                }
            }
            if (count >= MAX_LOG) updated = updated.substring(0, pos);

            mainPrefs.edit().putString(KEY_DEBUG_LOG, updated).apply();
        } catch (Exception e) {
            Log.e(TAG, "日志写入失败: " + e.getMessage());
        }
    }
}
