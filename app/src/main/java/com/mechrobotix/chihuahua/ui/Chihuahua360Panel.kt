package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mechrobotix.chihuahua.ble.BleVestDevice
import com.mechrobotix.chihuahua.ble.VestBleManager
import com.mechrobotix.chihuahua.ble.VestCommand
import com.mechrobotix.chihuahua.ble.VestConnectionPhase
import com.mechrobotix.chihuahua.ble.VestDiagnosticEntry
import com.mechrobotix.chihuahua.ble.VestDiagnosticLevel
import com.mechrobotix.chihuahua.data.Destination
import com.mechrobotix.chihuahua.data.ThermalChannel
import com.mechrobotix.chihuahua.data.VestHardware
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.StateFlow

private enum class MainSection(val label: String) {
    DESTINATIONS("Destinos"),
    VEST("Chaleco"),
    DIAGNOSTICS("Diagnóstico"),
}

@Composable
fun Chihuahua360Panel(
    destinations: List<Destination>,
    selectedDestination: StateFlow<Destination?>,
    vestBleManager: VestBleManager,
    onDestinationSelected: (Destination) -> Unit,
    onStartDemo: () -> Unit,
    onManualCommand: (VestCommand) -> Boolean,
    onManualAllOff: () -> Boolean,
    onRequestBluetoothPermissions: () -> Unit,
    onReturnToLobby: () -> Unit,
    onCloseMenu: () -> Unit,
    onRecenterPanel: () -> Unit,
    onExit: () -> Unit,
) {
    val selected by selectedDestination.collectAsState()
    var section by rememberSaveable(selected?.id) { mutableStateOf(MainSection.DESTINATIONS) }

    Chihuahua360Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFBF5), Color(0xFFF2E7D8)),
                    ),
                )
                .border(2.dp, Color(0xFF8A4C00))
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Header(
                    destination = selected,
                    onReturnToLobby = onReturnToLobby,
                    onCloseMenu = onCloseMenu,
                    onRecenterPanel = onRecenterPanel,
                    onExit = onExit,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(section, onSelect = { section = it })
                    Spacer(Modifier.width(18.dp))
                    when (section) {
                        MainSection.DESTINATIONS -> DestinationSection(
                            destinations = destinations,
                            selected = selected,
                            onSelect = onDestinationSelected,
                            onStartDemo = onStartDemo,
                        )
                        MainSection.VEST -> VestSection(
                            manager = vestBleManager,
                            onManualCommand = onManualCommand,
                            onManualAllOff = onManualAllOff,
                            onRequestPermissions = onRequestBluetoothPermissions,
                        )
                        MainSection.DIAGNOSTICS -> DiagnosticsSection(vestBleManager)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    destination: Destination?,
    onReturnToLobby: () -> Unit,
    onCloseMenu: () -> Unit,
    onRecenterPanel: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "ChihuahuaVR",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (destination == null) {
                    "Inicia el recorrido guiado o elige un destino"
                } else {
                    "Menú del recorrido inmersivo"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                text = destination?.title ?: "Lobby",
                accent = destination?.let { Color(it.accentArgb) } ?: MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = onRecenterPanel) {
                Text("Recentrar", fontSize = 18.sp)
            }
            if (destination != null) {
                OutlinedButton(onClick = onReturnToLobby) {
                    Text("Volver al Lobby", fontSize = 18.sp)
                }
                OutlinedButton(onClick = onCloseMenu) {
                    Text("Volver al Recorrido", fontSize = 18.sp)
                }
            }
            if (destination == null) {
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Salir", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(
    section: MainSection,
    onSelect: (MainSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(202.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MainSection.entries.forEach { item ->
            val selected = item == section
            if (selected) {
                Button(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(item.label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(item.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Gatillo: seleccionar · Grip: mover el panel.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun DestinationSection(
    destinations: List<Destination>,
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    onStartDemo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = if (selected == null) "Explora Chihuahua" else "Cambiar de destino",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = if (selected == null) {
                "Inicia el recorrido guiado o explora un destino por tu cuenta."
            } else {
                "Selecciona otro destino o consulta los puntos de interés del recorrido actual."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (selected == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "guided-tour") {
                    GuidedTourCard(onStartDemo = onStartDemo)
                }
                item(key = "explore-label") {
                    Text(
                        text = "Explorar por destino",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = destinations.chunked(3),
                    key = { row -> row.joinToString(separator = "|") { it.id } },
                ) { rowDestinations ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowDestinations.forEach { destination ->
                            DestinationCard(
                                destination = destination,
                                selected = false,
                                onClick = { onSelect(destination) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(208.dp),
                            )
                        }
                        repeat(3 - rowDestinations.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(destinations, key = { it.id }) { destination ->
                    DestinationCard(
                        destination = destination,
                        selected = destination.id == selected.id,
                        onClick = { onSelect(destination) },
                        modifier = Modifier
                            .width(258.dp)
                            .height(174.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            DestinationDetails(selected)
        }
    }
}

@Composable
private fun GuidedTourCard(onStartDemo: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF352312)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RECORRIDO GUIADO",
                    color = Color(0xFFFFC66B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Descubre Chihuahua en una secuencia inmersiva",
                    color = Color.White,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "10 destinos · Narración principal · Aproximadamente 3:30 min",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(22.dp))
            Button(onClick = onStartDemo) {
                Text("Iniciar Demo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(destination.accentArgb)
    Card(
        onClick = onClick,
        modifier = modifier.then(
            if (selected) Modifier.border(4.dp, accent, RoundedCornerShape(20.dp))
            else Modifier,
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(destination.thumbnailRes),
                contentDescription = destination.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF2000000)),
                        ),
                    ),
            )
            Text(
                text = if (selected) "ACTIVO" else "ABRIR",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color(0xFF101820),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    destination.category.uppercase(),
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    destination.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DestinationDetails(
    destination: Destination,
) {
    val accent = Color(destination.accentArgb)
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
            ) {
                Text(destination.category.uppercase(), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = destination.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Panorama, audio y perfil háptico activos",
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = "Puntos de interés",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(destination.hotspots, key = { it.id }) { hotspot ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(hotspot.title, fontWeight = FontWeight.Bold, color = accent)
                                    Text("Se descubre con la mirada", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    hotspot.summary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VestSection(
    manager: VestBleManager,
    onManualCommand: (VestCommand) -> Boolean,
    onManualAllOff: () -> Boolean,
    onRequestPermissions: () -> Unit,
) {
    val state by manager.state.collectAsState()
    val devices by manager.devices.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionCard(
            stateMessage = state.message,
            lastStatus = state.lastStatus,
            connected = state.isConnected,
            phase = state.phase,
            hasPermission = state.hasPermission,
            devices = devices,
            onRequestPermissions = onRequestPermissions,
            onScan = manager::startScan,
            onStopScan = manager::stopScan,
            onConnect = manager::connect,
            onDisconnect = manager::disconnect,
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VibrationControls(
                modifier = Modifier.weight(1f),
                enabled = state.isConnected,
                onSend = onManualCommand,
            )
            ThermalControls(
                modifier = Modifier.weight(1f),
                enabled = state.isConnected,
                onSend = onManualCommand,
                onAllOff = onManualAllOff,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    stateMessage: String,
    lastStatus: String?,
    connected: Boolean,
    phase: VestConnectionPhase,
    hasPermission: Boolean,
    devices: List<BleVestDevice>,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleVestDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Conexión Bluetooth", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = lastStatus?.let { "$stateMessage · $it" } ?: stateMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        !hasPermission -> Button(onClick = onRequestPermissions) {
                            Text("Autorizar Bluetooth")
                        }
                        connected -> OutlinedButton(onClick = onDisconnect) {
                            Text("Desconectar")
                        }
                        phase == VestConnectionPhase.SCANNING -> OutlinedButton(onClick = onStopScan) {
                            Text("Detener búsqueda")
                        }
                        else -> Button(onClick = onScan) {
                            Text("Buscar chaleco")
                        }
                    }
                }
            }

            if (!connected && devices.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices, key = { it.address }) { device ->
                        OutlinedButton(onClick = { onConnect(device) }) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(device.name, fontWeight = FontWeight.Bold)
                                Text("Señal ${device.rssi} dBm", fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibrationControls(
    modifier: Modifier,
    enabled: Boolean,
    onSend: (VestCommand) -> Boolean,
) {
    var channel by rememberSaveable { mutableIntStateOf(1) }
    var duration by rememberSaveable { mutableFloatStateOf(600f) }

    ControlCard(modifier, "Vibración", "Control de motores hápticos") {
        Text("Canal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectorRow(
            options = VestHardware.vibrationChannels.map { it.label },
            selectedIndex = channel - 1,
            onSelect = { channel = it + 1 },
        )
        Spacer(Modifier.height(14.dp))
        ValueLabel("Duración", "${duration.toInt()} ms")
        Slider(
            value = duration,
            onValueChange = { duration = it },
            valueRange = 100f..3_000f,
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = enabled,
                onClick = { onSend(VestCommand.Vibration(channel, true, duration.toInt())) },
            ) {
                Text("Activar")
            }
            OutlinedButton(
                enabled = enabled,
                onClick = { onSend(VestCommand.Vibration(channel, false)) },
            ) {
                Text("Detener")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = enabled,
            onClick = { onSend(VestCommand.VibrationAll(true, duration.toInt())) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Activar todos los motores")
        }
    }
}

@Composable
private fun ThermalControls(
    modifier: Modifier,
    enabled: Boolean,
    onSend: (VestCommand) -> Boolean,
    onAllOff: () -> Boolean,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var duty by rememberSaveable { mutableFloatStateOf(45f) }
    var duration by rememberSaveable { mutableFloatStateOf(VestHardware.THERMAL_HEAT_DURATION_MS.toFloat()) }
    val channel = VestHardware.thermalChannels[selectedIndex]

    ControlCard(modifier, "Control térmico", "Celdas de calor y frío") {
        Text("Zona", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThermalSelector(
            channels = VestHardware.thermalChannels,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                selectedIndex = index
                duration = VestHardware.thermalDuration(VestHardware.thermalChannels[index].mode).toFloat()
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = channel.label,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        ValueLabel("Ciclo de trabajo", "${duty.toInt()} %")
        Slider(
            value = duty,
            onValueChange = { duty = it },
            valueRange = 10f..70f,
            enabled = enabled,
        )
        ValueLabel("Duración", "${duration.toInt()} ms")
        Slider(
            value = duration,
            onValueChange = { duration = it },
            valueRange = 500f..10_000f,
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = enabled,
                onClick = {
                    onSend(
                        VestCommand.Thermal(
                            channel = channel.id,
                            mode = channel.mode,
                            duty = duty.toInt(),
                            durationMs = duration.toInt(),
                        ),
                    )
                },
            ) {
                Text("Activar")
            }
            OutlinedButton(
                enabled = enabled,
                onClick = { onSend(VestCommand.ThermalOff(channel.id)) },
            ) {
                Text("Detener zona")
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = enabled,
            onClick = { onAllOff() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apagar todo")
        }
    }
}


@Composable
private fun DiagnosticsSection(manager: VestBleManager) {
    val diagnostics by manager.diagnostics.collectAsState()
    val state by manager.state.collectAsState()

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Consola de diagnóstico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.isConnected) "Chaleco conectado" else "Chaleco desconectado",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                OutlinedButton(onClick = manager::clearDiagnostics) {
                    Text("Limpiar")
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFB9A891))
            Spacer(Modifier.height(12.dp))

            if (diagnostics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay eventos registrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(diagnostics.asReversed(), key = { it.id }) { entry ->
                        DiagnosticRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(entry: VestDiagnosticEntry) {
    val accent = when (entry.level) {
        VestDiagnosticLevel.INFO -> MaterialTheme.colorScheme.secondary
        VestDiagnosticLevel.HAPTIC -> MaterialTheme.colorScheme.primary
        VestDiagnosticLevel.WARNING -> Color(0xFFF4C95D)
        VestDiagnosticLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = formatDiagnosticTime(entry.timestampMs),
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = entry.message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}

private val diagnosticTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatDiagnosticTime(timestampMs: Long): String =
    Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(diagnosticTimeFormatter)

@Composable
private fun ControlCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFB9A891))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SelectorRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            if (selected) {
                Button(onClick = { onSelect(index) }, contentPadding = ButtonDefaults.ContentPadding) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ThermalSelector(
    channels: List<ThermalChannel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val labels = channels.map { channel ->
        channel.label
    }
    SelectorRow(labels, selectedIndex, onSelect)
}

@Composable
private fun ValueLabel(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFFFE2BF))
            .border(1.dp, accent, CircleShape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
