package com.diary.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.activity.result.contract.ActivityResultContracts.CaptureVideo
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

    private val takePictureLauncher = registerForActivityResult(TakePicture()) { success ->
        if (success) {
            viewModel.addMediaAttachmentFromPending(AttachmentType.IMAGE)
        }
        viewModel.setPendingMediaUri(null)
    }

    private val takeVideoLauncher = registerForActivityResult(CaptureVideo()) { success ->
        if (success) {
            viewModel.addMediaAttachmentFromPending(AttachmentType.VIDEO)
        }
        viewModel.setPendingMediaUri(null)
    }

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addMediaAttachment(it, AttachmentType.IMAGE) }
    }

    private val pickVideoLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addMediaAttachment(it, AttachmentType.VIDEO) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            viewModel = viewModel()

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
                    pickImageLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                }
            }

            LaunchedEffect(triggerPickVideo) {
                if (triggerPickVideo) {
                    viewModel.consumePickVideoTrigger()
                    pickVideoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                }
            }

            DiaryAppTheme {
                DiaryNavHost(viewModel = viewModel)
            }
        }
    }
}
