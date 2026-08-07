# 微信通知关键词监控 App

Android App，监听微信通知，匹配关键词后自动提醒（声音+震动）。

## 功能

- 监听 `com.tencent.mm`（微信）的所有通知
- 支持配置多个关键词，逗号/换行分隔
- 匹配成功后：震动 + 铃声 + App 内通知
- 后台常驻，系统开机自动启动监听

## 技术原理

使用 Android **NotificationListenerService**（API 18+）
- 系统级通知监听，无需 root
- 用户必须手动在「通知使用权」中授权
- 提取通知的 `android.title`（标题）和 `android.text`（内容）进行关键词匹配

## 使用前准备

### 1. 关键词
打开 App，在输入框填入要监控的关键词，例如：
```
转账, 红包, 汇款, 到账, 催款, 紧急
```

### 2. 开启通知权限
首次使用需要授权：
1. 点击「开启通知监控权限」
2. 在系统设置中找到「微信通知监控」
3. 开启开关

### 3. 测试
让朋友发一条包含关键词的微信消息，或者自己在另一台设备发消息，验证是否触发提醒。

## 编译打包

### 命令行
```bash
# 生成 wrapper
gradle wrapper

# 打包 debug APK
./gradlew assembleDebug

# 打包 release APK（需配置签名）
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### Android Studio
1. 用 Android Studio 打开项目根目录
2. Sync Project with Gradle Files
3. Run → Run 'app'

### 手机上安装
- 复制 APK 到手机，安装
- 首次安装需要允许「安装未知来源应用」
- 开启通知使用权

## 注意事项

- Android 13+（API 33）需要额外授予通知权限（App 会在首次打开时请求）
- 微信必须在后台运行才能接收通知（国产rom自启动限制需自行放行）
- 华为/小米等系统需要额外设置自启动/后台保活
- 关键词匹配不区分大小写
