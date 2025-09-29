package com.akb.telemetrylab

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.sqrt

class TelemetryService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // Coroutine scope for service
    private var running = false // Flag to track if the service is running

    companion object {
        const val NOTIF_CHANNEL_ID = "telemetry_lab_channel" // Notification channel ID for foreground service
        const val NOTIF_ID = 1001 // Notification ID for foreground service
        const val ACTION_START = "com.example.telemetrylab.action.START" // Action for starting the service
        const val ACTION_STOP = "com.example.telemetrylab.action.STOP" // Action for stopping the service

        const val EXTRA_COMPUTE_LOAD = "extra_compute_load" // Extra for specifying compute load
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called by the system every time a client explicitly starts the service by calling
     * startService().
     * If you override this method, be sure to stop the service as appropriate,
     * using stopSelf() or stopService().
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val load = intent.getIntExtra(EXTRA_COMPUTE_LOAD, 2)

                try {
                    startForeground(
                        NOTIF_ID,
                        buildNotification(load),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )

                } catch (e: ForegroundServiceStartNotAllowedException) {
                    e.printStackTrace()
                }
                startWork(load)
            }
            ACTION_STOP -> stopSelf()
            else -> {
                startForeground(
                    NOTIF_ID,
                    buildNotification(2),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )

                startWork(2)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        running = false
        super.onDestroy()
    }

    /**
     * Creates a notification channel for the foreground service.
     */
    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Telemetry Lab",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(ch)
    }

    /**
     * Starts the work for the service.
     * @param initialLoad The initial compute load.
     */
    private fun startWork(initialLoad: Int) {
        if (running) return
        running = true

        scope.launch {
            var computeLoad = initialLoad.coerceIn(1, 5)
            var count = 0L
            var mean = 0.0
            var m2 = 0.0
            val buffer = FloatArray(256 * 256) { (it % 255) / 255f }

            val frameTimes = ArrayDeque<Pair<Long, Double>>() // timestamp to frame time

            while (isActive) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                val isPowerSave = pm.isPowerSaveMode
                val hz = if (isPowerSave) 10 else 20
                val effectiveLoad = if (isPowerSave) max(1, computeLoad - 1) else computeLoad

                val frameStart = SystemClock.elapsedRealtimeNanos()

                withContext(Dispatchers.Default) {
                    repeat(effectiveLoad) {
                        convolutionPass(buffer, 256, 256)
                    }
                }

                val frameEnd = SystemClock.elapsedRealtimeNanos()
                val frameMs = (frameEnd - frameStart) / 1_000_000.0

                count++
                val delta = frameMs - mean
                mean += delta / count
                m2 += delta * (frameMs - mean)
                val variance = if (count > 1) m2 / (count - 1) else 0.0
                val std = sqrt(variance)

                val now = SystemClock.elapsedRealtime()
                frameTimes.addLast(now to frameMs)
                while (frameTimes.isNotEmpty() && now - frameTimes.first().first > 30_000) {
                    frameTimes.removeFirst()
                }
                val jankThreshold = 50.0 // ms threshold for jank
                val jankFrames = frameTimes.count { it.second > jankThreshold }
                val jankPercent = if (frameTimes.isNotEmpty())
                    (jankFrames.toDouble() / frameTimes.size) * 100.0 else 0.0

                // Post metrics to repository
                TelemetryRepository.post(
                    TelemetryMetrics(
                        timestampMs = now,
                        lastFrameMs = frameMs,
                        movingAvgMs = mean,
                        movingStdMs = std,
                        jankPercent30s = jankPercent,
                        jankCount30s = jankFrames,
                        effectiveHz = hz,
                        computeLoad = effectiveLoad
                    )
                )

                val frameDurationMs = 1000.0 / hz
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - frameStart) / 1_000_000.0
                val remainingMs = frameDurationMs - elapsedMs
                if (remainingMs > 0) delay(remainingMs.toLong()) else yield()
            }
        }
    }

    /**
     * Builds the notification for the foreground service.
     */
    private fun buildNotification(load: Int): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Telemetry Lab running")
            .setContentText("Compute load: $load")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    /**
     * Performs a convolution pass on the input data.
     * @param data The input data.
     * @param w The width of the input data.
     * @param h The height of the input data.
     */
    private fun convolutionPass(data: FloatArray, w: Int, h: Int): FloatArray {
        val kernel = floatArrayOf(
            1f/9f, 1f/9f, 1f/9f,
            1f/9f, 1f/9f, 1f/9f,
            1f/9f, 1f/9f, 1f/9f
        )
        val out = FloatArray(data.size)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = 0f
                for (ky in -1..1) {
                    val yy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -1..1) {
                        val xx = (x + kx).coerceIn(0, w - 1)
                        val kval = kernel[(ky + 1) * 3 + (kx + 1)]
                        acc += data[yy * w + xx] * kval
                    }
                }
                out[idx++] = acc
            }
        }
        return out
    }
}