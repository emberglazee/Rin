package com.rin.terminal

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.rin.RinLib


class SessionManager(
    private val homeDir: String,
    private val username: String
) {
    val sessions = mutableStateListOf<TerminalSession>()
    val activeIndexState = mutableIntStateOf(0)

    val activeIndex: Int get() = activeIndexState.intValue

    val activeSession: TerminalSession?
        get() = sessions.getOrNull(activeIndexState.intValue)

    private var sessionCounter = 0

    fun createSession(): TerminalSession {
        sessionCounter++
        val handle = RinLib.createEngine(
            80, 24, 14.0f,
            homeDir,
            username
        )
        val session = TerminalSession(
            name = "Session $sessionCounter",
            engineHandle = handle
        )
        sessions.add(session)
        activeIndexState.intValue = sessions.size - 1
        return session
    }

    fun switchSession(index: Int) {
        if (index in sessions.indices) {
            activeIndexState.intValue = index
        }
    }

    fun removeSession(index: Int) {
        if (index !in sessions.indices) return
        val session = sessions[index]

        // Destroy the native PTY engine
        if (session.engineHandle != 0L) {
            RinLib.destroyEngine(session.engineHandle)
        }
        sessions.removeAt(index)

        // Adjust active index after removal
        if (sessions.isEmpty()) {
            // Always keep at least one session alive
            createSession()
        } else {
            activeIndexState.intValue = activeIndexState.intValue.coerceIn(0, sessions.size - 1)
        }
    }

    fun renameSession(index: Int, newName: String) {
        if (index in sessions.indices) {
            sessions[index] = sessions[index].copy(name = newName)
        }
    }

    fun destroyAll() {
        sessions.forEach { session ->
            if (session.engineHandle != 0L) {
                RinLib.destroyEngine(session.engineHandle)
            }
        }
        sessions.clear()
    }

    val sessionCount: Int get() = sessions.size
}
