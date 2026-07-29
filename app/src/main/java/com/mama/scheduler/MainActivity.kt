package com.mama.scheduler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.mama.scheduler.auth.GoogleAuthManager
import com.mama.scheduler.data.prefs.SettingsRepository
import com.mama.scheduler.ui.MamaApp
import com.mama.scheduler.ui.theme.MamaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: GoogleAuthManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            authManager.onSignInResult(task.getResult(ApiException::class.java))
        } catch (e: ApiException) {
            authManager.onSignInResult(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val dynamicColor by settingsRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            MamaTheme(dynamicColor = dynamicColor) {
                MamaApp(
                    onGoogleSignIn = { googleSignInLauncher.launch(authManager.signInIntent) }
                )
            }
        }
    }
}
