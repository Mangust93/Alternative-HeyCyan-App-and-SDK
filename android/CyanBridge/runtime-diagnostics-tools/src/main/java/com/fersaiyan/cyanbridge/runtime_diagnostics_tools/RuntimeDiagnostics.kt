package com.fersaiyan.cyanbridge.runtime_diagnostics_tools

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point for the optional :runtime-diagnostics-tools module.
 *
 * [install] wires up lightweight, real runtime instrumentation that records app events to
 * [RuntimeDiagnosticsStore]:
 *   - an "app start" event plus build / device facts,
 *   - an uncaught-exception handler that records a crash event then DELEGATES to the
 *     previously installed handler (so the normal crash path is preserved),
 *   - if the context is an [Application], activity lifecycle transitions
 *     (created / resumed / paused / destroyed) by simple class name.
 *
 * It is normally called automatically by [RuntimeDiagnosticsInitProvider] during process
 * startup, so no :app code change is needed. It is idempotent: a second call is a no-op.
 *
 * This module has NO dependency on :app and never references the real glasses / media /
 * BLE / P2P flow or the Moonshine runtime. It only observes lifecycle/crash signals the
 * Android framework already exposes and writes them to app-specific storage.
 */
object RuntimeDiagnostics {

    private const val TAG_APP = "APP"
    private const val TAG_CRASH = "CRASH"
    private const val TAG_LIFECYCLE = "LIFECYCLE"

    private val installed = AtomicBoolean(false)

    /** True once [install] has run successfully in this process. */
    val isInstalled: Boolean
        get() = installed.get()

    /**
     * Install runtime instrumentation. Safe to call multiple times — only the first call
     * has any effect. Never throws: any failure is swallowed so it cannot affect app
     * startup.
     */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val appContext = context.applicationContext ?: context
        runCatching { recordAppStart(appContext) }
        runCatching { installCrashHandler(appContext) }
        runCatching { registerLifecycleCallbacks(appContext) }
    }

    private fun recordAppStart(context: Context) {
        RuntimeDiagnosticsStore.append(context, TAG_APP, "App process start; runtime diagnostics installed")
        RuntimeDiagnosticsStore.append(
            context,
            TAG_APP,
            "build: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | fingerprint ${Build.FINGERPRINT}",
        )
    }

    private fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Record the real crash, then ALWAYS hand off to the previous handler so the
            // normal crash flow (system dialog / reporters) is preserved unchanged.
            runCatching {
                RuntimeDiagnosticsStore.append(
                    context,
                    TAG_CRASH,
                    "Uncaught exception on thread '${thread.name}'",
                    throwable,
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // No prior handler: don't swallow the crash — re-raise the default behavior.
                throw throwable as? RuntimeException ?: RuntimeException(throwable)
            }
        }
    }

    private fun registerLifecycleCallbacks(context: Context) {
        val app = context as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                record(activity, "created")

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) =
                record(activity, "resumed")

            override fun onActivityPaused(activity: Activity) =
                record(activity, "paused")

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) =
                record(activity, "destroyed")

            private fun record(activity: Activity, transition: String) {
                runCatching {
                    RuntimeDiagnosticsStore.append(
                        activity.applicationContext,
                        TAG_LIFECYCLE,
                        "${activity.javaClass.simpleName} $transition",
                    )
                }
            }
        })
    }
}
