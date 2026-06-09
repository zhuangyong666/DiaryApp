# 自动构建 APK — 使用说明

## 前提

- 有一个 [GitHub 账号](https://github.com)
- 项目已上传到 GitHub 仓库

## 一键部署

### 1. 上传项目到 GitHub

在 PowerShell 中执行：

```powershell
cd C:\Users\Administrator\.openclaw\workspace\DiaryApp
git init
git add .
git commit -m "Initial commit - DiaryApp"
git remote add origin https://github.com/你的用户名/DiaryApp.git
git branch -M main
git push -u origin main
```

### 2. 自动构建

Push 完成后，GitHub Actions **自动开始构建**，约 5-10 分钟。

完成后在仓库页面：
- **Actions** 标签 → 查看构建状态
- **Releases** 标签 → 下载 APK
- 也可以直接下载 artifact

### 3. 手动触发

任何时候可以手动触发构建：
- 仓库 → **Actions** → Build APK → **Run workflow**

## APK 去哪了

- 构建产物保留 **30 天**，在 Actions → 点击对应 workflow → 页面底部 **Artifacts** 下载
- push 到 main 时会自动创建一个 **Release**，带 APK 文件

## 安装到手机

```bash
adb install app-debug.apk
```

或者把 APK 传到手机直接安装。
