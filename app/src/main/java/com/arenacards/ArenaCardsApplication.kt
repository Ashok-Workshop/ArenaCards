package com.arenacards

import android.app.Application
import com.google.firebase.FirebaseApp

class ArenaCardsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        checkNotNull(FirebaseApp.initializeApp(this)) {
            "Firebase could not initialize. Verify app/google-services.json and the Google Services Gradle plugin."
        }
    }
}
