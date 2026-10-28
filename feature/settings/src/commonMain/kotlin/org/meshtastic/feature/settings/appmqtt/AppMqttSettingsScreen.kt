package org.meshtastic.feature.settings.appmqtt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.model.util.MqttSettings

@Composable
fun AppMqttSettingsScreen(
    viewModel: AppMqttSettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by
    viewModel.settings.collectAsStateWithLifecycle()

    val mqttEnabled by
    viewModel.mqttEnabled.collectAsStateWithLifecycle()

    val testState by
    viewModel.testState.collectAsStateWithLifecycle()

    var host by remember {
        mutableStateOf("")
    }

    var port by remember {
        mutableStateOf("")
    }

    var topic by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var tlsEnabled by remember {
        mutableStateOf(true)
    }

    val parsedPort =
        port.toIntOrNull()

    val currentSettings =
        parsedPort?.let {
            MqttSettings(
                host = host.trim(),
                port = it,
                topic = topic.trim(),
                username = username.trim(),
                password = password,
                tlsEnabled = tlsEnabled,
            )
        }

    LaunchedEffect(settings) {
        host = settings.host
        port = settings.port.toString()
        topic = settings.topic
        username = settings.username
        password = settings.password
        tlsEnabled = settings.tlsEnabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MQTT de la aplicación"
                    )
                },
            )
        },
    ) { paddingValues ->

        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    "Activar MQTT desde el móvil"
                )

                Switch(
                    checked = mqttEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setMqttEnabled(
                            enabled
                        )
                    },
                )
            }

            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                },
                label = {
                    Text("Host")
                },
                singleLine = true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = port,
                onValueChange = { value ->
                    port =
                        value.filter {
                            it.isDigit()
                        }
                },
                label = {
                    Text("Puerto")
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number,
                    ),
                modifier =
                    Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = topic,
                onValueChange = {
                    topic = it
                },
                label = {
                    Text("Topic")
                },
                singleLine = true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                },
                label = {
                    Text("Usuario")
                },
                singleLine = true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                },
                label = {
                    Text("Contraseña")
                },
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    "Usar TLS"
                )

                Switch(
                    checked = tlsEnabled,
                    onCheckedChange = {
                        tlsEnabled = it
                    },
                )
            }

            Button(
                onClick = {
                    currentSettings?.let {
                        viewModel.testConnection(it)
                    }
                },
                enabled =
                    currentSettings != null &&
                            testState !is MqttTestState.Testing,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (
                        testState
                                is MqttTestState.Testing
                    ) {
                        "Comprobando..."
                    } else {
                        "Probar conexión"
                    }
                )
            }

            when (
                val state = testState
            ) {
                MqttTestState.Idle ->
                    Unit

                MqttTestState.Testing ->
                    Text(
                        "Conectando con el broker MQTT..."
                    )

                MqttTestState.Success ->
                    Text(
                        "Conexión MQTT correcta"
                    )

                is MqttTestState.Error ->
                    Text(
                        "No se pudo conectar: " +
                                state.message
                    )
            }

            Button(
                onClick = {
                    val validPort =
                        port.toIntOrNull()

                    if (
                        validPort != null &&
                        validPort in 1..65535
                    ) {
                        viewModel.saveSettings(
                            MqttSettings(
                                host =
                                    host.trim(),
                                port =
                                    validPort,
                                topic =
                                    topic.trim(),
                                username =
                                    username.trim(),
                                password =
                                    password,
                                tlsEnabled =
                                    tlsEnabled,
                            ),
                        )
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
        }
    }
}