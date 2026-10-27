package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.util.MqttSettings
import org.meshtastic.core.model.util.toOneLiner
import org.meshtastic.core.network.repository.mqttTlsConfig
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.usecase.MqttPrefs
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.MqttLogger
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.proto.Position

class MqttPositionFordwarder {
}