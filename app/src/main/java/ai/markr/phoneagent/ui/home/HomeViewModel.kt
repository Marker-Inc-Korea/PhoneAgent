package ai.markr.phoneagent.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.markr.phoneagent.data.SettingsRepository
import ai.markr.phoneagent.platform.Permissions
import ai.markr.phoneagent.runtime.AgentRunState
import ai.markr.phoneagent.runtime.AgentRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class HomeReadiness(
    val accessibilityEnabled: Boolean = false,
    val configured: Boolean = false,
) {
    val ready: Boolean get() = accessibilityEnabled && configured
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runner: AgentRunner,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val runState: StateFlow<AgentRunState> = runner.state

    private val _readiness = MutableStateFlow(HomeReadiness())
    val readiness: StateFlow<HomeReadiness> = _readiness.asStateFlow()

    init {
        settingsRepository.settings
            .onEach { s -> _readiness.value = _readiness.value.copy(configured = s.isConfigured) }
            .launchIn(viewModelScope)
        refreshPermissions()
    }

    fun refreshPermissions() {
        _readiness.value = _readiness.value.copy(
            accessibilityEnabled = Permissions.isAccessibilityEnabled(context),
        )
    }

    fun run(task: String) = runner.start(task)
    fun stop() = runner.stop()
    fun reset() = runner.reset()
}
