package com.ykz200.smiletracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring

// v5.0 Color Constants
private val ColorSmile    = Color(0xFF4CAF50)
private val ColorAngry    = Color(0xFFE91E63)
private val ColorNeutral  = Color(0xFF5C6BC0)
private val ColorThinking = Color(0xFF7E57C2)
private val ColorSurprise = Color(0xFFFFC107)
private val ColorFear     = Color(0xFFFF5722)
private val ColorDisgust  = Color(0xFF795548)

@Composable
internal fun MiniCircleButtonV4(
    label: String,
    backgroundColor: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFEEEEEE),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun EmotionBarRow(name: String, prob: Float, color: Color, isTop: Boolean) {
    val barFracState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(prob) {
        animate(
            initialValue = barFracState.floatValue,
            targetValue = prob.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = 120f)
        ) { value, _ -> barFracState.floatValue = value }
    }
    val barFrac = barFracState.floatValue
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            color = if (isTop) color else Color(0xFFCCCCCC),
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(28.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 4.dp)
        ) {
            if (barFrac > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFrac)
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.6f), color)
                            ),
                            RoundedCornerShape(6.dp)
                        )
                )
            }
        }
        Text(
            text = "${(prob * 100).toInt()}%",
            fontSize = 10.sp,
            color = if (isTop) color else Color(0xFF888888),
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp)
        )
    }
}

internal fun emotionColorFromName(emotionName: String): Color = when (emotionName) {
    "微笑" -> ColorSmile
    "生气" -> ColorAngry
    "中性" -> ColorNeutral
    "思考" -> ColorThinking
    "惊讶" -> ColorSurprise
    "恐惧" -> ColorFear
    "厌恶" -> ColorDisgust
    else -> Color(0xFF00E5FF)
}
