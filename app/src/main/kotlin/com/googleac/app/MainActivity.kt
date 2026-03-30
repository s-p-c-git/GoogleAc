package com.googleac.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.googleac.app.navigation.AppNavHost
import com.googleac.core.ui.theme.GoogleAcTheme
import com.googleac.feature.auth.data.OAuthCallbackRouter
import com.googleac.feature.auth.data.OAuthPkceHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Routes the OAuth redirect URI to [com.googleac.feature.auth.ui.AuthViewModel]
     * without coupling the Activity directly to the ViewModel.
     */
    @Inject
    lateinit var oauthCallbackRouter: OAuthCallbackRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoogleAcTheme {
                AppNavHost()
            }
        }
        // Handle a redirect that launched the Activity cold (e.g., task not in back stack).
        handleOAuthRedirectIntent(intent)
    }

    /**
     * Called when Chrome Custom Tabs redirects back to the app while the Activity
     * is already running (single-task / single-top launch mode).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirectIntent(intent)
    }

    private fun handleOAuthRedirectIntent(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        if (uri.startsWith(OAuthPkceHelper.REDIRECT_URI)) {
            oauthCallbackRouter.onRedirectReceived(uri)
        }
    }
}

