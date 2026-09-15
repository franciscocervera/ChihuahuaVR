package com.mechrobotix.chihuahua.data

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

data class Destination(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val mapLatitude: Float,
    val mapLongitude: Float,
    @DrawableRes val panoramaRes: Int,
    @DrawableRes val thumbnailRes: Int,
    @RawRes val ambientAudioRes: Int,
    val narrationFileName: String,
    val accentArgb: Long,
    val hapticEffectId: String,
    val hotspots: List<Hotspot>,
)

data class Hotspot(
    val id: String,
    val title: String,
    val body: String,
    val narrationFileName: String,
    val yaw: Float,
    val pitch: Float,
    val hapticEffectId: String,
)
