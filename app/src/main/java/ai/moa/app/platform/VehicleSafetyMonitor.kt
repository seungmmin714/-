package ai.moa.app.platform

import ai.moa.core.Safety
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Unknown is deliberately not permission to play. All callbacks run on the main thread. */
class VehicleSafetyMonitor(context: Context, private val onSafety: (Safety) -> Unit) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var source: SafetySource? = null
    private var generation = 0

    fun start() = onMain {
        disposeSource()
        val request = ++generation
        onSafety(Safety.UNKNOWN)
        // Keep android.car symbols out of this class so it can load on a normal phone.
        if (Build.VERSION.SDK_INT < 29 ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) return@onMain
        try {
            val newSource = AaosSafetySource(context, handler) { safety ->
                if (request == generation) onSafety(safety)
            }
            source = newSource
            newSource.start()
        } catch (_: LinkageError) {
            disposeSource()
            onSafety(Safety.UNKNOWN)
        } catch (_: Exception) {
            disposeSource()
            onSafety(Safety.UNKNOWN)
        }
    }

    fun close() = onMain {
        ++generation
        disposeSource()
        onSafety(Safety.UNKNOWN)
    }

    private fun disposeSource() {
        val oldSource = source
        source = null
        try { oldSource?.close() } catch (_: Exception) { } catch (_: LinkageError) { }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }
}

internal interface SafetySource {
    fun start()
    fun close()
}
