package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

/**
 * The cognitive interrupt. Naming the feeling you are escaping is the point; the text is never
 * stored. Step 2 (the burn button) only appears once something has been written.
 *
 * @param onBurn erases the text and hands control back to the ViewModel, which increments the
 * dodged counter and locks the device (or routes to accessibility settings).
 */
@Composable
fun ImpulseInterceptorDialog(
    accessibilityEnabled: Boolean,
    onBurn: () -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    SolisFullScreenDialog(onDismiss = onDismiss) {
        // Must run inside the dialog's own composition: the text field lives in the dialog
        // window, and requesting focus before it is attached throws.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Text("step 1", style = MaterialTheme.typography.labelMedium, color = SolisColors.Accent)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "What uncomfortable feeling or task are you running from right now?",
            style = MaterialTheme.typography.titleLarge,
            color = SolisColors.White,
        )
        Spacer(Modifier.height(28.dp))

        Column {
            BasicTextField(
                value = trigger,
                onValueChange = { trigger = it.take(MAX_LENGTH) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = SolisColors.White),
                cursorBrush = SolidColor(SolisColors.White),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .focusRequester(focusRequester)
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (trigger.isEmpty()) {
                            Text(
                                "avoiding writing paper. bored and lonely.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SolisColors.Accent,
                            )
                        }
                        inner()
                    }
                },
            )
            HorizontalDivider(color = SolisColors.Accent, thickness = 1.dp)
        }

        Spacer(Modifier.weight(1f))

        if (trigger.isNotBlank()) {
            Text("step 2", style = MaterialTheme.typography.labelMedium, color = SolisColors.Accent)
            Spacer(Modifier.height(8.dp))
            SolisOutlinedButton(
                text = "[ Burn It & Sleep Device ]",
                onClick = {
                    trigger = ""
                    onBurn()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!accessibilityEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "solis needs accessibility access once to turn the screen off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolisColors.Accent,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextAction(
            text = "never mind",
            onClick = {
                trigger = ""
                onDismiss()
            },
            color = SolisColors.Accent,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private const val MAX_LENGTH = 280
