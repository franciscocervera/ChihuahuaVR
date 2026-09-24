package com.mechrobotix.chihuahua.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

fun capturableAudioAttributes(contentType: Int): AudioAttributes =
    AudioAttributes.Builder()
        .setContentType(contentType)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        .build()

fun createCapturableRawPlayer(
    context: Context,
    resourceId: Int,
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
): MediaPlayer? {
    val player = MediaPlayer()
    return runCatching {
        player.setAudioAttributes(capturableAudioAttributes(contentType))
        context.resources.openRawResourceFd(resourceId).use { descriptor ->
            requireNotNull(descriptor) { "No se pudo abrir el recurso de audio $resourceId" }
            player.setDataSource(
                descriptor.fileDescriptor,
                descriptor.startOffset,
                descriptor.length,
            )
        }
        player.prepare()
        player
    }.getOrElse {
        runCatching { player.release() }
        null
    }
}
