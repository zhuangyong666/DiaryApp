package com.diary.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.diary.app.data.*
import org.joda.time.DateTime

// ===================== App Theme =====================

@Composable
fun DiaryAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF3700B3),
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF3700B3),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ===================== Permission helper =====================

@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    return object : PermissionState {
        override val isGranted get() = granted
        override fun launchPermissionRequest() { launcher.launch(permission) }
    }
}

interface PermissionState {
    val isGranted: Boolean
    fun launchPermissionRequest()
}

// ===================== Navigation =====================

sealed class Screen(val route: String) {
    object List : Screen("list")
    object Edit : Screen("edit")
    object Detail : Screen("detail/{entryId}") {
        fun createRoute(entryId: Long) = "detail/$entryId"
    }
    object Backup : Screen("backup")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryNavHost(
    viewModel: DiaryViewModel = viewModel()
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.List.route
    ) {
        composable(Screen.List.route) {
            DiaryListScreen(
                viewModel = viewModel,
                onNewEntry = {
                    viewModel.clearEditEntry()
                    navController.navigate(Screen.Edit.route)
                },
                onEntryClick = { entry ->
                    navController.navigate(Screen.Detail.createRoute(entry.id))
                },
                onBackup = { navController.navigate(Screen.Backup.route) }
            )
        }
        composable(Screen.Edit.route) {
            DiaryEditScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    viewModel.clearEditEntry()
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: 0L
            DiaryDetailScreen(
                viewModel = viewModel,
                entryId = entryId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.Edit.route) }
            )
        }
        composable(Screen.Backup.route) {
            BackupScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// ===================== 日记列表页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    viewModel: DiaryViewModel,
    onNewEntry: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit,
    onBackup: () -> Unit
) {
    val entries by viewModel.allEntries.collectAsStateWithLifecycle(emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val displayedEntries = remember(entries, showFavoritesOnly, searchQuery) {
        var result = if (showFavoritesOnly) entries.filter { it.isFavorite } else entries
        if (searchQuery.isNotEmpty()) {
            result = result.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索日记...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    } else {
                        Text("📔 我的日记", fontSize = 22.sp)
                    }
                },
                actions = {
                    if (showSearch) {
                        IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                            Icon(
                                if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏"
                            )
                        }
                        IconButton(onClick = onBackup) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "备份")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewEntry) {
                Icon(Icons.Default.Add, contentDescription = "新建")
            }
        }
    ) { padding ->
        if (displayedEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 64.sp)
                    Text(
                        if (showFavoritesOnly) "还没有收藏的日记" else "还没有日记",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "点击 + 开始写第一篇日记吧！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedEntries, key = { it.id }) { entry ->
                    DiaryEntryCard(entry = entry, onClick = { onEntryClick(entry) })
                }
            }
        }
    }
}

@Composable
fun DiaryEntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry.title.isEmpty()) "无标题" else entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (entry.isFavorite) {
                    Icon(Icons.Default.Favorite, contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFFFF5722), modifier = Modifier.size(20.dp))
                }
            }
            if (entry.content.isNotEmpty()) {
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateTime(entry.createdAt).toString("yyyy-MM-dd HH:mm"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.attachments.isNotEmpty()) {
                    Text("📷 ${entry.attachments.size}", style = MaterialTheme.typography.labelSmall)
                }
                if (entry.location != null) {
                    Text("📍", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (entry.attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entry.attachments.take(3)) { attachment ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(if (attachment.thumbnailUri.isNullOrEmpty()) attachment.uri else attachment.thumbnailUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ===================== 编辑页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditScreen(
    viewModel: DiaryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val editEntry by viewModel.editEntry.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(editEntry?.title ?: "") }
    var content by remember { mutableStateOf(editEntry?.content ?: "") }
    var attachments by remember { mutableStateOf<List<DiaryAttachment>>(editEntry?.attachments ?: emptyList()) }
    var location by remember { mutableStateOf(editEntry?.location) }

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // AI 编写对话框
    var showAIDialog by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf("") }

    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()

    LaunchedEffect(editEntry) {
        title = editEntry?.title ?: ""
        content = editEntry?.content ?: ""
        attachments = editEntry?.attachments ?: emptyList()
        location = editEntry?.location
    }

    // Monitor attachments from ViewModel (when user picks media)
    LaunchedEffect(editEntry?.attachments?.size) {
        editEntry?.let { attachments = it.attachments }
    }

    val isEdit = editEntry != null && editEntry.id != 0L
    val currentEntry = editEntry

    val saveDiary = {
        val entry = if (isEdit && currentEntry != null) {
            currentEntry.copy(
                title = title, content = content,
                attachments = attachments, location = location,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            DiaryEntry(title = title, content = content, attachments = attachments, location = location)
        }
        if (isEdit) viewModel.updateEntry(entry) else viewModel.insertEntry(entry)
        onNavigateBack()
    }

    // AI 编写
    val doAiWrite = {
        if (aiPrompt.isEmpty()) {
            aiResult = "请输入提示词"
        } else {
            aiLoading = true
            aiResult = ""
            viewModel.aiWrite(aiPrompt, aiConfig) { result ->
                aiLoading = false
                aiResult = result
                if (result.isNotEmpty() && result.startsWith("错误").not()) {
                    content = content + (if (content.isNotEmpty()) "\n\n" else "") + result
                    showAIDialog = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑日记" else "新建日记") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAIDialog = true }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "AI编写")
                    }
                    IconButton(onClick = saveDiary) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                placeholder = { Text("标题（可选）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                placeholder = { Text("今天发生了什么？") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), minLines = 5
            )
            // AI 编写按钮行
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showAIDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("✨ AI 帮我写")
                }
            }
            // 附件展示
            if (attachments.isNotEmpty()) {
                Text("📎 附件 (${attachments.size})", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { attachment ->
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(if (attachment.thumbnailUri.isNullOrEmpty()) attachment.uri else attachment.thumbnailUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp)
                            )
                            IconButton(
                                onClick = { attachments = attachments.filter { it.uri != attachment.uri } },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除",
                                    tint = androidx.compose.ui.graphics.Color.Red)
                            }
                        }
                    }
                }
            }
            // 位置
            if (location != null) {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📍 位置", fontWeight = FontWeight.Bold)
                            Text("纬度: ${location!!.latitude}, 经度: ${location!!.longitude}",
                                style = MaterialTheme.typography.bodySmall)
                            if (location!!.address != null) {
                                Text(location!!.address!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { location = null }) {
                            Icon(Icons.Default.Close, contentDescription = "删除")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 选择图片/视频
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.triggerPickImageAction() }, modifier = Modifier.weight(1f)) {
                    Text("🖼️ 选图片")
                }
                OutlinedButton(onClick = { viewModel.triggerPickVideoAction() }, modifier = Modifier.weight(1f)) {
                    Text("🎥 选视频")
                }
            }
            // 获取位置
            Button(
                onClick = {
                    if (locationPermission.isGranted) {
                        viewModel.getCurrentLocation(context) { loc -> location = loc }
                    } else {
                        locationPermission.launchPermissionRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("📍 获取当前位置")
            }
        }

        // AI 编写对话框
        if (showAIDialog) {
            AlertDialog(
                onDismissRequest = { if (!aiLoading) showAIDialog = false },
                title = { Text("✨ AI 帮我写") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("告诉 AI 你想写什么内容的日记：", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = aiPrompt,
                            onValueChange = { aiPrompt = it },
                            placeholder = { Text("例如：今天去了海边，天气很好，心情很愉快") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            enabled = !aiLoading
                        )
                        // AI 配置
                        Text("AI 接口配置：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = aiConfig.apiUrl,
                            onValueChange = { newUrl -> viewModel.updateAiConfig(newUrl, aiConfig.apiKey) },
                            label = { Text("API URL") },
                            placeholder = { Text("http://127.0.0.1:11434/api/generate") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !aiLoading
                        )
                        OutlinedTextField(
                            value = aiConfig.apiKey,
                            onValueChange = { newKey -> viewModel.updateAiConfig(aiConfig.apiUrl, newKey) },
                            label = { Text("API Key（可选）") },
                            placeholder = { Text("Bearer token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !aiLoading
                        )
                        if (aiLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("AI 正在生成中...")
                            }
                        }
                        if (aiResult.isNotEmpty()) {
                            SelectionContainer {
                                Text(aiResult, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!aiLoading) {
                        TextButton(onClick = { doAiWrite() }) {
                            if (aiResult.isNotEmpty() && !aiResult.startsWith("错误")) Text("插入内容")
                            else Text("生成")
                        }
                    }
                },
                dismissButton = {
                    if (!aiLoading) {
                        TextButton(onClick = { showAIDialog = false }) { Text("取消") }
                    }
                }
            )
        }
    }
}

// ===================== 详情页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    viewModel: DiaryViewModel,
    entryId: Long,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit
) {
    var entry by remember { mutableStateOf<DiaryEntry?>(null) }
    val context = LocalContext.current

    LaunchedEffect(entryId) {
        entry = viewModel.getEntryById(entryId)
    }

    if (entry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val e = entry!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e.title.ifEmpty { "日记详情" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setEditEntry(e); onEdit() }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { viewModel.toggleFavorite(e); entry = e.copy(isFavorite = !e.isFavorite) }) {
                        Icon(
                            if (e.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (e.isFavorite) androidx.compose.ui.graphics.Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    var showDeleteDialog by remember { mutableStateOf(false) }
                    var showBackupDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                    IconButton(onClick = { showBackupDialog = true }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "备份")
                    }
                    if (showBackupDialog) {
                        val singleBackup by viewModel.singleBackupState.collectAsStateWithLifecycle()
                        AlertDialog(
                            onDismissRequest = { if (singleBackup !is BackupState.BackingUp) showBackupDialog = false },
                            title = { Text("备份到 GitLab") },
                            text = {
                                Column {
                                    Text("将这篇日记以 Markdown 格式备份到 GitLab。")
                                    when (singleBackup) {
                                        is BackupState.BackingUp -> {
                                            Spacer(Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("正在备份...")
                                            }
                                        }
                                        is BackupState.Success -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text("✅ 备份成功！", color = MaterialTheme.colorScheme.primary)
                                        }
                                        is BackupState.Error -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text((singleBackup as BackupState.Error).message, color = MaterialTheme.colorScheme.error)
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            confirmButton = {
                                if (singleBackup !is BackupState.BackingUp) {
                                    TextButton(onClick = {
                                        if (singleBackup is BackupState.Success) {
                                            showBackupDialog = false; viewModel.clearSingleBackupState()
                                        } else {
                                            viewModel.backupSingleEntry(e)
                                        }
                                    }) {
                                        if (singleBackup is BackupState.Success) Text("关闭") else Text("确认备份")
                                    }
                                }
                            },
                            dismissButton = {
                                if (singleBackup !is BackupState.BackingUp) {
                                    TextButton(onClick = { showBackupDialog = false; viewModel.clearSingleBackupState() }) {
                                        Text("取消")
                                    }
                                }
                            }
                        )
                    }
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("删除日记") },
                            text = { Text("确定要删除这篇日记吗？此操作不可撤销。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteEntry(e)
                                    showDeleteDialog = false
                                    onNavigateBack()
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅 创建", style = MaterialTheme.typography.labelMedium)
                        Text(DateTime(e.createdAt).toString("yyyy-MM-dd HH:mm"),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✏️ 修改", style = MaterialTheme.typography.labelMedium)
                        Text(DateTime(e.updatedAt).toString("yyyy-MM-dd HH:mm"),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (e.content.isNotEmpty()) {
                Text(text = e.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            }
            if (e.attachments.isNotEmpty()) {
                Text("📎 附件 (${e.attachments.size})", fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(e.attachments) { attachment ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                when (attachment.type) {
                                    AttachmentType.IMAGE -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(attachment.uri)
                                                .crossfade(true).build(),
                                            contentDescription = attachment.fileName,
                                            modifier = Modifier.fillMaxWidth().height(250.dp)
                                        )
                                    }
                                    AttachmentType.VIDEO -> {
                                        Box(modifier = Modifier.fillMaxWidth().height(200.dp),
                                            contentAlignment = Alignment.Center) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(attachment.thumbnailUri ?: attachment.uri)
                                                    .crossfade(true).build(),
                                                contentDescription = attachment.fileName,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Icon(Icons.Default.PlayCircle, contentDescription = "播放",
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(64.dp))
                                        }
                                    }
                                }
                                if (attachment.fileName.isNotEmpty()) {
                                    Text(attachment.fileName,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (e.location != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📍 位置", fontWeight = FontWeight.Bold)
                        Text("纬度: ${e.location!!.latitude}, 经度: ${e.location!!.longitude}",
                            style = MaterialTheme.typography.bodyMedium)
                        if (e.location!!.address != null) {
                            Text(e.location!!.address!!, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = {
                            val uri = Uri.parse("geo:${e.location!!.latitude},${e.location!!.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }) {
                            Text("🗺️ 在地图中查看")
                        }
                    }
                }
            }
        }
    }
}

// ===================== 备份页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: DiaryViewModel,
    onNavigateBack: () -> Unit
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val singleBackupState by viewModel.singleBackupState.collectAsStateWithLifecycle()

    var gitlabUrl by remember { mutableStateOf("") }
    var gitlabToken by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("diary-backup") }

    LaunchedEffect(Unit) {
        val settings = viewModel.loadBackupSettings()
        gitlabUrl = settings.gitlabUrl
        gitlabToken = settings.gitlabToken
        repoName = settings.repoName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("☁️ GitLab 备份") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 使用说明", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "1. 输入你的 GitLab 实例 URL\n" +
                        "2. 输入 Personal Access Token（需要 api + write_repository 权限）\n" +
                        "3. 设置仓库名称\n" +
                        "4. 点击「全量备份」即可自动创建仓库并推送",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚙️ 配置", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = gitlabUrl, onValueChange = { gitlabUrl = it },
                        label = { Text("GitLab URL") }, placeholder = { Text("https://gitlab.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = gitlabToken, onValueChange = { gitlabToken = it },
                        label = { Text("Personal Access Token") }, placeholder = { Text("glpat-xxxx") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = repoName, onValueChange = { repoName = it },
                        label = { Text("仓库名称") }, placeholder = { Text("diary-backup") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }
            Button(
                onClick = {
                    viewModel.saveBackupSettings(gitlabUrl, gitlabToken, repoName)
                    viewModel.startBackup()
                },
                enabled = backupState !is BackupState.BackingUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (backupState is BackupState.BackingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("📦 全量备份到 GitLab")
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (val state = backupState) {
                is BackupState.Idle -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(12.dp)) { Text("✅ 就绪", fontWeight = FontWeight.Bold) }
                    }
                }
                is BackupState.BackingUp -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(state.message, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                }
                is BackupState.Success -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✅ 备份成功！", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is BackupState.Error -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("❌ 备份失败", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
