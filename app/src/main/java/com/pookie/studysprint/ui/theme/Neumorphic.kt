package com.pookie.studysprint.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class NeuColors(val light: Color, val dark: Color)

@Composable
private fun neuColors(): NeuColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        NeuColors(light = Color(0x1AFFFFFF), dark = Color(0xB3000000))
    } else {
        NeuColors(light = Color(0xFFFFFFFF), dark = Color(0x59A39C8F))
    }
}

/** Shape ke Outline ko Path mein badalta hai (Compose mein addOutline available nahi hai). */
private fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
}

/** Native Android paint, shadow layer ke saath. */
private fun shadowPaint(fill: Color, radius: Float, dx: Float, dy: Float, shadow: Color): android.graphics.Paint {
    return android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = fill.toArgb()
        setShadowLayer(radius, dx, dy, shadow.toArgb())
    }
}

/** Ubhra hua (raised) card. Background ke same color par do shadows: light upar-left, dark neeche-right. */
@Composable
fun Modifier.neuRaised(
    shape: Shape = Radius.card,
    elevation: Dp = 8.dp,
    fill: Color = MaterialTheme.colorScheme.background
): Modifier {
    val c = neuColors()
    return this.drawBehind {
        val path = outlineToPath(shape.createOutline(size, layoutDirection, this)).asAndroidPath()
        val e = elevation.toPx()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.drawPath(path, shadowPaint(fill, e * 1.5f, -e, -e, c.light))
            native.drawPath(path, shadowPaint(fill, e * 1.5f, e, e, c.dark))
        }
    }
}

/** Dabi hui (inset) surface: input fields, progress track, pressed buttons. */
@Composable
fun Modifier.neuInset(
    shape: Shape = Radius.control,
    depth: Dp = 5.dp,
    fill: Color = MaterialTheme.colorScheme.background
): Modifier {
    val c = neuColors()
    return this.drawBehind {
        val shapePath = outlineToPath(shape.createOutline(size, layoutDirection, this))
        val d = depth.toPx()
        val big = Path().apply {
            addRect(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f))
        }
        val frame = Path.combine(PathOperation.Difference, big, shapePath).asAndroidPath()

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val basePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = fill.toArgb()
            }
            native.drawPath(shapePath.asAndroidPath(), basePaint)

            native.save()
            native.clipPath(shapePath.asAndroidPath())
            native.drawPath(frame, shadowPaint(fill, d * 1.5f, d, d, c.dark))
            native.drawPath(frame, shadowPaint(fill, d * 1.5f, -d, -d, c.light))
            native.restore()
        }
    }
}

/**
 * Neumorphic button: normal = raised, press karne par = inset.
 * Text hamesha ek hi line mein rehta hai (jaise "Resume"), isliye side padding chhoti rakhi hai.
 */
@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val textColor = if (primary) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .let { if (pressed) it.neuInset(Radius.control) else it.neuRaised(Radius.control, 6.dp) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}