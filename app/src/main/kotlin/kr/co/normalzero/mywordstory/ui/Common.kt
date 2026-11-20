package kr.co.normalzero.mywordstory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colors from the design mockups.
object Palette {
    val ground = Color(0xFFF3F5F7)
    val surface = Color.White
    val ink = Color(0xFF15202B)
    val muted = Color(0xFF5B6673)
    val line = Color(0xFFDCE1E7)
    val accent = Color(0xFF2346C8)
    val accentSoft = Color(0xFFE6ECFB)
    val ok = Color(0xFF1F7A4D)
    val okSoft = Color(0xFFE3F3EA)
    val bad = Color(0xFFB3261E)
    val badSoft = Color(0xFFFCEBEA)
    val business = Color(0xFFA34D12)
    val businessSoft = Color(0xFFFBEBDD)
}

val AppColors = lightColorScheme(
    primary = Palette.accent,
    onPrimary = Color.White,
    background = Palette.ground,
    surface = Palette.surface,
    onSurface = Palette.ink,
    onBackground = Palette.ink,
    error = Palette.bad,
)

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.ground).systemBarsPadding().padding(horizontal = 20.dp),
        content = content,
    )
}

/** Top of a study screen: label, optional action, progress bar. */
@Composable
fun StudyHeader(label: String, progress: Float, onClose: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("✕", color = Palette.ink, fontSize = 18.sp) }
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Palette.accent,
            trackColor = Palette.line,
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.ink) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (color == Palette.ink) 1.dp else 2.dp, if (color == Palette.ink) Palette.line else color),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.surface, contentColor = color),
    ) { Text(text, fontSize = 16.sp, fontWeight = if (color == Palette.ink) FontWeight.Medium else FontWeight.Bold) }
}

@Composable
fun Chip(text: String, fg: Color, bg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
}

fun tagLabel(tag: String) = if (tag == "business") "IT 비즈니스" else "일상"
