package com.diary.app.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diary.app.data.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DiaryRepository(getApplication())
    private val prefs: SharedPreferences = getApplication<Application>().getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)
    private val appContext = getApplication<Application>().applicationContext

    val allEntries: Flow<List<DiaryEntry>> = repository.allEntries

    private val _editEntry = MutableStateFlow<DiaryEntry?>(null)
    val editEntry: StateFlow<DiaryEntry?> = _editEntry.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _singleBackupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val singleBackupState: StateFlow<BackupState> = _singleBackupState.asStateFlow()

    // 媒体捕获相关状�?
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
        sb.append("# ${entry.title.ifEmpty { "Untitled" }}\n\n")
        val created = DateTime(entry.createdAt).toString("yyyy-MM-dd HH:mm")
        val updated = DateTime(entry.updatedAt).toString("yyyy-MM-dd HH:mm")
        sb.append("> Created: $created\n")
        sb.append("> Updated: $updated\n\n")
        if (entry.location != null) {
            sb.append("## Location\n\n")
            sb.append("- Latitude: ${entry.location.latitude}\n")
            sb.append("- Longitude: ${entry.location.longitude}\n")
            if (entry.location.address != null) {
                sb.append("- Address: ${entry.location.address}\n")
            }
            sb.append("\n")
        }
        sb.append("## Content\n\n")
        sb.append(entry.content)
        sb.append("\n\n")
        if (entry.attachments.isNotEmpty()) {
            sb.append("## Attachments (${entry.attachments.size})\n\n")
            entry.attachments.forEachIndexed { index, attachment ->
                val icon = if (attachment.type == AttachmentType.IMAGE) "📷" else "🎬"
                sb.append("${index + 1}. $icon `${attachment.fileName}`\n")
            }
            sb.append("\n")
        }
        if (entry.isFavorite) {
            sb.append("---\n�?Favorited\n")
        }
        return sb.toString()
    }

    // ===================== GitLab HTTP API =====================

    private fun gitlabRequest(
        settings: BackupSettings,
        path: String,
        method: String = "GET",
        body: String? = null
    ): Pair<Int, String> {
        val baseUrl = settings.gitlabUrl.trimEnd('/')
        val url = URL("$baseUrl/api/v4$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("PRIVATE-TOKEN", settings.gitlabToken)
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val response = try {
            if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
        } catch (e: Exception) {
            ""
        }
        return code to response
    }

    private fun createGitLabProject(settings: BackupSettings): Boolean {
        val body = """{
            "name": "${settings.repoName}",
            "visibility": "private",
            "description": "DiaryApp backup - ${DateTime().toString("yyyy-MM-dd")}"
        }""".trimIndent()
        val (code, _) = gitlabRequest(settings, "/projects", "POST", body)
        return code == 201 || code == 400 // 400 = already exists
    }

    private fun commitFilesToGitLab(
        settings: BackupSettings,
        files: Map<String, String>,
        commitMessage: String
    ): Boolean {
        val encodedPath = java.net.URLEncoder.encode("root/${settings.repoName}", "UTF-8")
        val actions = files.map { (filePath, content) ->
            JsonObject().apply {
                addProperty("action", "create")
                addProperty("file_path", filePath)
                addProperty("content", content)
            }
        }
        val gson = Gson()
        val bodyObj = JsonObject().apply {
            addProperty("branch", "main")
            addProperty("commit_message", commitMessage)
            add("actions", gson.toJsonTree(actions))
        }
        val body = gson.toJson(bodyObj)
        val (code, resp) = gitlabRequest(
            settings,
            "/projects/$encodedPath/repository/commits",
            "POST",
            body
        )
        if (code in 200..299) return true
        Log.e("DiaryViewModel", "Commit failed: $code $resp")
        return false
    }

    // ===================== 全量备份 =====================

    fun startBackup() = viewModelScope.launch {
        try {
            val settings = loadBackupSettings()
            if (settings.gitlabToken.isEmpty()) {
                _backupState.value = BackupState.Error("Please enter Personal Access Token")
                return@launch
            }

            _backupState.value = BackupState.BackingUp("Creating/checking repository...")
            withContext(Dispatchers.IO) {
                createGitLabProject(settings)

                _backupState.value = BackupState.BackingUp("Exporting diary entries...")
                val entries = repository.getAllEntriesSync()

                val files = mutableMapOf<String, String>()

                val indexMd = buildString {
                    append("# Diary Backup\n\n")
                    append("> Generated: ${DateTime().toString("yyyy-MM-dd HH:mm")}\n\n")
                    append("## Entries\n\n")
                    entries.forEach { entry ->
                        val date = DateTime(entry.createdAt).toString("yyyy-MM-dd")
                        val title = entry.title.ifEmpty { "Untitled" }
                        val file = "${date}_${title.replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "").replace(" ", "_").take(50)}.md"
                        append("- [$title](entries/$file) ($date)\n")
                    }
                }
                files["README.md"] = indexMd

                entries.forEach { entry ->
                    val md = entryToMarkdown(entry)
                    val fileName = "${DateTime(entry.createdAt).toString("yyyy-MM-dd")}_${
                        entry.title.ifEmpty { "Untitled" }
                            .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
                            .replace(" ", "_")
                            .take(50)
                    }.md"
                    files["entries/$fileName"] = md
                }

                _backupState.value = BackupState.BackingUp("Uploading ${files.size} files to GitLab...")
                val success = commitFilesToGitLab(
                    settings,
                    files,
                    "Full backup: ${DateTime().toString("yyyy-MM-dd HH:mm")} (${entries.size} entries)"
                )

                if (success) {
                    _backupState.value = BackupState.Success(
                        "Backup successful!\n${entries.size} entries backed up to ${settings.gitlabUrl}/root/${settings.repoName}"
                    )
                } else {
                    _backupState.value = BackupState.Error(
                        "Upload failed. Check: 1) repo exists 2) token has write access 3) main branch exists"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Backup failed", e)
            _backupState.value = BackupState.Error("Backup failed: ${e.message}")
        }
    }

    // ===================== 单篇备份 =====================

    fun backupSingleEntry(entry: DiaryEntry) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val settings = loadBackupSettings()
            if (settings.gitlabToken.isEmpty()) {
                _singleBackupState.value = BackupState.Error("Please configure Token in backup settings first")
                return@launch
            }

            _singleBackupState.value = BackupState.BackingUp("Backing up \"${entry.title.ifEmpty { "Untitled" }}\"...")

            val fileName = "${DateTime(entry.createdAt).toString("yyyy-MM-dd")}_${
                entry.title.ifEmpty { "Untitled" }
                    .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
                    .replace(" ", "_")
                    .take(50)
            }.md"
            val md = entryToMarkdown(entry)

            val success = commitFilesToGitLab(
                settings,
                mapOf("entries/$fileName" to md),
                "Backup: ${entry.title.ifEmpty { "Untitled" }} (${DateTime(entry.createdAt).toString("yyyy-MM-dd HH:mm")})"
            )

            if (success) {
                _singleBackupState.value = BackupState.Success(
                    "�?\"${entry.title.ifEmpty { "Untitled" }}\" backed up to GitLab"
                )
            } else {
                _singleBackupState.value = BackupState.Error("Upload failed. Check if repository exists.")
            }
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Single backup failed", e)
            _singleBackupState.value = BackupState.Error("Backup failed: ${e.message}")
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

// ===================== 数据�?=====================

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
