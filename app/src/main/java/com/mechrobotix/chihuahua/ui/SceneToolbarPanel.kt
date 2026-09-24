package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SceneToolbarPanel(
    selectedDestination: StateFlow<Destination?>,
    narrationStatus: StateFlow<NarrationStatus>,
    narrationProgress: StateFlow<NarrationProgress>,
    vestBleManager: VestBleManager,
    onOpenMenu: () -> Unit,
    onReturnToLobby: () -> Unit,
    onToggleNarration: () -> Unit,
) {
    val destination by selectedDestination.collectAsState()
    val voiceStatus by narrationStatus.collectAsState()
    val voiceProgress by narrationProgress.collectAsState()
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
                            text = narrationLabel(voiceStatus),
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
            }
        }
    }
}

private fun narrationLabel(status: NarrationStatus): String = when (status) {
    NarrationStatus.PREPARING -> "Preparando narración…"
    NarrationStatus.PLAYING -> "Detener narración"
    NarrationStatus.IDLE,
    NarrationStatus.READY,
    NarrationStatus.FILE_MISSING,
    NarrationStatus.PLAYBACK_ERROR -> "Iniciar narración"
}
