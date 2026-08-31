package org.librelab.dialer.ui.voicemail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librelab.dialer.data.voicemail.VoicemailEntry
import org.librelab.dialer.data.voicemail.VoicemailRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Voicemail screen — replaces VoicemailFragment.java.
 */

@HiltViewModel
class VoicemailViewModel @Inject constructor(
    private val repository: VoicemailRepository,
) : ViewModel() {

    private val _voicemails = MutableStateFlow<List<VoicemailEntry>>(emptyList())
    val voicemails: StateFlow<List<VoicemailEntry>> = _voicemails.asStateFlow()

    init {
        loadVoicemails()
    }

    fun loadVoicemails() {
        viewModelScope.launch {
            _voicemails.value = repository.getVoicemails()
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            repository.markAsRead(id)
            loadVoicemails()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteVoicemail(id)
            loadVoicemails()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(
    viewModel: VoicemailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voicemails by viewModel.voicemails.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Voicemail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (voicemails.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Voicemail,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No voicemails",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(voicemails, key = { it.id }) { vm ->
                    VoicemailItem(
                        entry = vm,
                        onClick = { viewModel.markAsRead(vm.id) },
                        onDelete = { viewModel.delete(vm.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoicemailItem(
    entry: VoicemailEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Voicemail,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName.takeIf { it.isNotEmpty() } ?: entry.number,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.isRead) FontWeight.Normal else FontWeight.Bold,
            )
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun formatTimestamp(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(timestamp))
}
