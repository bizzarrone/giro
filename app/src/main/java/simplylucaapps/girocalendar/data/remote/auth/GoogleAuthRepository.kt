package simplylucaapps.girocalendar.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "GoogleAuthRepository"

/**
 * Wraps Credential Manager + Firebase Auth's Google provider for "real" cross-device identity.
 * Requires, from the Firebase console: the Google sign-in provider enabled, and this app's
 * SHA-1 fingerprint registered — until both are done, [isAvailable] is false and
 * the UI should hide/disable the "Accedi con Google" action rather than crash on a missing web
 * client id.
 */
class GoogleAuthRepository(private val auth: FirebaseAuth = Firebase.auth) {

    private val _lastError = MutableStateFlow<Throwable?>(null)

    /** The real exception from the last failed [signIn] attempt (not cancellation). */
    val lastError: StateFlow<Throwable?> = _lastError

    /** True once google-services.json contains a web OAuth client (i.e. Google sign-in is enabled in Firebase). */
    fun isAvailable(context: Context): Boolean = webClientId(context) != null

    /** Synchronous snapshot, for one-off checks outside the reactive [observeUser] flow. */
    val currentUser: FirebaseUser? get() = auth.currentUser?.takeUnless { it.isAnonymous }

    private fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId == 0) null else runCatching { context.getString(resId) }.getOrNull()
    }

    /** Non-anonymous signed-in user, or null. */
    fun observeUser(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.takeUnless { it.isAnonymous })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Launches the Credential Manager "Sign in with Google" flow (needs an Activity [context] for
     * its bottom-sheet UI) and completes the Firebase sign-in. Returns null on cancellation/failure
     * — callers should just leave the user where they were, not crash.
     */
    suspend fun signIn(context: Context): FirebaseUser? {
        _lastError.value = null
        val clientId = webClientId(context) ?: return null

        val bottomSheetOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
        var idToken = tryGetIdToken(context, bottomSheetOption)

        // Some Play Services versions/devices fail GetGoogleIdOption with a bare
        // NoCredentialException even though the account picker would otherwise work fine —
        // retry with the older button-flow option.
        if (idToken == null && _lastError.value is NoCredentialException) {
            _lastError.value = null
            val buttonFlowOption = GetSignInWithGoogleOption.Builder(clientId).build()
            idToken = tryGetIdToken(context, buttonFlowOption)
        }
        idToken ?: return null

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        return runCatching {
            if (current != null && current.isAnonymous) {
                current.linkWithCredential(firebaseCredential).await()
            } else {
                auth.signInWithCredential(firebaseCredential).await()
            }
        }.recoverCatching {
            auth.signInWithCredential(firebaseCredential).await()
        }.onFailure {
            Log.w(TAG, "Firebase sign-in with Google credential failed", it)
            _lastError.value = it
        }.getOrNull()?.user
    }

    private suspend fun tryGetIdToken(context: Context, option: CredentialOption): String? {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = CredentialManager.create(context).getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in attempt failed", e)
            if (e !is GetCredentialCancellationException) _lastError.value = e
            null
        }
    }

    fun signOut() {
        // toglie prima il token push dall'account, poi esce
        simplylucaapps.girocalendar.push.Push.unregister { auth.signOut() }
    }

    /**
     * Deletes the current Firebase Auth identity itself. Firebase requires a *recent* sign-in
     * for this; if it's been too long, this fails with FirebaseAuthRecentLoginRequiredException
     * and the caller should surface that rather than silently drop it.
     */
    suspend fun deleteAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.success(Unit)
        return runCatching { user.delete().await() }
            .map {}
            .onFailure { Log.w(TAG, "Failed to delete Firebase account", it) }
    }
}
