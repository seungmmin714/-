package ai.moa.app.platform

import ai.moa.core.Safety
import android.annotation.TargetApi
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler

/** Loaded only on Automotive API 29+. Does not infer parking from speed or a user switch. */
@TargetApi(29)
internal class AaosSafetySource(
    private val context: Context,
    private val handler: Handler,
    private val onSafety: (Safety) -> Unit,
) : SafetySource {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var active = false
    private var connected = false
    private var connectionGeneration = 0

    override fun start() {
        active = true
        onSafety(Safety.UNKNOWN)
        car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
            if (!active) return@createCar
            val connection = ++connectionGeneration
            connected = ready
            if (!ready) {
                manager = null
                onSafety(Safety.UNKNOWN)
            } else {
                try {
                    manager?.unregisterListener()
                    val ux = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                        as? CarUxRestrictionsManager
                    manager = ux
                    if (ux == null) {
                        onSafety(Safety.UNKNOWN)
                    } else {
                        val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                            if (active && connected && connection == connectionGeneration) publish(restrictions)
                        }
                        ux.registerListener(listener)
                        publish(ux.currentCarUxRestrictions)
                    }
                } catch (_: Exception) {
                    onSafety(Safety.UNKNOWN)
                } catch (_: LinkageError) {
                    onSafety(Safety.UNKNOWN)
                }
            }
        }
        if (car == null) onSafety(Safety.UNKNOWN)
    }

    private fun publish(restrictions: CarUxRestrictions?) {
        val safety = when {
            restrictions == null -> Safety.UNKNOWN
            restrictions.isRequiresDistractionOptimization -> Safety.BLOCKED
            restrictions.activeRestrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE -> Safety.BLOCKED
            else -> Safety.ALLOWED
        }
        onSafety(safety)
    }

    override fun close() {
        active = false
        connected = false
        ++connectionGeneration
        try { manager?.unregisterListener() } catch (_: Exception) { } catch (_: LinkageError) { }
        manager = null
        try { car?.disconnect() } catch (_: Exception) { } catch (_: LinkageError) { }
        car = null
    }
}
