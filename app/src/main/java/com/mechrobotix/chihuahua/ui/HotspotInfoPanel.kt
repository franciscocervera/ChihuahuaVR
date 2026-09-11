package com.mechrobotix.chihuahua.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mechrobotix.chihuahua.audio.NarrationProgress
import com.mechrobotix.chihuahua.audio.NarrationStatus
import com.mechrobotix.chihuahua.data.HotspotPresentation
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HotspotInfoPanel(
    selectedHotspot: StateFlow<HotspotPresentation?>,
    narrationStatus: StateFlow<NarrationStatus>,
    narrationProgress: StateFlow<NarrationProgress>,
    onClose: () -> Unit,
) {
    val presentation by selectedHotspot.collectAsState()
    val voiceStatus by narrationStatus.collectAsState()
    val voiceProgress by narrationProgress.collectAsState()
    val active = presentation ?: return
    val accent = Color(active.destination.accentArgb)

    Chihuahua360Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFBF4), Color(0xFFF3E6D2)),
                    ),
                )
                .border(2.dp, accent)
                .padding(22.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    ) {
                        Text(
                            text = active.destination.title.uppercase(),
                            color = accent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = active.hotspot.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    OutlinedButton(onClick = onClose) {
                        Text("Cerrar")
                    }
                }
                Spacer(Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = active.hotspot.body,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 21.sp,
                        lineHeight = 30.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))
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
