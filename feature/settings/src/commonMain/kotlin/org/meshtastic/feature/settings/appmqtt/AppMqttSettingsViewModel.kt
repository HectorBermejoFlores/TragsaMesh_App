package org.meshtastic.feature.settings.appmqtt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.util.MqttSettings
import org.meshtastic.core.repository.usecase.MqttPrefs
import org.meshtastic.core.data.manager.MqttPositionForwarder

@KoinViewModel
class AppMqttSettingsViewModel(
    private val mqttPrefs: MqttPrefs,
    private val mqttPositionForwarder: MqttPositionForwarder,
) : ViewModel() {

    val settings: StateFlow<MqttSettings> = mqttPrefs.settings

    val mqttEnabled: StateFlow<Boolean> = mqttPrefs.enabled

    private val _testState =
        MutableStateFlow<MqttTestState>(MqttTestState.Idle)
    val testState: StateFlow<MqttTestState> = _testState.asStateFlow()

    fun setMqttEnabled(
        enabled: Boolean,
    ) {
        mqttPrefs.setEnabled(enabled)
    }

    fun saveSettings(
        settings: MqttSettings,
    ) {
        mqttPrefs.saveSettings(settings)
    }

    fun testConnection(
        settings: MqttSettings,
    ) {
        viewModelScope.launch {
            _testState.value = MqttTestState.Testing

            val result =
                mqttPositionForwarder.testConnection(settings)

            _testState.value =
                result.fold(
                    onSuccess = { MqttTestState.Success },
                    onFailure = { exception ->
                        MqttTestState.Error(
                            exception.message ?: exception::class.simpleName ?: "Error desconocido"
                        )
                    },
                )
        }
    }
}

sealed interface MqttTestState {
    data object Idle : MqttTestState
    data object Testing : MqttTestState
    data object Success : MqttTestState
    data class Error(
        val message: String,
    ) : MqttTestState
}
