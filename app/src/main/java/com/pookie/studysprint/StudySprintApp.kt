package com.pookie.studysprint

import android.app.Application

class StudySprintApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Adhoora session ho to wapas laao (PAUSED state mein). Background mein chalta hai, UI nahi rukti.
        container.sessionEngine.recover()
    }
}