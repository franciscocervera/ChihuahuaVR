package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mechrobotix.chihuahua.audio.NarrationProgress
import com.mechrobotix.chihuahua.audio.NarrationStatus
import com.mechrobotix.chihuahua.ble.VestBleManager
import com.mechrobotix.chihuahua.data.Destination
import com.mechrobotix.chihuahua.data.Hotspot
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SceneToolbarPanel(
    selectedDestination: StateFlow<Destination?>,
    narrationEnabled: StateFlow<Boolean>,
    narrationStatus: StateFlow<NarrationStatus>,
    narrationProgress: StateFlow<NarrationProgress>,
    hotspotsEnabled: StateFlow<Boolean>,
    vestBleManager: VestBleManager,
    onOpenMenu: () -> Unit,
    onReturnToLobby: () -> Unit,
    onHotspotSelected: (Destination, Hotspot) -> Unit,
    onToggleNarration: () -> Unit,
) {
    val destination by selectedDestination.collectAsState()
    val automaticNarration by narrationEnabled.collectAsState()
    val voiceStatus by narrationStatus.collectAsState()
    val voiceProgress by narrationProgress.collectAsState()
    val hotspotAccess by hotspotsEnabled.collectAsState()
    val vestState by vestBleManager.state.collectAsState()
    val activeDestination = destination ?: return
    val accent = Color(activeDestination.accentArgb)

    Chihuahua360Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFCF7), Color(0xFFF2E6D5)),
                    ),
                )
                .border(2.dp, Color(0xFF7A4300))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeDestination.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(
                                        color = if (vestState.isConnected) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            Color(0xFF6D6257)
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                            Text(
                                text = if (vestState.isConnected) {
                                    "Chaleco conectado"
                                } else {
                                    "Chaleco desconectado"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleNarration,
                        enabled = voiceStatus != NarrationStatus.PREPARING,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = narrationLabel(voiceStatus, automaticNarration),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = onReturnToLobby,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Volver al Lobby", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onOpenMenu,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Ver destinos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                NarrationPlaybackIndicator(
                    status = voiceStatus,
                    progress = voiceProgress,
                    accent = accent,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    activeDestination.hotspots.forEachIndexed { index, hotspot ->
                        Button(
                            onClick = { onHotspotSelected(activeDestination, hotspot) },
                            enabled = hotspotAccess,
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, accent.copy(alpha = 0.62f)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp,
                                vertical = 10.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hotspot.title,
                                        fontSize = 18.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (hotspotAccess) {
                                            "Abrir punto de interés"
                                        } else {
                                            "Disponible al terminar la narración"
                                        },
                                        color = if (hotspotAccess) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun narrationLabel(
    status: NarrationStatus,
    automaticNarration: Boolean,
): String = when (status) {
    NarrationStatus.PREPARING -> "Cargando audio…"
    NarrationStatus.PLAYING -> "Narrando…"
    NarrationStatus.FILE_MISSING -> "Audio pendiente"
    NarrationStatus.PLAYBACK_ERROR -> "Error de audio"
    NarrationStatus.IDLE,
    NarrationStatus.READY -> if (automaticNarration) {
        "Narración activa"
    } else {
        "Narración pausada"
    }
}
