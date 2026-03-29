package com.googleac.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.googleac.core.data.db.entity.AccountFeature

/**
 * Shown immediately after a new account authenticates.
 *
 * Lets the user opt in to any combination of [AccountFeature]s for this account.
 * Tapping **Continue** persists the selection (even an empty one) and navigates
 * to the main screen.  Tapping **Skip for now** saves an empty selection — features
 * can be enabled later from account settings.
 */
@Composable
fun FeatureEnablementScreen(
    onDone: () -> Unit,
    viewModel: FeatureEnablementViewModel = hiltViewModel()
) {
    val enabledFeatures by viewModel.enabledFeatures.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Enable features",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = viewModel.email,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose which Google services to connect for this account. You can update these at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(AccountFeature.entries) { feature ->
                    FeatureRow(
                        feature = feature,
                        isEnabled = feature in enabledFeatures,
                        onToggle = { viewModel.toggleFeature(feature) }
                    )
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // "Enable All" shortcut — useful for power users
            OutlinedButton(
                onClick = { viewModel.enableAllFeatures() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && enabledFeatures.size < AccountFeature.entries.size
            ) {
                Text("Enable all features")
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.saveAndContinue(onDone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text("Continue")
            }

            TextButton(
                onClick = { viewModel.skipAndContinue(onDone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(
                    "Skip for now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    feature: AccountFeature,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = feature.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = featureIcon(feature),
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    )
}

private fun featureIcon(feature: AccountFeature): ImageVector = when (feature) {
    AccountFeature.DRIVE -> Icons.Default.CloudQueue
    AccountFeature.CALENDAR -> Icons.Default.CalendarMonth
    AccountFeature.TASKS -> Icons.Default.TaskAlt
    AccountFeature.AI_SUMMARIZER -> Icons.Default.AutoAwesome
}
