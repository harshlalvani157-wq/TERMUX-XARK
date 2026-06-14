package app.xodos2.ui

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

object LogcatLogger {
    private const val TAG = "LogcatLogger"
    private var captureThread: Thread? = null
    @Volatile private var running = false
    private var logcatProcess: java.lang.Process? = null
    private var writer: FileWriter? = null

    fun start(context: Context) {
        if (running) return
        running = true

        val myPid = Process.myPid()

        captureThread = Thread {
            try {
                val logDir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    File(context.getExternalFilesDir(null), "logs")
                } else {
                    File(context.getExternalFilesDir(null), "logs")
                }
                if (!logDir.exists()) logDir.mkdirs()

                val logFile = File(logDir, "app.log")
                // Overwrite instead of append: false or omit the boolean
                writer = FileWriter(logFile, false)  // changed from true to false

                // Clear logcat synchronously (wait until cleared)
                try {
                    val clearProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
                    clearProcess.waitFor()
                    // Brief pause to ensure buffer is actually flushed
                    Thread.sleep(200)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear logcat (possibly no permission)", e)
                }

                // Start fresh capture
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time")
                )

                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream)
                )

                var line: String?
                while (running) {
                    line = reader.readLine() ?: break
                    if (line.contains(myPid.toString())) {
                        writer?.write(line + "\n")
                        writer?.flush()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Logcat capture error", e)
            } finally {
                cleanup()
            }
        }

        captureThread!!.start()
        Log.d(TAG, "Logcat capture started (overwriting log file)")
    }

    fun stop() {
        running = false
        cleanup()
    }

    private fun cleanup() {
        try {
            logcatProcess?.destroy()
            logcatProcess = null
        } catch (_: Exception) {}

        try {
            writer?.flush()
            writer?.close()
            writer = null
        } catch (_: Exception) {}

        captureThread?.interrupt()
        captureThread = null
    }
}