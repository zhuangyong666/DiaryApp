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

    private lateinit var viewModel: DiaryViewModel

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            viewModel.addMediaAttachmentFromPending(AttachmentType.IMAGE)
        }
        viewModel.setPendingMediaUri(null)
    }

    private val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            viewModel.addMediaAttachmentFromPending(AttachmentType.VIDEO)
        }
        viewModel.setPendingMediaUri(null)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addMediaAttachment(it, AttachmentType.IMAGE) }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addMediaAttachment(it, AttachmentType.VIDEO) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DiaryApp", "MainActivity onCreate start")

        try {
            viewModel = viewModel()
            Log.d("DiaryApp", "ViewModel created")
        } catch (e: Exception) {
            Log.e("DiaryApp", "Failed to create ViewModel", e)
            e.printStackTrace()
        }

        setContent {
            try {
                Log.d("DiaryApp", "setContent called")

                val triggerCamera by viewModel.triggerCamera.collectAsState()
                val triggerVideo by viewModel.triggerVideo.collectAsState()
                val triggerPickImage by viewModel.triggerPickImage.collectAsState()
                val triggerPickVideo by viewModel.triggerPickVideo.collectAsState()

                LaunchedEffect(triggerCamera) {
                    if (triggerCamera) {
                        val uri = viewModel.createImageFileUri(this@MainActivity)
                        viewModel.setPendingMediaUri(uri)
                        viewModel.consumeCameraTrigger()
                        uri?.let { takePictureLauncher.launch(it) }
                    }
                }

                LaunchedEffect(triggerVideo) {
                    if (triggerVideo) {
                        val uri = viewModel.createVideoFileUri(this@MainActivity)
                        viewModel.setPendingMediaUri(uri)
                        viewModel.consumeVideoTrigger()
                        uri?.let { takeVideoLauncher.launch(it) }
                    }
                }

                LaunchedEffect(triggerPickImage) {
                    if (triggerPickImage) {
                        viewModel.consumePickImageTrigger()
                        pickImageLauncher.launch("image/*")
                    }
                }

                LaunchedEffect(triggerPickVideo) {
                    if (triggerPickVideo) {
                        viewModel.consumePickVideoTrigger()
                        pickVideoLauncher.launch("video/*")
                    }
                }

                Log.d("DiaryApp", "Rendering DiaryAppTheme")
                DiaryAppTheme {
                    DiaryNavHost(viewModel = viewModel)
                }
                Log.d("DiaryApp", "MainActivity onCreate complete")
            } catch (e: Exception) {
                Log.e("DiaryApp", "CRASH in setContent", e)
                e.printStackTrace()
                throw e
            }
        }
    }
}
