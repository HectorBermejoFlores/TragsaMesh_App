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

/**
 * Clase encargada de gestionar el envío de posiciones recibidas por la aplicación a un servidor MQTT.
 *
 * @param scope Ámbito de corrutinas utilizado para ejecutar de forma asíncrona las operaciones MQTT.
 * @param nodeManager Gestor de los nodos Meshtastic.
 * @param mqttPrefs Proporciona la configuración MQTT almacenada por el usuario.
 */
@Single
class MqttPositionForwarder(
    private val scope: ServiceScope,
    private val nodeManager: NodeManager,
    private val mqttPrefs: MqttPrefs,
) {
    private var client: MqttClient? = null
    private var clientNodeNum: Int? = null
    private var clientSettings: MqttSettings? = null
    private val connectionMutex = Mutex()

    fun forward(
        from: Int,
        id: Int,
        position: Position,
        receivedTime: Long,
    ) {
        if (!isPositionValid(receivedTime)) {
            val age = nowMillis - receivedTime
            Logger.i {
                "MQTT position discarded by age: " +
                        "from=${from.toUInt()} " +
                        "id=${id.toUInt()} " +
                        "receivedTime=$receivedTime " +
                        "ageMs=$age"
            }
            return
        }

        Logger.i {
            "MQTT position received: " +
                    "from=${from.toUInt()} " +
                    "id=${id.toUInt()} " +
                    "receivedTime=$receivedTime " +
                    "position=${Position.ADAPTER.toOneLiner(position)}"
        }

        val senderNodeNum = nodeManager.myNodeNum.value
        if (senderNodeNum == null) {
            Logger.w {
                "MQTT position ignored: local node is not available"
            }
            return
        }

        val json = createJson(
            from = from,
            id = id,
            senderNodeNum = senderNodeNum,
            position = position,
            receivedTime = receivedTime,
        )

        scope.launch {
            val settings = mqttPrefs.settings.value

            if (settings.host.isBlank()) {
                Logger.d { "MQTT position forward skipped: host is empty" }
                return@launch
            }

            Logger.d {
                "MQTT_POSITION_FORWARDER " +
                        "topic=${settings.topic} " +
                        "payload=$json"
            }

            try {
                validateSettings(settings)
                val mqttClient = ensureConnected(settings)

                sendPendingPositions(
                    mqttClient = mqttClient,
                    topic = settings.topic,
                )

                Logger.i {
                    "MQTT_PUBLISH: " +
                            "topic=${settings.topic} " +
                            "from=${from.toUInt()} " +
                            "id=${id.toUInt()} "
                }

                mqttClient.publish(
                    topic = settings.topic,
                    payload = json,
                    qos = QoS.AT_LEAST_ONCE,
                )

                Logger.i {
                    "MQTT position published: " +
                            "id=${id.toUInt()} " +
                            "from=${from.toUInt()}"
                }
            } catch (exception: Exception) {
                Logger.e {
                    "MQTT position publish failed: " +
                            "type=${exception::class.simpleName} " +
                            "message=${exception.message} " +
                            "id=${id.toUInt()} " +
                            "from=${from.toUInt()}"
                }

                savePendingPosition(
                    from = from,
                    id = id,
                    json = json,
                    receivedTime = receivedTime,
                )
            }
        }
    }

    private fun createClient(
        nodeNum: Int,
        settings: MqttSettings,
    ): MqttClient {
        val mqttClientId = "meshtastic-android-gateway-${nodeNum.toUInt()}"

        Logger.i {
            "Creating MQTT client: $mqttClientId " +
                    "host=${settings.host} " +
                    "port=${settings.port} " +
                    "tls=${settings.tlsEnabled} " +
                    "username=${settings.username}"
        }

        val tls = mqttTlsConfig()

        return MqttClient(mqttClientId) {
            keepAliveSeconds = 30
            autoReconnect = true
            negotiateVersion = false
            transportFactory = TcpTransportFactory(tls)
            username = settings.username
            password(settings.password)
            defaultQos = QoS.AT_LEAST_ONCE
            defaultRetain = false
            logger = MqttLogger.println()
            logLevel = MqttLogLevel.DEBUG
        }
    }

    private fun isPositionValid(
        receivedTime: Long,
    ): Boolean {
        val age = nowMillis - receivedTime
        return age in -FUTURE_TOLERANCE_MS..MAX_POSITION_AGE_MS
    }

    private suspend fun ensureConnected(
        settings: MqttSettings,
    ): MqttClient {
        val myNodeNum = nodeManager.myNodeNum.value
            ?: error("Local Meshtastic node is not available")

        return connectionMutex.withLock {
            val nodeChanged = clientNodeNum != myNodeNum
            val settingsChanged = clientSettings != settings

            if (client == null || nodeChanged || settingsChanged) {
                Logger.i {
                    "Recreating MQTT client: " +
                            "nodeChanged=$nodeChanged " +
                            "settingsChanged=$settingsChanged"
                }

                client?.close()
                client = createClient(
                    nodeNum = myNodeNum,
                    settings = settings,
                )
                clientNodeNum = myNodeNum
                clientSettings = settings
            }

            val mqttClient = checkNotNull(client)

            if (mqttClient.connectionState.value !is ConnectionState.Connected) {
                Logger.i {
                    "Connecting MQTT: " +
                            "host=${settings.host} " +
                            "port=${settings.port} " +
                            "tls=${settings.tlsEnabled}"
                }

                mqttClient.connect(
                    MqttEndpoint.Tcp(
                        host = settings.host,
                        port = settings.port,
                        tls = settings.tlsEnabled,
                    ),
                )
            }

            mqttClient
        }
    }

    suspend fun testConnection(
        settings: MqttSettings,
    ): Result<Unit> {
        return try {
            validateSettings(settings)

            val nodeNum = nodeManager.myNodeNum.value
                ?: error("No hay ningún nodo Meshtastic conectado")

            val testClient = createClient(
                nodeNum = nodeNum,
                settings = settings,
            )

            try {
                Logger.i {
                    "MQTT test connecting: " +
                            "host=${settings.host} " +
                            "port=${settings.port} " +
                            "tls=${settings.tlsEnabled}"
                }

                testClient.connect(
                    MqttEndpoint.Tcp(
                        host = settings.host,
                        port = settings.port,
                        tls = settings.tlsEnabled,
                    ),
                )

                Logger.i {
                    "MQTT test connected successfully: " +
                            "state=${testClient.connectionState.value}"
                }

                Result.success(Unit)
            } finally {
                testClient.close()
                Logger.i { "MQTT test client closed" }
            }
        } catch (exception: Exception) {
            Logger.e(exception) {
                "MQTT connection test failed: " +
                        "type=${exception::class.simpleName} " +
                        "message=${exception.message} " +
                        "host=${settings.host} " +
                        "port=${settings.port} " +
                        "tls=${settings.tlsEnabled} " +
                        "username=${settings.username}"
            }
            Result.failure(exception)
        }
    }

    private fun createJson(
        from: Int,
        id: Int,
        senderNodeNum: Int,
        position: Position,
        receivedTime: Long,
    ): String {
        val message = buildJsonObject {
            put("from", from.toUInt().toLong())
            put("id", id.toUInt().toLong())
            put("sender", "!${senderNodeNum.toUInt().toString(16)}")
            put("type", "position")
            put(
                "payload",
                buildJsonObject {
                    put("latitude_i", position.latitude_i)
                    put("longitude_i", position.longitude_i)
                    put("time", receivedTime / 1000)
                },
            )
        }
        return message.toString()
    }

    data class PendingPacket(
        val json: String,
        val receivedTime: Long,
    )

    private val pendingPositions = mutableMapOf<Pair<Int, Int>, PendingPacket>()

    private fun savePendingPosition(
        from: Int,
        id: Int,
        json: String,
        receivedTime: Long,
    ) {
        pendingPositions[from to id] = PendingPacket(
            json = json,
            receivedTime = receivedTime,
        )

        Logger.i {
            "MQTT position saved as pending: " +
                    "from=${from.toUInt()} " +
                    "id=${id.toUInt()}"
        }
    }

    private suspend fun sendPendingPositions(
        mqttClient: MqttClient,
        topic: String,
    ) {
        if (pendingPositions.isEmpty()) {
            return
        }

        val pendingCopy = pendingPositions.toMap()

        for ((key, packet) in pendingCopy) {
            val from = key.first
            val id = key.second

            if (!isPositionValid(packet.receivedTime)) {
                pendingPositions.remove(key)
                Logger.i {
                    "Pending MQTT position expired: " +
                            "from=${from.toUInt()} " +
                            "id=${id.toUInt()}"
                }
                continue
            }

            try {
                mqttClient.publish(
                    topic = topic,
                    payload = packet.json,
                    qos = QoS.AT_LEAST_ONCE,
                )

                pendingPositions.remove(key)
                Logger.i {
                    "Pending MQTT position published: " +
                            "from=${from.toUInt()} " +
                            "id=${id.toUInt()}"
                }
            } catch (exception: Exception) {
                Logger.e(exception) {
                    "Failed to publish pending MQTT position: " +
                            "from=${from.toUInt()} " +
                            "id=${id.toUInt()}"
                }
                break
            }
        }
    }

    private fun validateSettings(
        settings: MqttSettings,
    ) {
        require(settings.host.isNotBlank()) { "MQTT host is empty" }
        require(settings.port in 1..65535) { "MQTT port is invalid: ${settings.port}" }
        require(settings.topic.isNotBlank()) { "MQTT topic is empty" }
        require(settings.username.isNotBlank()) { "MQTT username is empty" }
        require(settings.password.isNotBlank()) { "MQTT password is empty" }
    }

    companion object {
        const val MAX_POSITION_AGE_MS = 120 * 60 * 1000L
        const val FUTURE_TOLERANCE_MS = 1000L
    }
}
