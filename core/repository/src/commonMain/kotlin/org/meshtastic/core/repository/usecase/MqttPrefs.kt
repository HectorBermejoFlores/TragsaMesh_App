package org.meshtastic.core.repository.usecase

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.util.MqttSettings

/*
 * Gestiona las preferencias relacionadas con la conexión MQTT.
 *
 * Los ajustes del servidor se guardan mediante saveSettings().
 * El estado enabled se guarda inmediatamente al modificar el switch.
 */
interface MqttPrefs {
    /*
     * Configuración MQTT almacenada acutalmente
     */
    val settings: StateFlow<MqttSettings>

    /*
     * Indica si el reevío de posiciones mediante MQTT está habilitado
     */
    val enabled: StateFlow<Boolean>

    /*
     * Guarda la configuración completa del servidor MQTT.
     */
    fun saveSettings(settings: MqttSettings)

    /*
     * Activa o desactiva el reenvío MQTT.
     */
    fun setEnabled(enabled: Boolean)
}