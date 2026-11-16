package com.arenacards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseBackendConfigTest {
    @Test
    fun selectsTheVerifiedFirebaseProjectAndNamedFirestoreDatabase() {
        assertEquals("arenacards-f3279", FirebaseBackendConfig.PROJECT_ID)
        assertEquals("unoashok", FirebaseBackendConfig.FIRESTORE_DATABASE_ID)
        assertEquals("us-central1", FirebaseBackendConfig.FUNCTIONS_REGION)
        FirebaseBackendConfig.requireExpectedProject("arenacards-f3279")
    }

    @Test
    fun rejectsAnUnexpectedFirebaseProject() {
        assertThrows(IllegalStateException::class.java) {
            FirebaseBackendConfig.requireExpectedProject("unexpected-project")
        }
    }
}
