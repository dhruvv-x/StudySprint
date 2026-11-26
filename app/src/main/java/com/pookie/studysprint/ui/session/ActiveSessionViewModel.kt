package com.pookie.studysprint.ui.session

import androidx.lifecycle.ViewModel
import com.pookie.studysprint.engine.EngineState
import com.pookie.studysprint.engine.SessionEngine
import com.pookie.studysprint.engine.SessionEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Screen aur engine ke beech ki patli parat. Engine app-scoped hai (screen se zyada jeeta hai),
 * isliye ViewModel clear hone par kuch cancel nahi hota: sirf commands aage bhejta hai.
 */
class ActiveSessionViewModel(private val engine: SessionEngine) : ViewModel() {

    val state: StateFlow<EngineState> = engine.state
    val events: SharedFlow<SessionEvent> = engine.events

    /** false = app start ka recovery abhi chal raha hai (engine idle dikhe to bhi screen band nahi karni). */
    val recoveryFinished: StateFlow<Boolean> = engine.recoveryFinished

    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun done() = engine.completeCurrent()
    fun skip() = engine.skipCurrent()
    fun stop() = engine.stop()

    /** Result panel band: engine idle ho jaata hai aur screen khud band ho jaati hai. */
    fun closeFinished() = engine.dismissFinished()
}