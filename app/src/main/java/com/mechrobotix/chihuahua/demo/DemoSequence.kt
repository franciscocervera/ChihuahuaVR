package com.mechrobotix.chihuahua.demo

data class DemoStep(
    val destinationId: String,
    val holdAfterNarrationMs: Long = 400L,
)

object DemoSequence {
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
        DemoStep("sinforosa", holdAfterNarrationMs = 850L),
    )
}
