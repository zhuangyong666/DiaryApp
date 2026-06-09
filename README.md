# 📔 DiaryApp - Android 日记应用

一个功能丰富的 Android 日记应用，支持图片/视频记录、位置标记和本地存储，可手动备份到 GitLab。

## 功能特性

- 📝 **富文本日记** - 支持文字、图片、视频混合排版
- 📷 **媒体支持** - 拍照、录像、从相册选择图片和视频
- 📍 **位置记录** - 自动获取当前地理位置并标记
- 💾 **本地存储** - 使用 Room 数据库持久化，数据完全在本地
- 🔒 **GitLab 备份** - 一键将日记数据库备份到 GitLab 私有仓库
- 🌙 **深色模式** - 跟随系统主题

## 技术栈

- **Kotlin** - 主要编程语言
- **Jetpack Compose** - 声明式 UI
- **Room** - 本地数据库
- **Navigation Compose** - 页面导航
- **Coil** - 图片加载
- **JGit** - GitLab 备份
- **Coroutines + Flow** - 异步处理
- **Hilt** - 依赖注入（可选）

## 构建要求

- Android Studio Koala 或更高版本
- JDK 17+
- minSdk 26 (Android 8.0)
- targetSdk 35 (Android 15)

## 构建运行

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## GitLab 备份设置

1. 在设置页面输入 GitLab 仓库 URL
2. 输入 Personal Access Token（需要 repo 权限）
3. 点击"备份"按钮即可完成推送

## 权限说明

- 相机 - 拍照和录像
- 位置 - 记录日记位置
- 存储 - 读取媒体文件
