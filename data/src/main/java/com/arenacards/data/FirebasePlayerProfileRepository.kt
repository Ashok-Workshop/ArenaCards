package com.arenacards.data

import com.arenacards.feature.profile.PlayerProfile
import com.arenacards.feature.profile.PlayerProfileRepository
import com.arenacards.feature.profile.ProfileVisibility
import com.arenacards.feature.profile.PublicPlayer
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebasePlayerProfileRepository : PlayerProfileRepository {
    private val firebaseApp = FirebaseApp.getInstance()
    private val auth = FirebaseAuth.getInstance(firebaseApp)
    private val firestore = FirebaseFirestoreProvider.getInstance(firebaseApp)
    private val functions = FirebaseFunctions.getInstance(
        firebaseApp,
        FirebaseBackendConfig.FUNCTIONS_REGION
    )

    override fun observeMyProfile(uid: String): Flow<PlayerProfile?> = callbackFlow {
        requireCurrentUid(uid)
        var registration: ListenerRegistration? = null
        registration = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toUserFacingProfileError())
                    snapshot == null -> close(IllegalStateException("Profile response was empty."))
                    !snapshot.exists() -> trySend(null)
                    else -> {
                        try {
                            val data = snapshot.data
                                ?: error("Profile data is missing.")
                            trySend(data.toPlayerProfile(uid))
                        } catch (mappingError: Exception) {
                            close(IllegalStateException(
                                "Player profile data is incomplete or invalid.",
                                mappingError
                            ))
                        }
                    }
                }
            }
        awaitClose { registration?.remove() }
    }

    override suspend fun isUsernameAvailable(username: String): Boolean {
        requireAuthenticatedUid()
        val result = callFunction("checkUsernameAvailability", mapOf("username" to username))
            .asStringKeyedMap()
        return result["available"] as? Boolean
            ?: throw IllegalStateException("Username availability response was invalid.")
    }

    override suspend fun createProfile(
        username: String,
        displayName: String,
        avatarId: String
    ): PlayerProfile {
        val result = callFunction(
            "createPlayerProfile",
            mapOf(
                "username" to username,
                "displayName" to displayName,
                "avatarId" to avatarId
            )
        ).asStringKeyedMap()
        return result.toPlayerProfile(requireAuthenticatedUid())
    }

    override suspend fun updateProfile(
        username: String,
        displayName: String,
        avatarId: String,
        visibility: ProfileVisibility
    ): PlayerProfile {
        val result = callFunction(
            "updatePlayerProfile",
            mapOf(
                "username" to username,
                "displayName" to displayName,
                "avatarId" to avatarId,
                "profileVisibility" to visibility.wireValue
            )
        ).asStringKeyedMap()
        return result.toPlayerProfile(requireAuthenticatedUid())
    }

    override suspend fun searchPublicPlayers(usernamePrefix: String): List<PublicPlayer> {
        requireAuthenticatedUid()
        val result = callFunction(
            "searchPublicPlayers",
            mapOf("usernamePrefix" to usernamePrefix)
        )
        val entries = result as? List<*>
            ?: throw IllegalStateException("Player search response was invalid.")
        return entries.map { item ->
            val data = item as? Map<*, *>
                ?: throw IllegalStateException("Player search item was invalid.")
            PublicPlayer(
                uid = data.requiredString("uid"),
                gamerId = data.requiredString("gamerId"),
                username = data.requiredString("username"),
                displayName = data.requiredString("displayName"),
                avatarId = data.requiredString("avatarId"),
                playerLevel = data.requiredNumber("playerLevel").toInt()
            )
        }
    }

    private suspend fun callFunction(
        name: String,
        payload: Map<String, String>
    ): Any? {
        requireAuthenticatedUid()
        return try {
            functions.getHttpsCallable(name).call(payload).await().getData()
        } catch (error: FirebaseFunctionsException) {
            throw error.toUserFacingProfileError()
        } catch (error: Exception) {
            throw error.toUserFacingProfileError()
        }
    }

    private fun Any?.asStringKeyedMap(): Map<String, Any?> {
        val map = this as? Map<*, *>
            ?: throw IllegalStateException("Profile service returned an invalid response.")
        return map.entries.associate { (key, value) ->
            (key as? String
                ?: throw IllegalStateException("Profile service response contained an invalid field.")) to value
        }
    }

    private fun requireCurrentUid(uid: String) {
        val currentUid = requireAuthenticatedUid()
        require(uid == currentUid) { "A user can only load their own player profile." }
    }

    private fun requireAuthenticatedUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Sign in to access your player profile.")

    private fun Map<String, Any?>.toPlayerProfile(uid: String): PlayerProfile =
        PlayerProfile.fromMap(this["uid"] as? String ?: uid, normalizeFirestoreValues())

    private fun Map<*, *>.normalizeFirestoreValues(): Map<String, Any?> =
        entries.associate { (key, value) ->
            val name = key as? String
                ?: throw IllegalStateException("Profile field name was invalid.")
            name to when (value) {
                is Timestamp -> value.toDate().time
                else -> value
            }
        }

    private fun Map<*, *>.requiredString(key: String): String =
        this[key] as? String ?: throw IllegalStateException("Player search field '$key' was invalid.")

    private fun Map<*, *>.requiredNumber(key: String): Number =
        this[key] as? Number ?: throw IllegalStateException("Player search field '$key' was invalid.")

    private fun Exception.toUserFacingProfileError(): Exception {
        val firebaseCode = (this as? FirebaseFunctionsException)?.code
        val message = when (firebaseCode) {
            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                "That username was just claimed. Choose another and try again."
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Your session expired. Sign in again to manage your player profile."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "The profile service denied this request. Check that the trusted backend is configured."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "A Gamer ID could not be allocated right now. Try again shortly."
            FirebaseFunctionsException.Code.UNAVAILABLE ->
                "The profile service is unavailable. Check your connection and try again."
            else -> this.message?.takeIf(String::isNotBlank)
                ?: "The player profile request failed. Check your connection and try again."
        }
        return IllegalStateException(message, this)
    }

    private companion object {
        const val USERS_COLLECTION = "users"
    }
}
