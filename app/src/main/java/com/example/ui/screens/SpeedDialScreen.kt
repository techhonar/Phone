package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.ContactEntity
import com.example.data.SpeedDialEntity
import com.example.rust.RustPhoneBridge
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed

private val SPEED_DIAL_T9_LABELS = mapOf(
    2 to "ABC",
    3 to "DEF",
    4 to "GHI",
    5 to "JKL",
    6 to "MNO",
    7 to "PQRS",
    8 to "TUV",
    9 to "WXYZ"
)

@Composable
fun SpeedDialScreen(
    viewModel: PhoneViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedDials by viewModel.speedDials.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val initialTargetDigit by viewModel.speedDialTargetDigit.collectAsState()

    SpeedDialContent(
        speedDials = speedDials,
        allContacts = allContacts,
        initialTargetDigit = initialTargetDigit,
        onClearInitialTargetDigit = { viewModel.clearSpeedDialTargetDigit() },
        onAssignContact = { digit, contact -> viewModel.assignSpeedDial(digit, contact) },
        onAssignCustomNumber = { digit, name, phone -> viewModel.assignCustomSpeedDial(digit, name, phone) },
        onRemoveSpeedDial = { digit -> viewModel.removeSpeedDial(digit) },
        onClearAllSpeedDials = { viewModel.clearAllSpeedDials() },
        onCallNumber = { phone -> viewModel.startCall(phone) },
        onCallVoicemail = { viewModel.callVoicemail() },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun SpeedDialContent(
    speedDials: List<SpeedDialEntity>,
    allContacts: List<ContactEntity>,
    initialTargetDigit: Int? = null,
    onClearInitialTargetDigit: () -> Unit = {},
    onAssignContact: (Int, ContactEntity) -> Unit,
    onAssignCustomNumber: (Int, String, String) -> Unit,
    onRemoveSpeedDial: (Int) -> Unit,
    onClearAllSpeedDials: () -> Unit,
    onCallNumber: (String) -> Unit,
    onCallVoicemail: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerDigit by remember { mutableStateOf(initialTargetDigit) }

    LaunchedEffect(initialTargetDigit) {
        if (initialTargetDigit != null && initialTargetDigit in 2..9) {
            pickerDigit = initialTargetDigit
        }
    }

    BackHandler {
        if (pickerDigit != null) {
            pickerDigit = null
            onClearInitialTargetDigit()
        } else {
            onBack()
        }
    }

    val byDigit = remember(speedDials) { speedDials.associateBy { it.keyDigit } }
    val contactsById = remember(allContacts) { allContacts.associateBy { it.id } }
    val digits = remember { (2..9).toList() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("speed_dial_screen")
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val useTwoColumns = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                // One UI Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .testTag("speed_dial_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.speed_dial_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.speed_dial_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SamsungGreen.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${speedDials.size}/8 active",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SamsungGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (speedDials.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = onClearAllSpeedDials,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("speed_dial_clear_all_button")
                        ) {
                            Text(
                                text = "Clear",
                                color = SamsungRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Instructional Tip + Key 1 Voicemail Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            SamsungGreen.copy(alpha = 0.14f),
                                            SamsungBlue.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(SamsungGreen, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "One-touch keypad calling",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Assign contacts to keys 2–9 below. Press and hold any assigned key on the Keypad to dial immediately.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Key 1 Reserved Voicemail Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCallVoicemail() }
                                .testTag("speed_dial_voicemail_slot")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(SamsungOrange.copy(alpha = 0.14f), CircleShape)
                                ) {
                                    Text(
                                        text = "1",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = SamsungOrange
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Voicemail",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Reserved system key • Long-press 1 on Keypad",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Voicemail,
                                    contentDescription = "Call Voicemail",
                                    tint = SamsungOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "ASSIGNABLE KEYS (2–9)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = SamsungGreen,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    if (useTwoColumns) {
                        items(digits.chunked(2), key = { row -> row.joinToString("_") }) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { digit ->
                                    val entry = byDigit[digit]
                                    val contact = entry?.let { contactsById[it.contactId] }
                                    SpeedDialSlotCard(
                                        digit = digit,
                                        t9Label = SPEED_DIAL_T9_LABELS[digit].orEmpty(),
                                        entry = entry,
                                        contact = contact,
                                        onOpenPicker = { pickerDigit = digit },
                                        onCall = { entry?.let { onCallNumber(it.phoneNumber) } },
                                        onRemove = { onRemoveSpeedDial(digit) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(digits, key = { it }) { digit ->
                            val entry = byDigit[digit]
                            val contact = entry?.let { contactsById[it.contactId] }
                            SpeedDialSlotCard(
                                digit = digit,
                                t9Label = SPEED_DIAL_T9_LABELS[digit].orEmpty(),
                                entry = entry,
                                contact = contact,
                                onOpenPicker = { pickerDigit = digit },
                                onCall = { entry?.let { onCallNumber(it.phoneNumber) } },
                                onRemove = { onRemoveSpeedDial(digit) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    pickerDigit?.let { targetDigit ->
        SpeedDialContactPickerDialog(
            digit = targetDigit,
            t9Label = SPEED_DIAL_T9_LABELS[targetDigit].orEmpty(),
            currentEntry = byDigit[targetDigit],
            contacts = allContacts,
            onDismiss = {
                pickerDigit = null
                onClearInitialTargetDigit()
            },
            onSelectContact = { contact ->
                onAssignContact(targetDigit, contact)
                pickerDigit = null
            },
            onSaveCustomNumber = { name, phone ->
                onAssignCustomNumber(targetDigit, name, phone)
                pickerDigit = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialSlotCard(
    digit: Int,
    t9Label: String,
    entry: SpeedDialEntity?,
    contact: ContactEntity?,
    onOpenPicker: () -> Unit,
    onCall: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (entry != null) 2.dp else 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onOpenPicker,
                onLongClick = { if (entry != null) onCall() }
            )
            .testTag("speed_dial_slot_$digit")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Key Badge (Digit + T9 Sub-letters)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (entry != null) SamsungGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Text(
                    text = digit.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = if (entry != null) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = t9Label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.6.sp
                    ),
                    color = if (entry != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (entry != null) {
                ContactAvatar(
                    name = entry.contactName,
                    photoUri = contact?.photoUri,
                    avatarColorHex = contact?.avatarColorHex ?: "#27C064",
                    size = 42
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.contactName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .testTag("speed_dial_call_$digit")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(SamsungGreen, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Quick call ${entry.contactName}",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenPicker,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .testTag("speed_dial_assign_$digit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change speed dial $digit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .testTag("speed_dial_remove_$digit")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove speed dial $digit",
                        tint = SamsungRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unassigned slot",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap to assign a contact to key $digit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenPicker,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .testTag("speed_dial_assign_$digit")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(SamsungGreen.copy(alpha = 0.14f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Assign key $digit",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialContactPickerDialog(
    digit: Int,
    t9Label: String,
    currentEntry: SpeedDialEntity?,
    contacts: List<ContactEntity>,
    onDismiss: () -> Unit,
    onSelectContact: (ContactEntity) -> Unit,
    onSaveCustomNumber: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCustomEntry by remember { mutableStateOf(contacts.isEmpty()) }
    var customName by remember { mutableStateOf("") }
    var customPhone by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            val q = searchQuery.trim().lowercase()
            val qDigits = RustPhoneBridge.normalizePhoneNumber(searchQuery)
            contacts.filter { contact ->
                contact.name.lowercase().contains(q) ||
                    contact.phoneNumber.lowercase().contains(q) ||
                    (qDigits.isNotEmpty() && RustPhoneBridge.normalizePhoneNumber(contact.phoneNumber).contains(qDigits))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speed_dial_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assign Key $digit ($t9Label)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (currentEntry != null) {
                            Text(
                                text = "Currently: ${currentEntry.contactName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SamsungGreen
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(48.dp, 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts or number") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("speed_dial_contact_search")
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredContacts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            val isCurrent = currentEntry?.contactId == contact.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isCurrent) SamsungGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectContact(contact) }
                                    .testTag("speed_dial_picker_contact_${contact.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(
                                        name = contact.name,
                                        photoUri = contact.photoUri,
                                        avatarColorHex = contact.avatarColorHex,
                                        size = 38
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    if (isCurrent) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = SamsungGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (contacts.isEmpty()) {
                                "No contacts saved yet. Enter a name and phone number below."
                            } else {
                                "No contacts match \"$searchQuery\"."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showCustomEntry = !showCustomEntry },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("speed_dial_custom_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showCustomEntry) "Hide quick number entry" else "Enter custom number")
                }

                AnimatedVisibility(visible = showCustomEntry) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Contact name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("speed_dial_custom_name_input")
                        )
                        OutlinedTextField(
                            value = customPhone,
                            onValueChange = { customPhone = it },
                            label = { Text("Phone number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("speed_dial_custom_phone_input")
                        )
                        Button(
                            onClick = { onSaveCustomNumber(customName, customPhone) },
                            enabled = customPhone.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = SamsungGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("speed_dial_custom_save_button")
                        ) {
                            Text("Save to Key $digit", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
