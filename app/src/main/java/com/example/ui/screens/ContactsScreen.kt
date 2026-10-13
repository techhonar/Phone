package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactEntity
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.GooglePhoneHeader
import com.example.ui.theme.GoogleMeetYellow
import com.example.ui.theme.GoogleSelectionBlue
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed

/**
 * Contacts, restyled after the reference video:
 * "Phone" header, an "Add your favorite contacts" card, a Groups card,
 * and contacts grouped by first letter into one large rounded card per
 * letter. Long-press enters multi-select with a floating Delete pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.filteredContacts.collectAsState()
    val favorites by viewModel.favoriteContacts.collectAsState()
    val searchQuery by viewModel.contactSearchQuery.collectAsState()
    val selectedContact by viewModel.selectedContactDetail.collectAsState()

    var searchVisible by remember { mutableStateOf(false) }
    var groupsExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val groupedContacts = remember(contacts) {
        contacts.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
            .toSortedMap()
    }

    val groupNames = remember(contacts) {
        contacts.groupBy { it.groupName }.toSortedMap()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        modifier = Modifier.testTag("contacts_selection_cancel")
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            } else {
                GooglePhoneHeader(
                    onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
                    onMenuSettings = { viewModel.openSettingsDialog() },
                    onAddContact = { viewModel.openCreateContact() },
                    onSearch = { searchVisible = !searchVisible }
                )
            }

        // Search Bar (shown from the header search icon, as in the video)
        AnimatedVisibility(visible = searchVisible || searchQuery.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setContactSearchQuery(it) },
                        placeholder = { Text("Search name or number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("contacts_search_input")
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setContactSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Favorites card (reference video: "Add your favorite contacts")
            if (searchQuery.isBlank() && !selectionMode) {
                item(key = "favorites_card") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column {
                            if (favorites.isEmpty()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = SamsungOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Add your favorite contacts",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = SamsungOrange
                                    )
                                }
                            } else {
                                favorites.forEachIndexed { index, contact ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                            modifier = Modifier.padding(start = 72.dp, end = 16.dp)
                                        )
                                    }
                                    ContactCardRow(
                                        contact = contact,
                                        selectionMode = false,
                                        isSelected = false,
                                        onTap = { viewModel.openContactDetail(contact) },
                                        onLongPress = {},
                                        modifier = Modifier.testTag("fav_contact_${contact.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Groups card (reference video)
                item(key = "groups_card") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { groupsExpanded = !groupsExpanded }
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .testTag("groups_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Groups",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (groupsExpanded) {
                                groupNames.forEach { (groupName, members) ->
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(start = 54.dp, end = 16.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(modifier = Modifier.width(38.dp))
                                        Text(
                                            text = groupName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = members.size.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Alphabetically grouped cards, one card per letter (reference video)
            groupedContacts.forEach { (initial, contactList) ->
                item(key = "header_$initial") {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
                    )
                }

                item(key = "card_$initial") {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            contactList.forEachIndexed { index, contact ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(start = 72.dp, end = 16.dp)
                                    )
                                }
                                ContactCardRow(
                                    contact = contact,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(contact.id),
                                    onTap = {
                                        if (selectionMode) {
                                            toggleSelection(contact.id)
                                        } else {
                                            viewModel.openContactDetail(contact)
                                        }
                                    },
                                    onLongPress = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = setOf(contact.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
        }

        // Floating Delete pill in selection mode (reference video)
        if (selectionMode && selectedIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clickable {
                        viewModel.deleteContacts(selectedIds)
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                    .testTag("contacts_delete_selected_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Contact Detail Bottom Sheet
    selectedContact?.let { contact ->
        ContactDetailSheet(
            contact = contact,
            onDismiss = { viewModel.closeContactDetail() },
            onCall = {
                viewModel.closeContactDetail()
                viewModel.startCall(contact.phoneNumber, contact.name)
            },
            onToggleFavorite = { viewModel.toggleFavoriteContact(contact) },
            onDelete = { viewModel.deleteContact(contact.id) }
        )
    }
}

@Composable
private fun ContactCardRow(
    contact: ContactEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(contact.id, selectionMode) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .testTag("contact_item_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isSelected) GoogleSelectionBlue else Color.Transparent, CircleShape)
                    .then(
                        if (isSelected) Modifier else Modifier.border(1.5.dp, Color.Gray, CircleShape)
                    )
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            ContactAvatar(name = contact.name, avatarColorHex = contact.avatarColorHex, size = 42)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailSheet(
    contact: ContactEntity,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            ContactAvatar(name = contact.name, avatarColorHex = contact.avatarColorHex, size = 76)
            Spacer(modifier = Modifier.height(12.dp))

            // Name
            Text(
                text = contact.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Phone
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Action Buttons Row (Call, Text, Video, Favorite),
            // using the flat round-button palette from the reference video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailActionCircle(
                    icon = Icons.Default.Call,
                    label = "Call",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = SamsungGreen,
                    onClick = onCall
                )
                DetailActionCircle(
                    icon = Icons.Default.Chat,
                    label = "Message",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = SamsungBlue,
                    onClick = { /* Simulated SMS */ }
                )
                DetailActionCircle(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    bgColor = Color.White,
                    iconTint = GoogleMeetYellow,
                    onClick = onCall
                )
                DetailActionCircle(
                    icon = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (contact.isFavorite) "Favorite" else "Add Fav",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = SamsungOrange,
                    onClick = onToggleFavorite
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Additional Details Card (Email, Notes, Group)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (contact.email.isNotEmpty()) {
                        Text(
                            text = "Email",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = contact.email,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = "Group",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = contact.groupName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (contact.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = contact.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete Contact Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SamsungRed.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDelete() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = SamsungRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Contact",
                        color = SamsungRed,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(bgColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
