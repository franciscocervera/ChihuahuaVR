package com.mechrobotix.chihuahua.audio

sealed interface NarrationEvent {
    val fileName: String

    data class Completed(override val fileName: String) : NarrationEvent
    data class FileMissing(override val fileName: String) : NarrationEvent
    data class PlaybackError(override val fileName: String) : NarrationEvent
}
