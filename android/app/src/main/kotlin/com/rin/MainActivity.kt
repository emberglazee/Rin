package com.rin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.rin.service.TerminalSessionService
import com.rin.terminal.SessionManager
import com.rin.ui.screen.SetupScreen
import com.rin.ui.screen.TerminalScreen
import com.rin.ui.screen.getStoredUsername
import com.rin.ui.theme.RinTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private var terminalService: TerminalSessionService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalSessionService.LocalBinder
            terminalService = binder.service
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            serviceBound = false
        }
    }

    private fun installPrebuiltBinaries() {
        val binDir = File(filesDir, "usr/bin").also { it.mkdirs() }
        val rpkgBin = File(binDir, "rpkg")
        val nativeDir = applicationInfo.nativeLibraryDir
        val nativeLib = File(nativeDir, "librpkg_cli.so")

        if (nativeLib.exists()) {
            rpkgBin.delete()
            try {
                android.system.Os.symlink(nativeLib.absolutePath, rpkgBin.absolutePath)
                Log.i("Rin", "Symlinked librpkg_cli.so to rpkg")
            } catch (e: Exception) {
                Log.e("Rin", "Failed to symlink rpkg: ${e.message}")
            }
        } else {
            Log.w("Rin", "Native library librpkg_cli.so not found in $nativeDir")
        }
    }

    private fun startTerminalService() {
        val intent = Intent(this, TerminalSessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installPrebuiltBinaries()
        startTerminalService()

        setContent {
            var username by remember { mutableStateOf(getStoredUsername(this)) }

            RinTheme {
                val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    window.statusBarColor = surfaceColor
                    window.navigationBarColor = surfaceColor
                }
                if (username == null) {
                    SetupScreen(
                        onComplete = { name ->
                            username = name
                        }
                    )
                } else {
                    val homeDir = filesDir.resolve("home").also { it.mkdirs() }
                    val currentUser = username ?: "user"

                    val prefix = filesDir.absolutePath
                    val mkshrc = homeDir.resolve(".mkshrc")
                    mkshrc.writeText("""
                        USER=${"$"}{USER:-$currentUser}
                        export PATH=$prefix/usr/bin:${"$"}{PATH}
                        export LD_LIBRARY_PATH=$prefix/usr/lib:${"$"}{LD_LIBRARY_PATH}
                        PS1="rin@${"$"}USER:~${"$"} "
                    """.trimIndent() + "\n")

                    val sessionManager = remember {
                        SessionManager(
                            homeDir = homeDir.absolutePath,
                            username = currentUser
                        )
                    }

                    // Create the first session
                    DisposableEffect(sessionManager) {
                        if (sessionManager.sessions.isEmpty()) {
                            sessionManager.createSession()
                        }
                        terminalService?.sessionManager = sessionManager

                        onDispose {
                            sessionManager.destroyAll()
                        }
                    }
                    val sessionCount = sessionManager.sessionCount
                    SideEffect {
                        terminalService?.sessionManager = sessionManager
                        terminalService?.updateNotification(sessionCount)
                    }

                    TerminalScreen(sessionManager = sessionManager)
                }
            }
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
