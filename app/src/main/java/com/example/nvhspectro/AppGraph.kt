package com.example.nvhspectro

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.nvhspectro.data.DiagnosticLog
import com.example.nvhspectro.data.SettingsStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [plan 3.3] Application-scoped object graph: the ONE MeasurementSession the
 * three ViewModels share. Recreated only with the process.
 */
object AppGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistenceStarted = AtomicBoolean(false)

    val session: MeasurementSession by lazy { MeasurementSession(scope) }

    /**
     * [S1, plan 3.6] Restore persisted settings/kinematics into the session,
     * then write every later change back. Idempotent; restore completes
     * before observation starts so defaults never clobber stored values.
     */
    fun startPersistence(application: Application) {
        if (!persistenceStarted.compareAndSet(false, true)) return

        // [V3, plan 4.7] Every user-facing notice is also written to the local diagnostic
        // log, so a field failure leaves a trace the operator can send afterwards instead of
        // vanishing with the session.
        DiagnosticLog.init(application)
        DiagnosticLog.i("Session", "NVH Spectro v${BuildConfig.VERSION_NAME} — nouvelle session")
        scope.launch {
            session.analysisNotice.collect { notice -> notice?.let { DiagnosticLog.notice(it) } }
        }

        val store = SettingsStore(application)
        scope.launch {
            store.restoreInto(session)
            store.startObserving(session, scope)
        }
    }
}

/** Builds the session-sharing ViewModels [plan 3.3]. */
class NvhViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    init {
        AppGraph.startPersistence(application)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val session = AppGraph.session
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(LiveViewModel::class.java) -> LiveViewModel(application, session)
            modelClass.isAssignableFrom(AnalyzerViewModel::class.java) -> AnalyzerViewModel(application, session)
            modelClass.isAssignableFrom(ReportViewModel::class.java) -> ReportViewModel(application, session)
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        } as T
    }
}
