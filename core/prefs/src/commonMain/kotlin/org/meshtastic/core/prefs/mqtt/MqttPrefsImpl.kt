package org.meshtastic.core.prefs.mqtt

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.MqttSettings
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.repository.usecase.MqttPrefs

@Single
class MqttPrefsImpl(
    private val dataStore: UiDataStore,
    dispatchers: CoroutineDispatchers,
) : MqttPrefs {

    private val scope =
        CoroutineScope(
            SupervisorJob() + dispatchers.default,
        )

        override val settings: StateFlow<MqttSettings> =
            dataStore.data.map { preferences ->
                MqttSettings(
                    host = preferences[KEY_HOST] ?: DEFAULT_HOST,
                    port = preferences[KEY_PORT] ?: DEFAULT_PORT,
                    topic = preferences[KEY_TOPIC] ?: DEFAULT_TOPIC,
                    username = preferences[KEY_USERNAME] ?: DEFAULT_USERNAME,
                    password = preferences[KEY_PASSWORD] ?: DEFAULT_PASSWORD,
                    tlsEnabled = preferences[KEY_TLS_ENABLED] ?: DEFAULT_TLS_ENABLED,
                )
            }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    initialValue =
                        MqttSettings(
                            host = DEFAULT_HOST,
                            port = DEFAULT_PORT,
                            topic = DEFAULT_TOPIC,
                            username = DEFAULT_USERNAME,
                            password = DEFAULT_PASSWORD,
                            tlsEnabled = DEFAULT_TLS_ENABLED,
                        ),
                )
        override val enabled: StateFlow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[KEY_ENABLED] ?: DEFAULT_ENABLED
            }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    initialValue = DEFAULT_ENABLED,
                )

    override fun saveSettings(settings: MqttSettings) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_HOST] = settings.host.trim()
                preferences[KEY_PORT] = settings.port
                preferences[KEY_TOPIC] = settings.topic.trim()
                preferences[KEY_USERNAME] = settings.username.trim()
                preferences[KEY_PASSWORD] = settings.password
                preferences[KEY_TLS_ENABLED] = settings.tlsEnabled
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_ENABLED] = enabled
            }
        }
    }

    private companion object {
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 1883
        const val DEFAULT_TOPIC = "msh/EU_868/ES/Tragsa/Posiciones"
        const val DEFAULT_USERNAME = ""
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_TLS_ENABLED = false
        const val DEFAULT_ENABLED = false

        val KEY_HOST =
            stringPreferencesKey(
                "mqtt_gateway_host",
            )
        val KEY_PORT =
            intPreferencesKey(
                "mqtt_gateway_port",
            )
        val KEY_TOPIC =
            stringPreferencesKey(
                "mqtt_gateway_topic",
            )
        val KEY_USERNAME =
            stringPreferencesKey(
                "mqtt_gateway_username",
            )
        val KEY_PASSWORD =
            stringPreferencesKey(
                "mqtt_gateway_password",
            )
        val KEY_TLS_ENABLED =
            booleanPreferencesKey(
                "mqtt_gateway_tls_enabled",
            )
        val KEY_ENABLED =
            booleanPreferencesKey(
                "mqtt_gateway_enabled",
            )
    }

}