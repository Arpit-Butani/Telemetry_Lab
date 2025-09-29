package com.akb.telemetrylab

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data class representing telemetry metrics.
 * @property timestampMs The timestamp in milliseconds.
 * @property lastFrameMs The last frame time in milliseconds.
 * @property movingAvgMs The moving average frame time in milliseconds.
 * @property movingStdMs The moving standard deviation of frame times in milliseconds.
 * @property jankPercent30s The percentage of frames that are over 50ms.
 * @property jankCount30s The number of frames that are over 50ms.
 * @property effectiveHz The effective frames per second.
 * @property computeLoad The compute load.
 */
data class TelemetryMetrics(
    val timestampMs: Long,
    val lastFrameMs: Double,
    val movingAvgMs: Double,
    val movingStdMs: Double,
    val jankPercent30s: Double,
    val jankCount30s: Int,
    val effectiveHz: Int,
    val computeLoad: Int
)

/**
 * Repository for telemetry metrics.
 */
object TelemetryRepository {
    private val _metrics = MutableSharedFlow<TelemetryMetrics>(replay = 1)
    val metrics = _metrics.asSharedFlow()


    suspend fun post(m: TelemetryMetrics) {
        _metrics.emit(m)
    }
}