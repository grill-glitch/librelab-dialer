package org.librelab.dialer.ui.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.librelab.dialer.data.calllog.CallLogRepository
import org.librelab.dialer.data.contacts.ContactsRepository
import org.librelab.dialer.domain.model.CallLogEntry
import org.librelab.dialer.domain.model.CallLogGroup
import javax.inject.Inject

/**
 * CallLog ViewModel — loads and groups call log entries.
 * Core logic migrated from CallLogFragment.java and CallLogGroupBuilder.java.
 */
@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    private val _callLogGroups = MutableStateFlow<List<CallLogGroup>>(emptyList())
    val callLogGroups: StateFlow<List<CallLogGroup>> = _callLogGroups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.ALL)
    val selectedFilter: StateFlow<CallLogFilter> = _selectedFilter.asStateFlow()

    init {
        loadCallLog()
    }

    fun loadCallLog() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entries = callLogRepository.getCallLog(limit = 500)
                val groups = callLogRepository.groupCallLogEntries(entries)
                _callLogGroups.value = groups
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: CallLogFilter) {
        _selectedFilter.value = filter
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filterParam = if (filter == CallLogFilter.ALL) null else filter
                val entries = callLogRepository.getCallLog(limit = 500, filter = filterParam)
                val groups = callLogRepository.groupCallLogEntries(entries)
                _callLogGroups.value = groups
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            callLogRepository.deleteCallLogEntry(entryId)
            loadCallLog()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            callLogRepository.clearAllCallLog()
            _callLogGroups.value = emptyList()
        }
    }

    fun clearCallLog() {
        clearAll()
    }

    fun showSettings() {
        // Settings navigation is handled by MainActivity — emit event when needed
    }
}

enum class CallLogFilter {
    ALL, INCOMING, OUTGOING, MISSED
}
