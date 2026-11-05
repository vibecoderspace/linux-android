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
import com.vibecoderspace.linux.LinuxProcess
import com.vibecoderspace.linux.LinuxProcessSpec
import com.vibecoderspace.linux.LinuxRuntime
import com.vibecoderspace.linux.LinuxRuntimeOptions
import com.vibecoderspace.linux.ProotBinaries
import com.vibecoderspace.linux.RootfsImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                options = LinuxRuntimeOptions(hostname = "linux"),
            )
        }
    }

    val status = remember {
        MutableStateFlow(
            if (binaries == null) {
                "PRoot binaries not found. Add libproot.so and libloader.so to the app's jniLibs."
            } else {
                "Ready"
            },
        )
    }
    val statusText by status.collectAsState()
    val log = remember { MutableStateFlow("") }
    val logText by log.collectAsState()
    val running = remember { MutableStateFlow<LinuxProcess?>(null) }
    val runningProcess by running.collectAsState()

    var image by remember { mutableStateOf<RootfsImage?>(null) }

    fun install() {
        val rt = runtime ?: return
        scope.launch {
            status.value = "Starting install"
            try {
                val installed = rt.ensureInstalled(DefaultProfiles.ubuntu) { progress ->
                    status.value = describe(progress)
                }
                image = installed
                status.value = "Installed ${installed.profile.displayName}"
            } catch (error: Exception) {
                status.value = "Install failed: ${error.message}"
            }
        }
    }

    fun run(command: List<String>) {
        val rt = runtime ?: return
        val installed = image
        if (installed == null) {
            status.value = "Install an image first"
            return
        }

        scope.launch {
            val label = command.joinToString(" ")
            status.value = "Running $label"
            try {
                val process = withContext(Dispatchers.IO) {
                    rt.launch(installed, LinuxProcessSpec(command = command))
                }
                running.value = process

                val output = withContext(Dispatchers.IO) {
                    process.output.bufferedReader().use { it.readText() }
                }
                val exitCode = withContext(Dispatchers.IO) { process.awaitExit() }

                log.value += "\n$ $label\n$output\n[exit $exitCode]"
                status.value = "Ready"
            } catch (error: Exception) {
                log.value += "\n$ $label\nERROR: ${error.message}"
                status.value = "Failed"
            } finally {
                running.value = null
            }
        }
    }

    fun stop() {
        running.value?.stop()
        running.value = null
        status.value = "Stopped"
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
            text = "PRoot runtime with host-supplied binaries.",
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
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { run(listOf("uname", "-a")) },
                enabled = image != null && runningProcess == null,
            ) {
                Text("uname -a")
            }
            OutlinedButton(
                onClick = { run(listOf("id")) },
                enabled = image != null && runningProcess == null,
            ) {
                Text("id")
            }
            OutlinedButton(
                onClick = { run(listOf("/bin/sh", "-c", "echo \$PATH")) },
                enabled = image != null && runningProcess == null,
            ) {
                Text("PATH")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { stop() },
            enabled = runningProcess != null,
        ) {
            Text("Stop")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Output",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = logText.ifBlank { "No output yet." },
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

private fun describe(progress: InstallProgress): String = when (progress.stage) {
    InstallProgress.Stage.DOWNLOADING -> {
        if (progress.fraction < 0f) {
            // Some mirrors (including cdimage.ubuntu.com) do not send a length.
            "Downloading…"
        } else {
            val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
            "Downloading $percent%"
        }
    }

    InstallProgress.Stage.EXTRACTING -> "Extracting"
    InstallProgress.Stage.CONFIGURING -> "Configuring"
}
