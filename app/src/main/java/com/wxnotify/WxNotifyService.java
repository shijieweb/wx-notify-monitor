package com.wxnotify;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Build;
import android.util.Log;
import android.app.Activity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WxNotifyService extends NotificationListenerService {

    private static final String TAG = "WxNotifyService";
    private static final String WX_PACKAGE = "com.tencent.mm";

    // 统一用同一个 prefs 文件
    public static final String PREFS_NAME = "wx_notify_prefs";
    public static final String KEY_KEYWORDS = "keywords";
    public static final String KEY_ENABLED = "monitor_enabled";
    public static final String KEY_DEBUG_LOG = "debug_log";
    private static final String KEY_DEBUG_ENABLED = "debug_enabled";
    private static final int MAX_LOG = 10;

    // 回调接口
    public static OnKeywordMatchedListener listener;

    public interface OnKeywordMatchedListener {
        void onKeywordMatched(String title, String content, String matchedKeyword);
    }

    public static void setListener(OnKeywordMatchedListener l) {
        listener = l;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WX_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        String title = "";
        String content = "";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                android.os.Bundle extras = sbn.getNotification().extras;
                if (extras != null) {
                    CharSequence t = extras.getCharSequence("android.title", null);
                    CharSequence c = extras.getCharSequence("android.text", null);
                    title = (t != null) ? t.toString() : "";
                    content = (c != null) ? c.toString() : "";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析通知失败: " + e.getMessage());
        }

        Log.d(TAG, "收到微信通知 - 标题: " + title + " | 内容: " + content);

        // 记录到调试日志（所有通知都记）
        addDebugLog(title, content);

        // 关键词匹配
        String matched = matchKeyword(title, content);
        if (matched != null) {
            Log.i(TAG, "关键词命中: " + matched);
            if (listener != null) {
                listener.onKeywordMatched(title, content, matched);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 不做处理
    }

    @Override
    public void onListenerDisconnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this, WxNotifyService.class));
        }
    }

    /**
     * 关键词匹配，返回命中的关键词，不匹配返回 null
     */
    private String matchKeyword(String title, String content) {
        String keywordStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_KEYWORDS, "");
        if (keywordStr == null || keywordStr.trim().isEmpty()) {
            return null;
        }

        boolean enabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            return null;
        }

        String[] keywords = keywordStr.split("[,\n]+");
        String text = (title + " " + content).toLowerCase();

        for (String kw : keywords) {
            kw = kw.trim().toLowerCase();
            if (!kw.isEmpty() && text.contains(kw)) {
                return kw;
            }
        }
        return null;
    }

    /**
     * 将拦截记录写入 SharedPreferences
     * 保留最近 MAX_LOG 条
     */
    private void addDebugLog(String title, String content) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean debugOn = prefs.getBoolean(KEY_DEBUG_ENABLED, false);
            if (!debugOn) return;

            String existing = prefs.getString(KEY_DEBUG_LOG, "");
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = time + "\n  " + title + "\n  " + content + "\n";
            String updated = line + existing;

            // 截取保留最近 MAX_LOG 条
            int count = 0;
            int pos = 0;
            while (count < MAX_LOG && pos < updated.length()) {
                int sep = updated.indexOf("\n", pos);
                if (sep < 0) break;
                pos = sep + 1;
                if (pos < updated.length() && updated.charAt(pos) == '\n') {
                    pos++;
                    count++;
                }
            }
            if (count >= MAX_LOG) {
                updated = updated.substring(0, pos);
            }

            prefs.edit().putString(KEY_DEBUG_LOG, updated).apply();
            Log.d(TAG, "调试日志已更新: " + count + " 条");

        } catch (Exception e) {
            Log.e(TAG, "addDebugLog 失败: " + e.getMessage());
        }
    }
}
