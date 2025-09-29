<h1>Telemetry Lab: README</h1>


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
<p align="left"> <img width="349" height="728" alt="Screenshot 2025-09-29 165307" src="https://github.com/user-attachments/assets/e7d00c3a-0641-4836-8f92-2d263446ade7" /> <br/> <img width="379" height="760" alt="Screenshot 2025-09-29 165322" src="https://github.com/user-attachments/assets/7ae560d5-7232-4c17-8100-0a85c4cdea53" /> <br/> <img width="376" height="762" alt="Screenshot 2025-09-29 165345" src="https://github.com/user-attachments/assets/1f33729f-b1bb-4370-a1a0-e54311f7c56c" /> </p>


