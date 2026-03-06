package com.rin.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class CommandHelp(
    val command: String,
    val description: String,
    val category: String
)

private val helpCommands = listOf(
    CommandHelp(
        command = "rpkg -Sy",
        description = "Sync package database from repository",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -S <package>",
        description = "Install a package (e.g., rpkg -S vim)",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -Syu",
        description = "Sync database and upgrade all packages",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -Su",
        description = "Upgrade all installed packages",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -Ss <query>",
        description = "Search for packages (e.g., rpkg -Ss python)",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -Sf <package>",
        description = "Force reinstall a package",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -R <package>",
        description = "Remove/uninstall a package",
        category = "Package Management"
    ),
    CommandHelp(
        command = "rpkg -Q",
        description = "List all installed packages",
        category = "Package Management"
    ),
    CommandHelp(
        command = "ls",
        description = "List directory contents",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "ls -la",
        description = "List all files with details",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "cd <directory>",
        description = "Change directory (e.g., cd /sdcard)",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "cd ~",
        description = "Go to home directory",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "pwd",
        description = "Print current working directory",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "cat <file>",
        description = "Display file contents",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "mkdir <directory>",
        description = "Create new directory",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "rm <file>",
        description = "Remove file",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "rm -rf <directory>",
        description = "Remove directory recursively",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "cp <source> <dest>",
        description = "Copy file or directory",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "mv <source> <dest>",
        description = "Move or rename file",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "clear",
        description = "Clear terminal screen",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "echo <text>",
        description = "Print text to terminal",
        category = "Basic Commands"
    ),
    CommandHelp(
        command = "exit",
        description = "Exit current session",
        category = "Terminal Control"
    ),
    CommandHelp(
        command = "help",
        description = "Show this help dialog",
        category = "Terminal Control"
    )
)

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rin Terminal Help",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Divider()
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val groupedCommands = helpCommands.groupBy { it.category }
                    
                    groupedCommands.forEach { (category, commands) ->
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        items(commands) { cmd ->
                            CommandItem(
                                command = cmd,
                                onCopy = {
                                    val clip = ClipData.newPlainText("command", cmd.command)
                                    clipboardManager.setPrimaryClip(clip)
                                }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandItem(
    command: CommandHelp,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = command.command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = command.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy command",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
