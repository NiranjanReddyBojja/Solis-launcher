package com.solis.launcher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.domain.CurfewPolicy
import com.solis.launcher.domain.FocusSession
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

/**
 * The "one app at a time" drawer. No icons, no grid. With an empty query, a vertical pager shows
 * exactly one app name per page with a friction index; typing switches to a filtered text list.
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
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        if (state.drawerLocked) {
            DrawerLockout(state, onAction)
            return@Column
        }

        SearchBar(
            query = state.query,
            onQueryChange = { onAction(LauncherAction.QueryChanged(it)) },
            onGo = { state.searchResults.firstOrNull()?.let { onAction(LauncherAction.LaunchApp(it)) } },
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.query.isBlank()) {
                AppCarousel(state.carouselApps, state.prefs.distractionPackages, onAction)
            } else {
                SearchResults(state.searchResults, onAction)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Instant focus: the keyboard is up the moment the drawer opens.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = SolisColors.White),
            cursorBrush = SolidColor(SolisColors.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "> search_",
                            style = MaterialTheme.typography.titleLarge,
                            color = SolisColors.Accent,
                        )
                    }
                    inner()
                }
            },
        )
        HorizontalDivider(color = SolisColors.Accent, thickness = 1.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCarousel(
    apps: List<AppEntry>,
    distractions: Set<String>,
    onAction: (LauncherAction) -> Unit,
) {
    if (apps.isEmpty()) {
        EmptyMessage("no apps.")
        return
    }
    val pagerState = rememberPagerState(pageCount = { apps.size })

    Column(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            key = { apps[it].key },
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val app = apps[page]
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = app.label.lowercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SolisColors.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onAction(LauncherAction.LaunchApp(app)) },
                                onLongClick = { onAction(LauncherAction.LongPressApp(app)) },
                            )
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                    if (app.packageName in distractions) {
                        Text(
                            text = "⧗ delayed",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolisColors.Accent,
                        )
                    }
                }
            }
        }
        Text(
            text = "app ${pagerState.currentPage + 1} of ${apps.size}",
            style = MaterialTheme.typography.labelMedium,
            color = SolisColors.Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResults(results: List<AppEntry>, onAction: (LauncherAction) -> Unit) {
    if (results.isEmpty()) {
        EmptyMessage("nothing here. good.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(results, key = { it.key }) { app ->
            Text(
                text = app.label.lowercase(),
                style = MaterialTheme.typography.titleMedium,
                color = SolisColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onAction(LauncherAction.LaunchApp(app)) },
                        onLongClick = { onAction(LauncherAction.LongPressApp(app)) },
                    )
                    .padding(vertical = 14.dp),
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = SolisColors.White)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "the drawer opens again $opens.\n" +
                "phone, messages and the rest of home still work.",
            style = MaterialTheme.typography.bodyMedium,
            color = SolisColors.Accent,
        )
        Spacer(Modifier.height(32.dp))
        SolisOutlinedButton(text = "[ back home ]", onClick = { onAction(LauncherAction.HomePressed) })
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SolisColors.Accent)
    }
}
