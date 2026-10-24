package com.solis.launcher.ui.moment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.Reflection
import com.solis.launcher.domain.SelfCare
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "A moment": time for the user instead of the phone. One minute of box breathing, then one
 * reflection prompt a day, saved privately on the device.
 */
@Composable
fun MomentScreen(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompt = SelfCare.promptFor(state.now.toLocalDate().toEpochDay())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("a moment.", style = MaterialTheme.typography.headlineMedium, color = SolisColors.White)
                Spacer(Modifier.weight(1f))
                TextAction("× close", { onAction(LauncherAction.BackPressed) }, color = SolisColors.Accent,
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "this one is for you, not the phone.",
                style = MaterialTheme.typography.labelMedium,
                color = SolisColors.Accent,
            )
        }

        heading("breathe")
        item { BoxBreathing() }

        heading("reflect")
        item { ReflectionComposer(prompt) { onAction(LauncherAction.SaveReflection(prompt, it)) } }

        if (state.prefs.reflections.isNotEmpty()) {
            heading("notes to self")
            items(state.prefs.reflections, key = { it.atMillis }) { entry ->
                ReflectionRow(entry) { onAction(LauncherAction.DeleteReflection(entry.atMillis)) }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

private fun LazyListScope.heading(title: String) {
    item(key = "heading-$title") {
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = SolisColors.Accent)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Box breathing: a square traced one side per phase (in, hold, out, hold), four seconds each,
 * four rounds. Monochrome: a dim square with a white line drawing over it.
 */
@Composable
private fun BoxBreathing() {
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var phase by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        progress.snapTo(0f)
        for (r in 0 until SelfCare.BREATH_ROUNDS) {
            round = r
            for (p in SelfCare.BREATH_PHASES.indices) {
                phase = p
                val sideStart = p / 4f
                progress.snapTo(sideStart)
                progress.animateTo(
                    sideStart + 0.25f,
                    tween(SelfCare.BREATH_PHASE_SECONDS * 1_000, easing = LinearEasing),
                )
            }
        }
        running = false
        finished = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f).padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawRect(SolisColors.Dim, size = size, style = Stroke(stroke))
                if (running) drawTracedSquare(progress.value, stroke)
            }
            Text(
                text = when {
                    running -> SelfCare.BREATH_PHASES[phase]
                    finished -> "well done."
                    else -> "4 · 4 · 4 · 4"
                },
                style = MaterialTheme.typography.titleMedium,
                color = SolisColors.White,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                running -> "round ${round + 1} of ${SelfCare.BREATH_ROUNDS}"
                finished -> "notice how your body feels now."
                else -> "in for four, hold for four, out for four, hold for four."
            },
            style = MaterialTheme.typography.bodySmall,
            color = SolisColors.Accent,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SolisOutlinedButton(
            text = if (running) "[ stop ]" else "[ breathe · 1 min ]",
            onClick = {
                finished = false
                running = !running
            },
            emphasized = !running,
        )
    }
}

/** Draws the part of the square's outline already traced, clockwise from the top-left corner. */
private fun DrawScope.drawTracedSquare(progress: Float, stroke: Float) {
    val w = size.width
    val h = size.height
    val corners = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h), Offset(0f, 0f))
    val traced = progress.coerceIn(0f, 1f) * 4f
    for (side in 0 until 4) {
        val amount = (traced - side).coerceIn(0f, 1f)
        if (amount <= 0f) break
        val from = corners[side]
        val to = corners[side + 1]
        drawLine(SolisColors.White, from, from + (to - from) * amount, strokeWidth = stroke * 1.5f)
    }
}

@Composable
private fun ReflectionComposer(prompt: String, onSave: (String) -> Unit) {
    var draft by rememberSaveable(prompt) { mutableStateOf("") }
    Column {
        Text(prompt, style = MaterialTheme.typography.titleMedium, color = SolisColors.White)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.take(SelfCare.MAX_REFLECTION_LENGTH) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SolisColors.White),
            cursorBrush = SolidColor(SolisColors.White),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) {
                        Text("write freely. only you will see this.", style = MaterialTheme.typography.bodyMedium,
                            color = SolisColors.Accent)
                    }
                    inner()
                }
            },
        )
        HorizontalDivider(color = SolisColors.Accent, thickness = 1.dp)
        TextAction(
            "[ keep it ]",
            onClick = {
                onSave(draft)
                draft = ""
            },
            color = if (draft.isBlank()) SolisColors.Accent else SolisColors.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

@Composable
private fun ReflectionRow(entry: Reflection, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text(
                Instant.ofEpochMilli(entry.atMillis).atZone(ZoneId.systemDefault()).format(DATE).lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SolisColors.Accent,
            )
            Text(
                "×",
                style = MaterialTheme.typography.titleMedium,
                color = SolisColors.Accent,
                modifier = Modifier.clickable(onClick = onDelete).padding(start = 12.dp),
            )
        }
        Text(entry.prompt, style = MaterialTheme.typography.bodySmall, color = SolisColors.Accent)
        Spacer(Modifier.height(4.dp))
        Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = SolisColors.White)
    }
}
