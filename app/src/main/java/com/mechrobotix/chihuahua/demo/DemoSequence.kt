package com.mechrobotix.chihuahua.demo

data class DemoStep(
    val destinationId: String,
    val holdAfterNarrationMs: Long = DemoSequence.DEFAULT_READING_HOLD_MS,
)

object DemoSequence {
    const val DEFAULT_READING_HOLD_MS = 1_000L
    const val FINAL_READING_HOLD_MS = 1_000L

    val hapticMode: DemoHapticMode = DemoHapticMode.VIBRATION_ONLY

    val complete: List<DemoStep> = listOf(
        DemoStep("barrancas-cobre"),
        DemoStep("chepe"),
        DemoStep("centro-chihuahua"),
        DemoStep("paquime"),
        DemoStep("samalayuca"),
        DemoStep("creel-arareko"),
        DemoStep("basaseachi"),
        DemoStep("parral"),
        DemoStep("batopilas"),
        DemoStep("sinforosa", holdAfterNarrationMs = FINAL_READING_HOLD_MS),
    )
}
