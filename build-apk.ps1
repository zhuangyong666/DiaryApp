# ============================================================
# DiaryApp 一键构建脚本
# 自动下载 Android SDK + 构建工具 + 打包 APK
# ============================================================

$ErrorActionPreference = "Stop"

# 配置
$AndroidSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$CmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$BuildToolsVersion = "35.0.0"
$PlatformVersion = "android-35"
$ProjectRoot = Split-Path $MyInvocation.MyCommand.Path -Parent

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  DiaryApp 一键构建" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- Step 1: 检查/下载 Android Command Line Tools ----
$SdkManagerPath = Join-Path $AndroidSdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"

if (-not (Test-Path $SdkManagerPath)) {
    Write-Host "[1/5] 下载 Android Command Line Tools..." -ForegroundColor Yellow
    
    $TempZip = Join-Path $env:TEMP "android-cmdline-tools.zip"
    
    if (Test-Path $TempZip) { Remove-Item $TempZip -Force }
    
    Write-Host "  下载中... (约 150MB)" -ForegroundColor Gray
    
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $CmdlineToolsUrl -OutFile $TempZip -UseBasicParsing
    } catch {
        Write-Host "  下载失败: $_" -ForegroundColor Red
        Write-Host "  请手动下载: $CmdlineToolsUrl" -ForegroundColor Red
        Write-Host "  然后放到: $TempZip" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "  解压中..." -ForegroundColor Gray
    
    # 创建临时解压目录
    $TempExtract = Join-Path $env:TEMP "android-cmdline-extract"
    if (Test-Path $TempExtract) { Remove-Item $TempExtract -Recurse -Force }
    Expand-Archive -Path $TempZip -DestinationPath $TempExtract -Force
    
    # 移动到正确位置
    $TargetDir = Join-Path $AndroidSdkRoot "cmdline-tools\latest"
    if (-not (Test-Path $TargetDir)) {
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }
    
    # cmdline-tools 解压后内部还有一个 cmdline-tools 目录
    $SourceDir = Join-Path $TempExtract "cmdline-tools"
    if (Test-Path (Join-Path $SourceDir "bin")) {
        Copy-Item -Path (Join-Path $SourceDir "\*") -Destination $TargetDir -Recurse -Force
    } else {
        Copy-Item -Path (Join-Path $TempExtract "\*") -Destination $TargetDir -Recurse -Force
    }
    
    # 清理
    Remove-Item $TempZip -Force -ErrorAction SilentlyContinue
    Remove-Item $TempExtract -Recurse -Force -ErrorAction SilentlyContinue
    
    Write-Host "  ✅ 安装完成" -ForegroundColor Green
} else {
    Write-Host "[1/5] Android Command Line Tools 已存在 ✓" -ForegroundColor Green
}

# ---- Step 2: 设置环境变量 ----
$env:ANDROID_HOME = $AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $AndroidSdkRoot

Write-Host "[2/5] 环境变量设置完成 ✓" -ForegroundColor Green

# ---- Step 3: 接受许可证并安装 SDK 组件 ----
Write-Host "[3/5] 安装 SDK 组件 (build-tools, platform, emulator)..." -ForegroundColor Yellow

$SdkManager = Join-Path $AndroidSdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"

# 自动接受所有许可证
$Components = @(
    "platform-tools",
    "platforms;$PlatformVersion",
    "build-tools;$BuildToolsVersion"
)

foreach ($comp in $Components) {
    if ($LASTEXITCODE -ne 0) {
        & $SdkManager "--licenses" 2>&1 | Out-Null
        echo "y" | & $SdkManager $comp 2>&1 | Out-Null
    }
}

# 确保许可证被接受
$LicenseDir = Join-Path $AndroidSdkRoot "licenses"
if (-not (Test-Path $LicenseDir)) {
    New-Item -ItemType Directory -Path $LicenseDir -Force | Out-Null
}
Set-Content -Path (Join-Path $LicenseDir "android-sdk-license") -Value "8933bad161af4178b1185d1a37fbf41ea5269c55" -Force
Set-Content -Path (Join-Path $LicenseDir "android-sdk-preview-license") -Value "504667f4c0de7af1a06de2f3649583275df3684e" -Force

# 安装组件
$InstallCmd = "& `"$SdkManager`" " + ($Components -join " ")
Invoke-Expression $InstallCmd

Write-Host "  ✅ SDK 组件安装完成" -ForegroundColor Green

# ---- Step 4: 创建 local.properties ----
$LocalProps = Join-Path $ProjectRoot "local.properties"
$sdkPathEscaped = $AndroidSdkRoot -replace '\\', '/'
Set-Content -Path $LocalProps -Value "sdk.dir=$sdkPathEscaped" -Force

Write-Host "[4/5] local.properties 已创建 ✓" -ForegroundColor Green

# ---- Step 5: 构建 APK ----
Write-Host "[5/5] 开始构建 APK..." -ForegroundColor Yellow
Write-Host ""

$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"

if (Test-Path $GradleWrapper) {
    Push-Location $ProjectRoot
    & cmd /c "$GradleWrapper assembleDebug --no-daemon"
    $BuildResult = $LASTEXITCODE
    Pop-Location
} else {
    Write-Host "  ❌ 找不到 gradlew.bat" -ForegroundColor Red
    Write-Host "  需要先生成 Gradle Wrapper..." -ForegroundColor Yellow
    
    # 下载 Gradle 并生成 wrapper
    $GradleVersion = "8.9"
    $GradleUrl = "https://services.gradle.org/distributions/gradle-${GradleVersion}-bin.zip"
    $GradleZip = Join-Path $env:TEMP "gradle.zip"
    
    Write-Host "  下载 Gradle $GradleVersion..." -ForegroundColor Gray
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $GradleUrl -OutFile $GradleZip -UseBasicParsing
    
    $GradleExtract = Join-Path $env:TEMP "gradle-extract"
    if (Test-Path $GradleExtract) { Remove-Item $GradleExtract -Recurse -Force }
    Expand-Archive -Path $GradleZip -DestinationPath $GradleExtract -Force
    
    $GradleBin = (Get-ChildItem -Path $GradleExtract -Filter "gradle.bat" -Recurse)[0].FullName
    $GradleHome = (Get-Item $GradleBin).Directory.Parent.FullName
    
    Push-Location $ProjectRoot
    & $GradleBin wrapper
    $BuildResult = $LASTEXITCODE
    Pop-Location
    
    Remove-Item $GradleZip -Force -ErrorAction SilentlyContinue
    Remove-Item $GradleExtract -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan

if ($BuildResult -eq 0) {
    $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    
    if (Test-Path $ApkPath) {
        Write-Host "  🎉 构建成功！" -ForegroundColor Green
        Write-Host ""
        Write-Host "  APK 位置: $ApkPath" -ForegroundColor White
        Write-Host "  APK 大小: $([math]::Round((Get-Item $ApkPath).Length / 1MB, 2)) MB" -ForegroundColor White
        Write-Host ""
        Write-Host "  安装到设备: adb install `"$ApkPath`"" -ForegroundColor Gray
    } else {
        Write-Host "  ⚠️ 构建完成但未找到 APK" -ForegroundColor Yellow
        Write-Host "  检查: app\build\outputs\apk\debug\" -ForegroundColor Gray
    }
} else {
    Write-Host "  ❌ 构建失败，请检查错误信息" -ForegroundColor Red
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
