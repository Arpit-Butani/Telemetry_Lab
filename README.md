**#Threading & Backpressure Approach**
* Telemetry computation runs within a dedicated coroutine scope on a Dispatchers.Default thread pool for high efficiency and concurrency. The foreground service uses an atomic flag to manage workload state and leverages Kotlin coroutines for structured concurrency and automatic cancellation. Backpressure is handled by posting telemetry metrics at interval after frames are processed, so UI observers only receive the latest computed stats through a SharedFlow that naturally buffers and drops outdated emissions if collector(s) lag.
* Real-time frame metrics are collected and delivered via a SharedFlow with replay size one, preventing outdated backlog and avoiding memory leaks while ensuring immediate delivery to UI.
* Heavy compute is segmented via suspend functions and periodically yields/pauses, giving the runtime and UI time to process other tasks, and minimizing ANRs.


**#Why Foreground Service, Not WorkManager?**
* Foreground Service (FGS) provides a persistent compute process that reliably delivers real-time telemetry with a visible notification for users—this is essential for continuous performance monitoring when the app is visible or in background.
* WorkManager is ideal for deferred, batch, or periodic work where timely delivery isn't guaranteed; Android may throttle or delay workers due to battery policies.
* The service needs non-deferrable, instant computation and ongoing reporting. FGS ensures the process remains alive, runs instantly, and avoids delays or quota limitations common to WorkManager, especially for tasks requiring minute-level precision and instant reporting.
* For telemetry/jank measurement where users expect immediate feedback, FGS offers reliability and explicit control that aligns with Android’s best practices for high-priority, long-running foreground work.

**#JankStats Output (Sample @load=2)**

<img src="C:\Users\asus\Pictures\Screenshots\Screenshot 2025-09-29 165307.png"/>
These values can be viewed in logcat or the app UI as live-updating telemetry (simulate by setting slider to 2 and running service for 30s).

This design prioritizes high-precision, low-latency telemetry for UX and developer diagnostics, adhering to Android's most reliable service patterns for real-time, long-running tasks.

