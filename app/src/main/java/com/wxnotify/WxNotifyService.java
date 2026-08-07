package com.wxnotify;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Build;
import android.util.Log;

import java.util.Set;
import java.util.HashSet;

/**
 * 微信通知监听服务
 * 监听 com.tencent.mm 的通知，提取文字内容，匹配关键词后触发提醒
 */
public class WxNotifyService extends NotificationListenerService {

    private static final String TAG = "WxNotifyService";
    private static final String WX_PACKAGE = "com.tencent.mm"; // 微信包名

    // SharedPreferences key 名称
    public static final String PREFS_NAME = "wx_notify_prefs";
    public static final String KEY_KEYWORDS = "keywords";
    public static final String KEY_ENABLED = "monitor_enabled";

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
        // 只监听微信
        if (!WX_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }

        String title = "";
        String content = "";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                android.os.Bundle extras = sbn.getNotification().extras;
                if (extras != null) {
                    title = extras.getCharSequence("android.title", "").toString();
                    content = extras.getCharSequence("android.text", "").toString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析通知失败: " + e.getMessage());
        }

        Log.d(TAG, "收到微信通知 - 标题: " + title + " | 内容: " + content);

        // 关键词匹配
        if (matchKeyword(title, content)) {
            Log.i(TAG, "关键词命中！");
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!WX_PACKAGE.equals(sbn.getPackageName())) {
            return;
        }
        Log.d(TAG, "微信通知已移除");
    }

    @Override
    public void onListenerDisconnected() {
        // 系统断开连接时，请求重新绑定（Android 7.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this, WxNotifyService.class));
        }
    }

    /**
     * 关键词匹配
     * 从 SharedPreferences 读取用户配置的关键词列表
     * 返回命中的关键词
     */
    private boolean matchKeyword(String title, String content) {
        String keywordStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_KEYWORDS, "");
        if (keywordStr == null || keywordStr.trim().isEmpty()) {
            return false;
        }

        boolean enabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            return false;
        }

        String[] keywords = keywordStr.split("[,\n]+");
        String text = (title + " " + content).toLowerCase();

        for (String kw : keywords) {
            kw = kw.trim().toLowerCase();
            if (!kw.isEmpty() && text.contains(kw)) {
                // 命中，触发提醒
                if (listener != null) {
                    listener.onKeywordMatched(title, content, kw);
                }
                return true;
            }
        }
        return false;
    }
}
