package io.github.airdaydreamers.melddrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageEffect
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import io.github.airdaydreamers.melddrive.ui.viewmodel.AddStorageViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AddStorageScreen(viewModel: AddStorageViewModel, onBack: () -> Unit, onSuccess: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                AddStorageEffect.NavigateBack -> onSuccess()
            }
        }
    }

    AddStorageContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStorageContent(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add SMB Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { onIntent(AddStorageIntent.DisplayNameChange(it)) },
                label = { Text("Display Name (e.g. Home NAS)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.host,
                onValueChange = { onIntent(AddStorageIntent.HostChange(it)) },
                label = { Text("Host Address (IP or hostname)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.port,
                onValueChange = { onIntent(AddStorageIntent.PortChange(it)) },
                label = { Text("Port (default 445)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isAnonymous,
                    onCheckedChange = { onIntent(AddStorageIntent.AnonymousChange(it)) },
                )
                Text("Anonymous Access")
            }

            if (!state.isAnonymous) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { onIntent(AddStorageIntent.UsernameChange(it)) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onIntent(AddStorageIntent.PasswordChange(it)) },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { onIntent(AddStorageIntent.SaveServer) },
                modifier = Modifier.align(Alignment.End),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Connect & Save")
            }
        }
    }
}
