package org.meshtastic.core.model.util

/*
 * Configuración necesaria para establecer la conexión con el servidor MQTT
 */
data class MqttSettings (
    val host: String,
    val port: Int,
    val topic: String,
    val username: String,
    val password: String,
    val tlsEnabled: Boolean,

)