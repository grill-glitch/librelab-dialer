package org.librelab.dialer.ui.contactinfo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ContactInfoActivity — displays a contact's info and actions.
 * Launched from call log, contacts list, or notification.
 */
@AndroidEntryPoint
class ContactInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ContactInfoScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

data class ContactInfoState(
    val id: Long = 0,
    val lookupKey: String = "",
    val displayName: String = "",
    val photoUri: Uri? = null,
    val phoneNumbers: List<PhoneItem> = emptyList(),
    val emails: List<EmailItem> = emptyList(),
)

data class PhoneItem(val number: String, val type: Int, val label: String)
data class EmailItem(val address: String, val type: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.displayName.ifEmpty { "联系人" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5C6BC0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.photoUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(state.photoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Contact photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    state.phoneNumbers.firstOrNull()?.let {
                        Text(
                            text = it.number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionCol(
                        icon = Icons.Filled.Call,
                        label = "拨号",
                        onClick = {
                            state.phoneNumbers.firstOrNull()?.let { phone ->
                                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${phone.number}")
                                })
                            }
                        },
                    )
                    ActionCol(
                        icon = Icons.AutoMirrored.Outlined.Message,
                        label = "短信",
                        onClick = {
                            state.phoneNumbers.firstOrNull()?.let { phone ->
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:${phone.number}")
                                })
                            }
                        },
                    )
                    ActionCol(
                        icon = Icons.Filled.Videocam,
                        label = "视频",
                        onClick = {
                            state.phoneNumbers.firstOrNull()?.let { phone ->
                                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${phone.number}")
                                })
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.phoneNumbers.isNotEmpty()) {
                item { SectionTitle("电话号码") }
                state.phoneNumbers.forEach { phone ->
                    item {
                        ContactDetailRow(
                            label = phone.label,
                            value = phone.number,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${phone.number}")
                                })
                            },
                        )
                    }
                }
            }

            if (state.emails.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("电子邮件")
                }
                state.emails.forEach { email ->
                    item {
                        val label = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                            context.resources, email.type, ""
                        ).toString()
                        ContactDetailRow(
                            label = label,
                            value = email.address,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${email.address}")
                                })
                            },
                        )
                    }
                }
            }

            if (state.displayName.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到联系人信息",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCol(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ContactDetailRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@HiltViewModel
class ContactInfoViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactInfoState())
    val state: StateFlow<ContactInfoState> = _state

    init {
        val lookupKey = savedStateHandle.get<String>("lookupKey")
        val contactUri = savedStateHandle.get<String>("contactUri")?.let {
            try { Uri.parse(it) } catch (_: Exception) { null }
        }
        when {
            lookupKey != null -> loadByLookupKey(lookupKey)
            contactUri != null -> loadByUri(contactUri)
        }
    }

    private fun loadByLookupKey(lookupKey: String) {
        val cr = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(lookupKey).build()
        cr.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val photo = if (photoIdx >= 0) cursor.getString(photoIdx)?.let { Uri.parse(it) } else null

                val phones = loadPhones(cr, id)
                val emails = loadEmails(cr, id)
                _state.value = ContactInfoState(
                    id = id,
                    lookupKey = lookupKey,
                    displayName = name,
                    photoUri = photo,
                    phoneNumbers = phones,
                    emails = emails,
                )
            }
        }
    }

    private fun loadByUri(uri: Uri) {
        val cr = context.contentResolver
        cr.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val photo = if (photoIdx >= 0) cursor.getString(photoIdx)?.let { Uri.parse(it) } else null
                val phones = loadPhones(cr, id)
                val emails = loadEmails(cr, id)
                _state.value = ContactInfoState(
                    id = id,
                    lookupKey = "",
                    displayName = name,
                    photoUri = photo,
                    phoneNumbers = phones,
                    emails = emails,
                )
            }
        }
    }

    private fun loadPhones(cr: android.content.ContentResolver, contactId: Long): List<PhoneItem> {
        val phones = mutableListOf<PhoneItem>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) ?: "" else ""
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, type, customLabel
                ).toString()
                phones.add(PhoneItem(number, type, label))
            }
        }
        return phones
    }

    private fun loadEmails(cr: android.content.ContentResolver, contactId: Long): List<EmailItem> {
        val emails = mutableListOf<EmailItem>()
        cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val addrIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
                val addr = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else ""
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                emails.add(EmailItem(addr, type))
            }
        }
        return emails
    }
}
