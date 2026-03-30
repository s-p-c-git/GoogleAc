package com.googleac.feature.auth.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(
    onAuthSuccess: (accountId: String, email: String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.Success) {
            onAuthSuccess(state.accountId, state.email)
        }
    }

    // When the PKCE URL is ready, open it in a Chrome Custom Tab immediately.
    // The screen stays in the "loading" look while the tab is open.
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.AwaitingRedirect) {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(context, Uri.parse(state.authUrl))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "gShare",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Multi-Account Google Drive Manager",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))

        when (val state = uiState) {
            is AuthUiState.Setup -> {
                SetupClientIdSection(onSave = { viewModel.saveClientId(it) })
            }
            is AuthUiState.Idle -> {
                Button(
                    onClick = { viewModel.startAuth() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Google")
                }
            }
            is AuthUiState.AwaitingRedirect, is AuthUiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (uiState is AuthUiState.AwaitingRedirect)
                        "Opening Google sign-in…"
                    else
                        "Signing in…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AuthUiState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.startAuth() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
            }
            is AuthUiState.Success -> {
                Text("Signed in as ${state.email}")
            }
        }
    }
}

/**
 * One-time setup section that prompts the user to enter their Google OAuth
 * client ID when the app was built without one (e.g., a pre-built CI APK).
 *
 * The entered value is persisted via [AuthViewModel.saveClientId] so the user
 * only needs to do this once.
 */
@Composable
private fun SetupClientIdSection(onSave: (String) -> Unit) {
    var clientIdInput by remember { mutableStateOf("") }

    Text(
        text = "One-time setup required",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "This build was not packaged with a Google OAuth client ID.\n" +
               "Enter your Web/Android OAuth client ID below to enable sign-in.\n\n" +
               "Create one at:\nconsole.cloud.google.com → APIs & Services → Credentials",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = clientIdInput,
        onValueChange = { clientIdInput = it },
        label = { Text("OAuth Client ID") },
        placeholder = { Text("1234567890.apps.googleusercontent.com") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { onSave(clientIdInput.trim()) },
        enabled = clientIdInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save & Continue")
    }
}
