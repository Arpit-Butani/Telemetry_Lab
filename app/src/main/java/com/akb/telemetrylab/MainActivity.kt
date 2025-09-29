package com.akb.telemetrylab

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelemetryLabScreen()
        }
    }
}

@Composable
fun TelemetryLabScreen() {
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(2f) }
    var metrics by remember { mutableStateOf<TelemetryMetrics?>(null) }

// Collect real metrics from repository
    LaunchedEffect(Unit) {
        TelemetryRepository.metrics.collectLatest { m ->
            metrics = m
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Telemetry Lab") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Button(onClick = {
                    if (!started) {
                        val intent = Intent(context, TelemetryService::class.java).apply {
                            action = TelemetryService.ACTION_START
                            putExtra(TelemetryService.EXTRA_COMPUTE_LOAD, sliderValue.toInt())
                        }
                        context.startForegroundService(intent)
                    } else {
                        val intent = Intent(context, TelemetryService::class.java).apply {
                            action = TelemetryService.ACTION_STOP
                        }
                        context.stopService(intent)
                    }
                    started = !started
                }) {
                    Text(if (started) "Stop" else "Start")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Compute Load: ${sliderValue.toInt()}")
            }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 1f..5f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            metrics?.let { m ->
                Text("Last frame: ${String.format("%.2f", m.lastFrameMs)} ms")
                Text("Moving avg: ${String.format("%.2f", m.movingAvgMs)} ms")
                Text("Std dev: ${String.format("%.2f", m.movingStdMs)} ms")
                Text("Jank (30s): ${String.format("%.1f", m.jankPercent30s)}% (${m.jankCount30s} frames)")
                Text("Power Save Mode: ${if (isPowerSaveMode(context)) "On" else "Off"}")
                Text("Effective Hz: ${m.effectiveHz}")
            } ?: Text("Waiting for metrics…")

            Spacer(modifier = Modifier.height(16.dp))

            ScrollingList()
        }
    }
}

@Composable
fun ScrollingList() {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val items = remember { List(1000) { "Demo Item For Scrolling List #$it" } }

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            kotlinx.coroutines.delay(500)
            index = (index + 1) % items.size
            scope.launch { state.animateScrollToItem(index) }
        }
    }

    LazyColumn(state = state, modifier = Modifier.fillMaxHeight().height(300.dp)) {
        items(items.size) { i ->
            Text(text = items[i], modifier = Modifier.padding(8.dp))
        }
    }

}

fun isPowerSaveMode(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isPowerSaveMode
}
