package io.github.airdaydreamers.melddrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.discovery.DiscoveredServer
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import io.github.airdaydreamers.melddrive.ui.mvi.ServerType
import io.github.airdaydreamers.melddrive.ui.viewmodel.AddStorageViewModel

@Composable
fun AddStorageScreen(onBack: () -> Unit, onSuccess: () -> Unit, viewModel: AddStorageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onSuccess()
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
                title = {
                    val typeName = when (state.serverType) {
                        ServerType.WEBDAV -> stringResource(R.string.untranslatable_webdav)
                        ServerType.SMB -> stringResource(R.string.untranslatable_smb)
                    }
                    Text(stringResource(R.string.add_storage_title, typeName))
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
            )
        },
    ) { padding ->
        AddStorageForm(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun AddStorageForm(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DiscoveredServersSection(state, onIntent)

        AddStorageFields(state, onIntent)

        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("add_storage_error"))
        }

        AddStorageButtons(state, onIntent)
    }
}

@Composable
private fun DiscoveredServersSection(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.discovered_servers_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { onIntent(AddStorageIntent.StartDiscovery) },
                enabled = !state.isDiscovering,
                modifier = Modifier.testTag("scan_network_button"),
            ) {
                if (state.isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scanning_network))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_scan_network))
                }
            }
        }

        if (state.discoveredServers.isEmpty()) {
            Text(
                text = stringResource(R.string.no_servers_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.discoveredServers.forEach { server ->
                    DiscoveredServerItem(server = server, onSelect = {
                        onIntent(AddStorageIntent.SelectDiscoveredServer(server))
                    })
                }
            }
        }
    }
}

@Composable
private fun DiscoveredServerItem(server: DiscoveredServer, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("discovered_server_${server.host}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${server.type.name} • ${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddStorageFields(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit) {
    ServerTypeSelector(state.serverType, onIntent)

    OutlinedTextField(
        value = state.displayName,
        onValueChange = { onIntent(AddStorageIntent.DisplayNameChange(it)) },
        label = { Text(stringResource(R.string.label_display_name, stringResource(R.string.untranslatable_nas))) },
        modifier = Modifier.fillMaxWidth().testTag("display_name_input"),
    )

    OutlinedTextField(
        value = state.host,
        onValueChange = { onIntent(AddStorageIntent.HostChange(it)) },
        label = { Text(stringResource(R.string.label_host, stringResource(R.string.untranslatable_ip))) },
        modifier = Modifier.fillMaxWidth().testTag("host_input"),
    )

    OutlinedTextField(
        value = state.port,
        onValueChange = { onIntent(AddStorageIntent.PortChange(it)) },
        label = { Text(stringResource(R.string.label_port)) },
        modifier = Modifier.fillMaxWidth().testTag("port_input"),
    )

    AuthFields(state, onIntent)
}

@Composable
private fun ServerTypeSelector(serverType: ServerType, onIntent: (AddStorageIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = serverType == ServerType.SMB,
            onClick = { onIntent(AddStorageIntent.ServerTypeChange(ServerType.SMB)) },
            label = { Text(stringResource(R.string.untranslatable_smb)) },
            modifier = Modifier.testTag("server_type_smb"),
        )
        FilterChip(
            selected = serverType == ServerType.WEBDAV,
            onClick = { onIntent(AddStorageIntent.ServerTypeChange(ServerType.WEBDAV)) },
            label = { Text(stringResource(R.string.untranslatable_webdav)) },
            modifier = Modifier.testTag("server_type_webdav"),
        )
    }
}

@Composable
private fun AuthFields(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit) {
    if (state.serverType == ServerType.WEBDAV) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.trustSelfSigned,
                onCheckedChange = { onIntent(AddStorageIntent.TrustSelfSignedChange(it)) },
                modifier = Modifier.testTag("trust_self_signed_checkbox"),
            )
            Text(stringResource(R.string.trust_self_signed))
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.isAnonymous,
            onCheckedChange = { onIntent(AddStorageIntent.AnonymousChange(it)) },
            modifier = Modifier.testTag("anonymous_checkbox"),
        )
        Text(stringResource(R.string.anonymous_access))
    }

    if (!state.isAnonymous) {
        OutlinedTextField(
            value = state.username,
            onValueChange = { onIntent(AddStorageIntent.UsernameChange(it)) },
            label = { Text(stringResource(R.string.label_username)) },
            modifier = Modifier.fillMaxWidth().testTag("username_input"),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = { onIntent(AddStorageIntent.PasswordChange(it)) },
            label = { Text(stringResource(R.string.label_password)) },
            modifier = Modifier.fillMaxWidth().testTag("password_input"),
        )
    }
}

@Composable
private fun ColumnScope.AddStorageButtons(state: AddStorageState, onIntent: (AddStorageIntent) -> Unit) {
    Button(
        onClick = { onIntent(AddStorageIntent.SaveServer) },
        modifier = Modifier.align(Alignment.End).testTag("connect_save_button"),
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
        Text(stringResource(R.string.btn_connect_save))
    }
}
