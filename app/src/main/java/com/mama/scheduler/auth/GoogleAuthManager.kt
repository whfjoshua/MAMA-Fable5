package com.mama.scheduler.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds Google Sign-In state, shared between MainActivity and ViewModels. */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    }

    private val client: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CALENDAR_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private val _account = MutableStateFlow(GoogleSignIn.getLastSignedInAccount(context))
    val account: StateFlow<GoogleSignInAccount?> = _account.asStateFlow()

    val signInIntent: Intent
        get() = client.signInIntent

    fun onSignInResult(account: GoogleSignInAccount?) {
        _account.value = account
    }

    fun signOut(onComplete: () -> Unit = {}) {
        client.signOut().addOnCompleteListener {
            _account.value = null
            onComplete()
        }
    }
}
