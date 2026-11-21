package com.example.data.repository

import com.example.data.model.InterventionToastState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DiagnosticsBridge {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _lastConnectedTimestampMs = MutableStateFlow(0L)
    val lastConnectedTimestampMs: StateFlow<Long> = _lastConnectedTimestampMs.asStateFlow()

    private val _recentToast = MutableStateFlow<InterventionToastState?>(null)
    val recentToast: StateFlow<InterventionToastState?> = _recentToast.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
        if (connected) {
            _lastConnectedTimestampMs.value = System.currentTimeMillis()
        }
    }

    fun publishToast(toast: InterventionToastState?) {
        _recentToast.value = toast
    }

    fun dismissToast() {
        _recentToast.value = null
    }

    fun dismissToastIfEvent(eventId: Long) {
        if (_recentToast.value?.eventId == eventId) {
            _recentToast.value = null
        }
    }
}
