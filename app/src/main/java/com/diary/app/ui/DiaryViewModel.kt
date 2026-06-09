package com.diary.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diary.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.URIish
import org.joda.time.DateTime
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DiaryViewModel(context: Context) : ViewModel() {

    private val repository = DiaryRepository(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    val allEntries: Flow<List<DiaryEntry>> = repository.allEntries

    private val _editEntry = MutableStateFlow<DiaryEntry?>(null)
    val editEntry: StateFlow<DiaryEntry?> = _editEntry.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _singleBackupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val singleBackupState: StateFlow<BackupState> = _singleBackupState.asStateFlow()

    // 媒体捕获相关状态
    private val _pendingMediaUri = MutableStateFlow<Uri?>(null)
    val pendingMediaUri: StateFlow<Uri?> = _pendingMediaUri.asStateFlow()

    private val _mediaCaptureType = MutableStateFlow(AttachmentType.IMAGE)
    val mediaCaptureType: StateFlow<AttachmentType> = _mediaCaptureType.asStateFlow()

    private val _triggerCamera = MutableStateFlow(false)
    val triggerCamera: StateFlow<Boolean> = _triggerCamera.asStateFlow()

    private val _triggerVideo = MutableStateFlow(false)
    val triggerVideo: StateFlow<Boolean> = _triggerVideo.asStateFlow()

    private val _triggerPickImage = MutableStateFlow(false)
    val triggerPickImage: StateFlow<Boolean> = _triggerPickImage.asStateFlow()

    private val _triggerPickVideo = MutableStateFlow(false)
    val triggerPickVideo: StateFlow<Boolean> = _triggerPickVideo.asStateFlow()

    // ===================== 日记 CRUD =====================

    fun setEditEntry(entry: DiaryEntry) {
        _editEntry.value = entry
    }

    fun clearEditEntry() {
        _editEntry.value = null
    }

    fun insertEntry(entry: DiaryEntry) = viewModelScope.launch {
        repository.insertEntry(entry)
    }

    fun updateEntry(entry: DiaryEntry) = viewModelScope.launch {
        repository.updateEntry(entry)
    }

    fun deleteEntry(entry: DiaryEntry) = viewModelScope.launch {
        repository.deleteEntry(entry)
    }

    suspend fun getEntryById(id: Long): DiaryEntry? = repository.getEntryById(id)

    fun toggleFavorite(entry: DiaryEntry) = viewModelScope.launch {
        repository.updateEntry(entry.copy(isFavorite = !entry.isFavorite))
    }

    // ===================== 搜索 =====================

    private val _searchQuery = MutableStateFlow("")
    val searchResults: Flow<List<DiaryEntry>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) repository.allEntries
            else repository.searchEntries(query)
        }

    fun searchEntries(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ===================== 媒体附件 =====================

    fun createImageFileUri(context: Context): Uri? {
        return try {
            val dir = File(context.filesDir, "media/images")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val file = File(dir, fileName)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Failed to create image file", e)
            null
        }
    }

    fun createVideoFileUri(context: Context): Uri? {
        return try {
            val dir = File(context.filesDir, "media/videos")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "VID_${System.currentTimeMillis()}.mp4"
            val file = File(dir, fileName)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Failed to create video file", e)
            null
        }
    }

    fun triggerCapturePhoto() {
        _triggerCamera.value = true
    }

    fun consumeCameraTrigger() {
        _triggerCamera.value = false
    }

    fun triggerCaptureVideo() {
        _triggerVideo.value = true
    }

    fun consumeVideoTrigger() {
        _triggerVideo.value = false
    }

    fun triggerPickImageAction() {
        _triggerPickImage.value = true
    }

    fun consumePickImageTrigger() {
        _triggerPickImage.value = false
    }

    fun triggerPickVideoAction() {
        _triggerPickVideo.value = true
    }

    fun consumePickVideoTrigger() {
        _triggerPickVideo.value = false
    }

    fun setPendingMediaUri(uri: Uri?) {
        _pendingMediaUri.value = uri
    }

    fun addMediaAttachment(uri: Uri, type: AttachmentType) {
        val current = _editEntry.value
        if (current != null) {
            val attachment = DiaryAttachment(
                type = type,
                uri = uri.toString(),
                fileName = uri.lastPathSegment ?: ""
            )
            _editEntry.value = current.copy(
                attachments = current.attachments + attachment,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun addMediaAttachmentFromPending(type: AttachmentType) {
        _pendingMediaUri.value?.let { uri ->
            addMediaAttachment(uri, type)
        }
    }

    // ===================== 位置获取 =====================

    fun getCurrentLocation(context: Context, callback: (DiaryLocation) -> Unit) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val location = locationManager.getLastKnownLocation(
                android.location.LocationManager.GPS_PROVIDER
            ) ?: locationManager.getLastKnownLocation(
                android.location.LocationManager.NETWORK_PROVIDER
            )

            if (location != null) {
                val diaryLocation = DiaryLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = getAddressFromLocation(context, location.latitude, location.longitude)
                )
                callback(diaryLocation)
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Failed to get location", e)
        }
    }

    private fun getAddressFromLocation(context: Context, lat: Double, lng: Double): String? {
        return try {
            val geocoder = android.location.Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                null
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    addresses[0].getAddressLine(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===================== Markdown 导出 =====================

    fun entryToMarkdown(entry: DiaryEntry): String {
        val sb = StringBuilder()

        // 标题
        sb.append("# ${entry.title.ifEmpty { "无标题" }}\n\n")

        // 时间信息
        val created = DateTime(entry.createdAt).toString("yyyy-MM-dd HH:mm")
        val updated = DateTime(entry.updatedAt).toString("yyyy-MM-dd HH:mm")
        sb.append("> 📅 创建时间: $created\n")
        sb.append("> ✏️ 更新时间: $updated\n\n")

        // 位置
        if (entry.location != null) {
            sb.append("## 📍 位置\n\n")
            sb.append("- 纬度: ${entry.location.latitude}\n")
            sb.append("- 经度: ${entry.location.longitude}\n")
            if (entry.location.address != null) {
                sb.append("- 地址: ${entry.location.address}\n")
            }
            sb.append("- 地图链接: [查看地图](geo:${entry.location.latitude},${entry.location.longitude})\n\n")
        }

        // 正文
        sb.append("## 📝 内容\n\n")
        sb.append(entry.content)
        sb.append("\n\n")

        // 附件
        if (entry.attachments.isNotEmpty()) {
            sb.append("## 📎 附件 (${entry.attachments.size})\n\n")
            entry.attachments.forEachIndexed { index, attachment ->
                when (attachment.type) {
                    AttachmentType.IMAGE -> {
                        sb.append("${index + 1}. 📷 图片: `${attachment.fileName}`\n")
                    }
                    AttachmentType.VIDEO -> {
                        sb.append("${index + 1}. 🎬 视频: `${attachment.fileName}`\n")
                    }
                }
            }
            sb.append("\n")
        }

        if (entry.isFavorite) {
            sb.append("---\n⭐ 已收藏\n")
        }

        return sb.toString()
    }

    // ===================== GitLab API =====================

    /**
     * 通过 GitLab API 创建项目
     */
    private fun createGitLabProject(settings: BackupSettings): Int? {
        return try {
            val urlStr = "${settings.gitlabUrl.trimEnd('/')}/api/v4/projects"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.setRequestProperty("PRIVATE-TOKEN", settings.gitlabToken)
            conn.setRequestProperty("Content-Type", "application/json")

            val body = """{
                "name": "${settings.repoName}",
                "visibility": "private",
                "description": "DiaryApp 日记备份 - ${DateTime().toString("yyyy-MM-dd")}"
            }""".trimIndent()

            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            Log.d("DiaryViewModel", "Create project response: $code")
            if (code == 201) {
                val response = conn.inputStream.bufferedReader().readText()
                val gson = com.google.gson.Gson()
                val json = gson.fromJson(response, com.google.gson.JsonObject::class.java)
                json?.get("id")?.asInt
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Failed to create project", e)
            null
        }
    }

    /**
     * 通过 GitLab API 上传单个文件到仓库
     */
    fun uploadToGitLab(entry: DiaryEntry, settings: BackupSettings): Boolean {
        return try {
            val baseUrl = settings.gitlabUrl.trimEnd('/')
            val encodedPath = java.net.URLEncoder.encode("root/${settings.repoName}", "UTF-8")
            val fileName = entryToSafeFileName(entry)
            val urlStr = "$baseUrl/api/v4/projects/$encodedPath/repository/files/$fileName"

            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("PRIVATE-TOKEN", settings.gitlabToken)
            conn.setRequestProperty("Content-Type", "application/json")

            val markdown = entryToMarkdown(entry)
            val body = """{
                "branch": "main",
                "content": ${com.google.gson.Gson().toJson(markdown)},
                "commit_message": "Backup diary: ${entry.title.ifEmpty { "无标题" }} (${DateTime(entry.createdAt).toString("yyyy-MM-dd HH:mm")})"
            }""".trimIndent()

            Log.d("DiaryViewModel", "Uploading to: $urlStr")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d("DiaryViewModel", "Upload response: $code")
            code == 201 || code == 200
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Upload failed", e)
            false
        }
    }

    private fun entryToSafeFileName(entry: DiaryEntry): String {
        val date = DateTime(entry.createdAt).toString("yyyy-MM-dd")
        val title = entry.title.ifEmpty { "无标题" }
            .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
            .replace(" ", "_")
            .take(50)
        return java.net.URLEncoder.encode("${date}_${title}.md", "UTF-8")
    }

    // ===================== 全量备份 =====================

    fun startBackup() = viewModelScope.launch {
        try {
            val settings = loadBackupSettings()
            if (settings.gitlabToken.isEmpty()) {
                _backupState.value = BackupState.Error("请先填写 Personal Access Token")
                return@launch
            }
            if (settings.gitlabUrl.isEmpty()) {
                _backupState.value = BackupState.Error("请填写 GitLab URL")
                return@launch
            }

            _backupState.value = BackupState.BackingUp("正在创建/检查 GitLab 仓库...")

            // Step 1: 确保项目存在
            val projectId = createGitLabProject(settings)
            if (projectId != null) {
                Log.d("DiaryViewModel", "Project created with ID: $projectId")
            }

            // Step 2: 导出所有日记为 Markdown + JSON
            _backupState.value = BackupState.BackingUp("正在导出日记数据...")
            val entries = repository.getAllEntriesSync()
            val backupDir = File(appContext.filesDir, "backup_repo")
            if (!backupDir.exists()) backupDir.mkdirs()

            // Markdown 目录
            val mdDir = File(backupDir, "markdown")
            if (!mdDir.exists()) mdDir.mkdirs()

            entries.forEach { entry ->
                val mdContent = entryToMarkdown(entry)
                val fileName = "${DateTime(entry.createdAt).toString("yyyy-MM-dd")}_${
                    entry.title.ifEmpty { "无标题" }
                        .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
                        .replace(" ", "_")
                        .take(50)
                }.md"
                File(mdDir, fileName).writeText(mdContent)
            }

            // JSON 备份
            val json = com.google.gson.Gson().toJson(entries)
            File(backupDir, "latest.json").writeText(json)

            // 索引文件
            val indexMd = buildString {
                append("# 📔 日记备份索引\n\n")
                append("> 自动生成于 ${DateTime().toString("yyyy-MM-dd HH:mm")}\n\n")
                append("## 日记列表\n\n")
                append("| 日期 | 标题 | 附件 | 位置 |\n")
                append("|------|------|------|------|\n")
                entries.forEach { entry ->
                    val date = DateTime(entry.createdAt).toString("yyyy-MM-dd")
                    val title = entry.title.ifEmpty { "无标题" }
                    val attachCount = entry.attachments.size
                    val hasLocation = if (entry.location != null) "✅" else "-"
                    val mdFile = "${date}_${title.replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "").replace(" ", "_").take(50)}.md"
                    append("| $date | [$title](markdown/$mdFile) | $attachCount | $hasLocation |\n")
                }
                append("\n---\n*Generated by DiaryApp*")
            }
            File(backupDir, "README.md").writeText(indexMd)

            // Step 3: Git 操作
            _backupState.value = BackupState.BackingUp("正在提交数据 (${entries.size} 篇日记)...")
            val git = if (File(backupDir, ".git").exists()) {
                Git.open(backupDir)
            } else {
                Git.init().setDirectory(backupDir).call().also { g ->
                    // 创建 main 分支
                    g.checkout().setCreateBranch(true).setName("main").call()
                }
            }

            git.add().addFilepattern(".").call()
            val status = git.status().call()
            if (status.added.isNotEmpty() || status.changed.isNotEmpty() || status.modified.isNotEmpty()) {
                val timestamp = DateTime().toString("yyyy-MM-dd_HH-mm-ss")
                git.commit()
                    .setMessage("Full backup: $timestamp\n\nTotal entries: ${entries.size}")
                    .call()
            }

            // Step 4: 推送
            _backupState.value = BackupState.BackingUp("正在推送到 GitLab...")
            val host = settings.gitlabUrl
                .trimEnd('/')
                .replace("https://", "")
                .replace("http://", "")
            val gitUrl = "https://oauth2:${settings.gitlabToken}@$host/root/${settings.repoName}.git"

            val remote = "origin"
            val remotes = git.remoteList().call()
            val existingRemote = remotes.find { it.name == remote }

            if (existingRemote == null) {
                git.remoteAdd().setName(remote).setUri(URIish(gitUrl)).call()
            } else {
                git.remoteRemove().setName(remote).call()
                git.remoteAdd().setName(remote).setUri(URIish(gitUrl)).call()
            }

            val credentials: CredentialsProvider = UsernamePasswordCredentialsProvider(
                "oauth2", settings.gitlabToken
            )

            git.push()
                .setRemote(remote)
                .setCredentialsProvider(credentials)
                .setForce(true)
                .call()

            _backupState.value = BackupState.Success(
                "✅ 全量备份成功！\n" +
                "📅 时间: ${DateTime().toString("yyyy-MM-dd HH:mm")}\n" +
                "📝 日记: ${entries.size} 篇\n" +
                "🔗 仓库: ${settings.gitlabUrl}/root/${settings.repoName}"
            )

        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Backup failed", e)
            _backupState.value = BackupState.Error(
                "❌ 备份失败: ${e.message ?: "未知错误"}\n\n" +
                "请检查:\n" +
                "1. GitLab URL 是否正确\n" +
                "2. Token 是否有效（需要 api + write_repository 权限）\n" +
                "3. 网络连接是否正常\n" +
                "4. 仓库是否已存在或 Token 是否有创建权限"
            )
        }
    }

    // ===================== 单篇备份 =====================

    fun backupSingleEntry(entry: DiaryEntry) = viewModelScope.launch {
        try {
            val settings = loadBackupSettings()
            if (settings.gitlabToken.isEmpty()) {
                _singleBackupState.value = BackupState.Error("请先在备份页面配置 Token")
                return@launch
            }

            _singleBackupState.value = BackupState.BackingUp("正在备份「${entry.title.ifEmpty { "无标题" }}」...")

            val success = uploadToGitLab(entry, settings)

            if (success) {
                _singleBackupState.value = BackupState.Success(
                    "✅ 「${entry.title.ifEmpty { "无标题" }}」已备份到 GitLab"
                )
            } else {
                _singleBackupState.value = BackupState.Error(
                    "❌ 备份失败，请检查仓库是否存在"
                )
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Single backup failed", e)
            _singleBackupState.value = BackupState.Error("❌ 备份失败: ${e.message}")
        }
    }

    fun clearSingleBackupState() {
        _singleBackupState.value = BackupState.Idle
    }

    // ===================== 备份设置 =====================

    fun loadBackupSettings(): BackupSettings {
        return BackupSettings(
            gitlabUrl = prefs.getString("gitlab_url", "https://gitlab.com") ?: "https://gitlab.com",
            gitlabToken = prefs.getString("gitlab_token", "") ?: "",
            repoName = prefs.getString("gitlab_repo", "diary-backup") ?: "diary-backup"
        )
    }

    fun saveBackupSettings(url: String, token: String, repo: String) {
        prefs.edit()
            .putString("gitlab_url", url)
            .putString("gitlab_token", token)
            .putString("gitlab_repo", repo)
            .apply()
    }
}

// ===================== 数据类 =====================

sealed class BackupState {
    object Idle : BackupState()
    data class BackingUp(val message: String) : BackupState()
    data class Success(val message: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

data class BackupSettings(
    val gitlabUrl: String = "https://gitlab.com",
    val gitlabToken: String = "",
    val repoName: String = "diary-backup"
)
