package com.example.nvhspectro

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * [plan 3.3] Application-scoped object graph: the ONE MeasurementSession the
 * three ViewModels share. Recreated only with the process.
 */
object AppGraph {
    val session: MeasurementSession by lazy {
        MeasurementSession(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

/** Builds the session-sharing ViewModels [plan 3.3]. */
class NvhViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
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
