package ai.markr.phoneagent.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.markr.phoneagent.data.RunHistoryDao
import ai.markr.phoneagent.data.RunRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: RunHistoryDao,
) : ViewModel() {

    val records: StateFlow<List<RunRecord>> = dao.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }
}
