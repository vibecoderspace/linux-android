package com.vibecoderspace.linux.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecoderspace.linux.DefaultProfiles
import com.vibecoderspace.linux.InstallProgress
import com.vibecoderspace.linux.LinuxRuntime
import com.vibecoderspace.linux.ProotBinaries
import com.vibecoderspace.linux.RootfsImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleScreen()
                }
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val binaries = remember { ProotBinaries.fromNativeLibraryDir(context) }
    val runtime = remember(binaries) {
        binaries?.let {
            LinuxRuntime.proot(
                context = context,
                binaries = it,
            )
        }
    }

    var image by remember { mutableStateOf(runtime?.installedImage()) }
    val status = remember {
        MutableStateFlow(
            when {
                binaries == null ->
                    "PRoot binaries not found. Add libproot.so and libloader.so to jniLibs."
                image != null -> "Installed ${image?.profile?.displayName}"
                else -> "No image installed"
            },
        )
    }
    val statusText by status.collectAsState()

    fun install() {
        val rt = runtime ?: return
        scope.launch {
            try {
                val installed = rt.install(DefaultProfiles.ubuntu) { progress ->
                    status.value = describe(progress)
                }
                image = installed
                status.value = "Installed ${installed.profile.displayName}"
            } catch (error: Exception) {
                status.value = "Install failed: ${error.message}"
            }
        }
    }

    fun reinstall() {
        val rt = runtime ?: return
        scope.launch {
            try {
                val reinstalled = rt.reinstall(keepHome = true) { progress ->
                    status.value = describe(progress)
                }
                image = reinstalled
                status.value = "Reinstalled ${reinstalled.profile.displayName}"
            } catch (error: Exception) {
                status.value = "Reinstall failed: ${error.message}"
            }
        }
    }

    fun delete() {
        val rt = runtime ?: return
        scope.launch {
            try {
                rt.delete()
                image = null
                status.value = "Deleted"
            } catch (error: Exception) {
                status.value = "Delete failed: ${error.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "linux-android",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Root filesystem provisioning with host-supplied PRoot binaries.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(16.dp))
        Text(text = statusText, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { install() },
                enabled = runtime != null && image == null,
            ) {
                Text("Install Ubuntu")
            }
            OutlinedButton(
                onClick = { reinstall() },
                enabled = image != null,
            ) {
                Text("Reinstall")
            }
            OutlinedButton(
                onClick = { delete() },
                enabled = image != null,
            ) {
                Text("Delete")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Image",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = image?.let { describeImage(it) } ?: "No image installed.",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

private fun describeImage(image: RootfsImage): String = buildString {
    appendLine("profile:   ${image.profile.displayName} (${image.profile.id})")
    appendLine("rootfs:    ${image.rootfsDir.absolutePath}")
    appendLine("sandbox:   ${image.layout.sandboxDir.absolutePath}")
    appendLine("home:      ${image.layout.homeDir.absolutePath}")
    appendLine("installed: ${image.installedAt}")
}

private fun describe(progress: InstallProgress): String = when (progress.stage) {
    InstallProgress.Stage.CHECKING -> "Checking"

    InstallProgress.Stage.DOWNLOADING -> {
        if (progress.fraction < 0f) {
            // Some mirrors (including cdimage.ubuntu.com) do not send a length.
            "Downloading…"
        } else {
            val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
            "Downloading $percent%"
        }
    }

    InstallProgress.Stage.VERIFYING -> "Verifying"
    InstallProgress.Stage.EXTRACTING -> "Extracting"
    InstallProgress.Stage.CONFIGURING -> "Configuring"
    InstallProgress.Stage.READY -> "Ready"
}
