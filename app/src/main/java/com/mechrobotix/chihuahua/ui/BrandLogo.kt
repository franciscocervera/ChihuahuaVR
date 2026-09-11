package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mechrobotix.chihuahua.R

@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.tix_brand_logo),
        contentDescription = "TIX",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
