package com.rin.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ExtraKey(
    val label: String,
    val code: String,
    val isModifier: Boolean = false,
    val repeatable: Boolean = false
)

private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L

private val row1Keys = listOf(
    ExtraKey("ESC", "\u001b"),
    ExtraKey("/", "/"),
    ExtraKey("-", "-"),
    ExtraKey("HOME", "\u001b[H"),
    ExtraKey("▲", "\u001b[A", repeatable = true),
    ExtraKey("END", "\u001b[F"),
    ExtraKey("PGUP", "\u001b[5~")
)

private val row2Keys = listOf(
    ExtraKey("TAB", "\t"),
    ExtraKey("CTRL", "CTRL", isModifier = true),
    ExtraKey("ALT", "ALT", isModifier = true),
    ExtraKey("◄", "\u001b[D", repeatable = true),
    ExtraKey("▼", "\u001b[B", repeatable = true),
    ExtraKey("►", "\u001b[C", repeatable = true),
    ExtraKey("PGDN", "\u001b[6~")
)

@Composable
fun ExtraKeysBar(
    onKeyPress: (String) -> Unit,
    onCtrlToggle: (Boolean) -> Unit,
    onRepeatStateChange: (Boolean) -> Unit = {},
    sessionName: String = "Session 1",
    onSessionButtonClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var ctrlActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalButton(
            onClick = onSessionButtonClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = "⊞ $sessionName",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        KeyRow(
            keys = row1Keys,
            ctrlActive = ctrlActive,
            onKeyPress = onKeyPress,
            onCtrlToggle = { },
            onRepeatStateChange = onRepeatStateChange
        )
        KeyRow(
            keys = row2Keys,
            ctrlActive = ctrlActive,
            onKeyPress = onKeyPress,
            onCtrlToggle = { active ->
                ctrlActive = active
                onCtrlToggle(active)
            },
            onRepeatStateChange = onRepeatStateChange
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<ExtraKey>,
    ctrlActive: Boolean,
    onKeyPress: (String) -> Unit,
    onCtrlToggle: (Boolean) -> Unit,
    onRepeatStateChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        keys.forEach { key ->
            val isActive = key.label == "CTRL" && ctrlActive

            if (key.repeatable) {
                RepeatableKeyButton(
                    key = key,
                    onKeyPress = onKeyPress,
                    onRepeatStateChange = onRepeatStateChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = {
                        when {
                            key.label == "CTRL" -> onCtrlToggle(!ctrlActive)
                            key.label == "ALT" -> { }
                            else -> onKeyPress(key.code)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isActive)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = key.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatableKeyButton(
    key: ExtraKey,
    onKeyPress: (String) -> Unit,
    onRepeatStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val containerColor = if (pressed)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (pressed)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .pointerInput(key.code) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onRepeatStateChange(true)
                        onKeyPress(key.code)

                        val repeatJob: Job = scope.launch {
                            delay(REPEAT_INITIAL_DELAY_MS)
                            while (true) {
                                onKeyPress(key.code)
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }

                        tryAwaitRelease()
                        repeatJob.cancel()
                        pressed = false
                        onRepeatStateChange(false)
                    }
                )
            },
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

