package com.arenacards.data

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

internal object FirebaseBackendConfig {
    const val PROJECT_ID = "arenacards-f3279"
    const val FIRESTORE_DATABASE_ID = "unoashok"
    const val FUNCTIONS_REGION = "us-central1"

    fun requireExpectedProject(projectId: String?) {
        check(projectId == PROJECT_ID) {
            "Firebase project mismatch. Expected $PROJECT_ID but found ${projectId ?: "none"}."
        }
    }
}

internal object FirebaseFirestoreProvider {
    fun getInstance(app: FirebaseApp): FirebaseFirestore {
        FirebaseBackendConfig.requireExpectedProject(app.options.projectId)
        return FirebaseFirestore.getInstance(app, FirebaseBackendConfig.FIRESTORE_DATABASE_ID)
    }
}
