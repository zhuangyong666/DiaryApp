package com.diary.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.AttachmentType
import com.diary.app.ui.DiaryAppTheme
import com.diary.app.ui.DiaryNavHost
import com.diary.app.ui.DiaryViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DiaryApp", "onCreate start")

        setContent {
            val vm: DiaryViewModel = viewModel()
            Log.d("DiaryApp", "ViewModel created")

            val pickImageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    Log.d("DiaryApp", "Image picked: $uri")
                    vm.addMediaAttachment(uri, AttachmentType.IMAGE)
                }
            }

            val pickVideoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    Log.d("DiaryApp", "Video picked: $uri")
                    vm.addMediaAttachment(uri, AttachmentType.VIDEO)
                }
            }

            val takePictureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    vm.pendingMediaUri.value?.let { uri ->
                        vm.addMediaAttachment(uri, AttachmentType.IMAGE)
                    }
                }
                vm.setPendingMediaUri(null)
            }

            val takeVideoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CaptureVideo()
            ) { success ->
                if (success) {
                    vm.pendingMediaUri.value?.let { uri ->
                        vm.addMediaAttachment(uri, AttachmentType.VIDEO)
                    }
                }
                vm.setPendingMediaUri(null)
            }

            val triggerPickImage by vm.triggerPickImage.collectAsState()
            val triggerPickVideo by vm.triggerPickVideo.collectAsState()
            val triggerCamera by vm.triggerCamera.collectAsState()
            val triggerVideo by vm.triggerVideo.collectAsState()

            LaunchedEffect(triggerPickImage) {
                if (triggerPickImage) {
                    vm.consumePickImageTrigger()
                    pickImageLauncher.launch("image/*")
                }
            }

            LaunchedEffect(triggerPickVideo) {
                if (triggerPickVideo) {
                    vm.consumePickVideoTrigger()
                    pickVideoLauncher.launch("video/*")
                }
            }

            LaunchedEffect(triggerCamera) {
                if (triggerCamera) {
                    val uri = vm.createImageFileUri(this@MainActivity)
                    vm.setPendingMediaUri(uri)
                    vm.consumeCameraTrigger()
                    uri?.let { takePictureLauncher.launch(it) }
                }
            }

            LaunchedEffect(triggerVideo) {
                if (triggerVideo) {
                    val uri = vm.createVideoFileUri(this@MainActivity)
                    vm.setPendingMediaUri(uri)
                    vm.consumeVideoTrigger()
                    uri?.let { takeVideoLauncher.launch(it) }
                }
            }

            DiaryAppTheme { DiaryNavHost(viewModel = vm) }
        }
    }
}
