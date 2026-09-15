package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mechrobotix.chihuahua.demo.DemoPhase
import com.mechrobotix.chihuahua.demo.DemoState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DemoControlPanel(
    demoState: StateFlow<DemoState>,
    onExitDemo: () -> Unit,
) {
    val state by demoState.collectAsState()
    if (!state.active) return

    val currentStep = (state.stepIndex + 1).coerceAtMost(state.totalSteps.coerceAtLeast(1))
    val progress = if (state.totalSteps > 0) {
        currentStep.toFloat() / state.totalSteps.toFloat()
    } else {
        0f
    }

    Chihuahua360Theme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF21B1712),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recorrido guiado · $currentStep/${state.totalSteps}",
                            color = Color(0xFFFFD89A),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when (state.phase) {
                                DemoPhase.TRAVELING -> "Viajando a ${state.destinationTitle}"
                                DemoPhase.TRANSITIONING -> "Llegando a ${state.destinationTitle}"
                                DemoPhase.FINISHING -> "Finalizando recorrido"
                                else -> state.destinationTitle
                            },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                    Button(onClick = onExitDemo) {
                        Text("Salir", fontSize = 16.sp)
                    }
                }
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.16f),
                )
            }
        }
    }
}
