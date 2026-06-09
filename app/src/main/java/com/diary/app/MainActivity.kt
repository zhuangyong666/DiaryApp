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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.AttachmentType
import com.diary.app.ui.DiaryAppTheme
import com.diary.app.ui.DiaryNavHost
import com.diary.app.ui.DiaryViewModel

class MainActivity : ComponentActivity() {

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // handled inside composable
        }
    }

    private val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            // handled inside composable
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        // handled inside composable
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        // handled inside composable
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DiaryApp", "onCreate start")
        setContent {
            val vm: DiaryViewModel = viewModel()
            Log.d("DiaryApp", "ViewModel created")

            val triggerCamera by vm.triggerCamera.collectAsState()
            val triggerVideo by vm.triggerVideo.collectAsState()
            val triggerPickImage by vm.triggerPickImage.collectAsState()
            val triggerPickVideo by vm.triggerPickVideo.collectAsState()

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

            Log.d("DiaryApp", "Rendering UI")
            DiaryAppTheme { DiaryNavHost(viewModel = vm) }
            Log.d("DiaryApp", "onCreate complete")
        }
    }
}
