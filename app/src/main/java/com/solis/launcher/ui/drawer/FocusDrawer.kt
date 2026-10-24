package com.solis.launcher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.domain.AppSection
import com.solis.launcher.domain.AwayTime
import com.solis.launcher.domain.CurfewPolicy
import com.solis.launcher.domain.FocusSession
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction
import kotlinx.coroutines.launch

/**
 * The app list. No icons, no grid, no search: every app by name, A to Z, under a letter
 * heading. The letters down the right edge jump to a section. Vault apps sit behind one tap at
 * the end of the list.
 */
@Composable
fun FocusDrawer(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 28.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
    ) {
        if (state.drawerLocked) {
            DrawerLockout(state, onAction)
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 16.dp)) {
            Text("apps", style = MaterialTheme.typography.headlineMedium, color = SolisColors.White)
            Spacer(Modifier.weight(1f))
            Text(
                "${state.visibleAppCount}",
                style = MaterialTheme.typography.labelMedium,
                color = SolisColors.Accent,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp, modifier = Modifier.padding(end = 16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.appSections.isEmpty() && state.vaultApps.isEmpty()) {
                EmptyMessage("no apps.")
            } else {
                AlphabeticalList(state, onAction)
            }
        }

        HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp, modifier = Modifier.padding(end = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
            TextAction(
                text = "× home",
                onClick = { onAction(LauncherAction.HomePressed) },
                color = SolisColors.Accent,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                text = "settings",
                onClick = { onAction(LauncherAction.OpenSettings) },
                color = SolisColors.Accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AlphabeticalList(state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = state.appSections
    // Index in the lazy list of each section's header: one header item plus its apps.
    val headerIndex = remember(sections) {
        var i = 0
        sections.associate { section -> section.letter to i.also { i += 1 + section.apps.size } }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            sections.forEach { letterSection(it, state, onAction) }
            vault(state, onAction)
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (sections.size > 1) {
            LetterIndex(
                letters = sections.map { it.letter },
                onPick = { letter ->
                    headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                },
            )
        }
    }
}

private fun LazyListScope.letterSection(
    section: AppSection,
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
) {
    item(key = "letter-${section.letter}") {
        Text(
            text = section.letter.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = SolisColors.Accent,
            modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
        )
    }
    items(section.apps, key = { it.key }) { app -> AppRow(app, state, onAction) }
}

private fun LazyListScope.vault(state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    if (state.vaultApps.isEmpty()) return
    item(key = "vault") {
        Column(modifier = Modifier.padding(top = 28.dp)) {
            HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp)
            TextAction(
                text = if (state.vaultOpen) "▾ vault (${state.vaultApps.size})" else "▸ vault (${state.vaultApps.size})",
                onClick = { onAction(LauncherAction.ToggleVault) },
                color = SolisColors.Accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (state.vaultOpen) {
        items(state.vaultApps, key = { "vault-${it.key}" }) { app -> AppRow(app, state, onAction) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppEntry, state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    val flagged = app.packageName in state.prefs.distractionPackages
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onAction(LauncherAction.LaunchApp(app)) },
                onLongClick = { onAction(LauncherAction.LongPressApp(app)) },
            )
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = app.label.lowercase(),
            style = MaterialTheme.typography.titleMedium,
            color = if (flagged) SolisColors.Accent else SolisColors.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (flagged) {
            val away = state.awayFrom(app.packageName)
            Text(
                text = if (away != null) "⧗ ${AwayTime.format(away)} away" else "⧗ delayed",
                style = MaterialTheme.typography.labelSmall,
                color = SolisColors.Accent,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            )
        }
    }
}

/** A narrow column of letters; tapping one jumps the list to that section. */
@Composable
private fun LetterIndex(letters: List<Char>, onPick: (Char) -> Unit) {
    Column(
        modifier = Modifier.width(28.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = SolisColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(letter) }
                    .padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun DrawerLockout(state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    // A focus session can outlast the curfew, so it wins when both apply.
    val (title, opens) = if (state.focusActive) {
        "focusing." to "in ${FocusSession.format(state.focusRemainingSeconds)}"
    } else {
        "curfew." to "at ${CurfewPolicy.format(state.prefs.curfew.endMinutes)}"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(end = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = SolisColors.White)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "the app list opens again $opens.\n" +
                "phone, messages and the rest of home still work.",
            style = MaterialTheme.typography.bodyMedium,
            color = SolisColors.Accent,
        )
        Spacer(Modifier.height(32.dp))
        SolisOutlinedButton(text = "[ back home ]", onClick = { onAction(LauncherAction.HomePressed) })
        Spacer(Modifier.height(12.dp))
        SolisOutlinedButton(
            text = "[ take a moment instead ]",
            onClick = { onAction(LauncherAction.OpenMoment) },
            emphasized = false,
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SolisColors.Accent)
    }
}
