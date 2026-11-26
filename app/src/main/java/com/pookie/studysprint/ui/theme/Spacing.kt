package com.pookie.studysprint.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4dp grid. Har screen mein spacing sirf yahin se aayegi. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen ke left/right padding */
    val screenHorizontal = 20.dp
}

object Radius {
    val card = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(10.dp)
    val chip = RoundedCornerShape(8.dp)
}