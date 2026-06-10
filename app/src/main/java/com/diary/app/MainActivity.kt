package com.diary.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

            var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
            var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

            val pickImageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                selectedImageUri = uri
                Log.d("DiaryApp", "Image picked: $uri")
            }

            val pickVideoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                selectedVideoUri = uri
                Log.d("DiaryApp", "Video picked: $uri")
            }

            // Handle picked image
            LaunchedEffect(selectedImageUri) {
                selectedImageUri?.let { uri ->
                    vm.addMediaAttachment(uri, AttachmentType.IMAGE)
                    selectedImageUri = null
                }
            }

            // Handle picked video
            LaunchedEffect(selectedVideoUri) {
                selectedVideoUri?.let { uri ->
                    vm.addMediaAttachment(uri, AttachmentType.VIDEO)
                    selectedVideoUri = null
                }
            }

            // Trigger pick image
            LaunchedEffect(vm.triggerPickImage.collectAsState().value) {
                if (vm.triggerPickImage.value) {
                    vm.consumePickImageTrigger()
                    pickImageLauncher.launch("image/*")
                }
            }

            // Trigger pick video
            LaunchedEffect(vm.triggerPickVideo.collectAsState().value) {
                if (vm.triggerPickVideo.value) {
                    vm.consumePickVideoTrigger()
                    pickVideoLauncher.launch("video/*")
                }
            }

            // Trigger camera
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

            LaunchedEffect(vm.triggerCamera.collectAsState().value) {
                if (vm.triggerCamera.value) {
                    val uri = vm.createImageFileUri(this@MainActivity)
                    vm.setPendingMediaUri(uri)
                    vm.consumeCameraTrigger()
                    uri?.let { takePictureLauncher.launch(it) }
                }
            }

            // Trigger video recording
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

            LaunchedEffect(vm.triggerVideo.collectAsState().value) {
                if (vm.triggerVideo.value) {
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
