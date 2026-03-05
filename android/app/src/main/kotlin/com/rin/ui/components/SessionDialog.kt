package com.rin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rin.terminal.TerminalSession


@Composable
fun SessionDialog(
    sessions: List<TerminalSession>,
    activeIndex: Int,
    onDismiss: () -> Unit,
    onSwitchSession: (Int) -> Unit,
    onCreateSession: () -> Unit,
    onRemoveSession: (Int) -> Unit,
    onRenameSession: (Int, String) -> Unit
) {
    var renameIndex by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(-1) }

    if (renameIndex >= 0) {
        AlertDialog(
            onDismissRequest = { renameIndex = -1 },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Session name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameSession(renameIndex, renameText.trim())
                    }
                    renameIndex = -1
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameIndex = -1 }) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    if (showDeleteConfirm >= 0) {
        val sessionName = sessions.getOrNull(showDeleteConfirm)?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = -1 },
            title = { Text("Remove Session") },
            text = { Text("Remove \"$sessionName\"? The shell process will be terminated.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSession(showDeleteConfirm)
                    showDeleteConfirm = -1
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = -1 }) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sessions",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((sessions.size.coerceAtMost(6) * 52).dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(sessions) { index, session ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchSession(index)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (index == activeIndex)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = index == activeIndex,
                                    onClick = {
                                        onSwitchSession(index)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = session.name,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = {
                                        renameIndex = index
                                        renameText = session.name
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("✎", fontSize = 16.sp)
                                }
                                // Delete button
                                IconButton(
                                    onClick = { showDeleteConfirm = index },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = {
                        onCreateSession()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("＋ New Session", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
