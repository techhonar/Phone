package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactEntity
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.OneUiHeader
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed

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

    val groupedContacts = remember(contacts) {
        contacts.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
            .toSortedMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // One UI Header
        OneUiHeader(
            title = "Contacts",
            subtitle = "${contacts.size} contacts",
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onAddContact = { viewModel.openCreateContact() }
        )

        // Search Bar
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            // Favorites Section (if no active search filter)
            if (searchQuery.isBlank() && favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "FAVORITES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SamsungGreen,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                    ) {
                        items(favorites, key = { "fav_${it.id}" }) { contact ->
                            FavoriteContactCard(
                                contact = contact,
                                onClick = { viewModel.openContactDetail(contact) },
                                onCallClick = { viewModel.startCall(contact.phoneNumber, contact.name) }
                            )
                        }
                    }
                }
            }

            // Alphabetically grouped list
            groupedContacts.forEach { (initial, contactList) ->
                item(key = "header_$initial") {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                }

                items(contactList, key = { it.id }) { contact ->
                    ContactRowItem(
                        contact = contact,
                        onClick = { viewModel.openContactDetail(contact) },
                        onCallClick = { viewModel.startCall(contact.phoneNumber, contact.name) }
                    )
                }
            }
        }
    }

    // Samsung Contact Detail Bottom Sheet
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
private fun FavoriteContactCard(
    contact: ContactEntity,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .width(108.dp)
            .clickable { onClick() }
            .testTag("fav_contact_${contact.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactAvatar(name = contact.name, avatarColorHex = contact.avatarColorHex, size = 48)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(32.dp)
                    .background(SamsungGreen.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = SamsungGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactRowItem(
    contact: ContactEntity,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() }
            .testTag("contact_item_${contact.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, avatarColorHex = contact.avatarColorHex, size = 42)
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(SamsungGreen.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call ${contact.name}",
                    tint = SamsungGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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

            // Quick Action Buttons Row (Call, Text, Video, Favorite)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailActionCircle(
                    icon = Icons.Default.Call,
                    label = "Call",
                    bgColor = SamsungGreen,
                    iconTint = Color.White,
                    onClick = onCall
                )
                DetailActionCircle(
                    icon = Icons.Default.Chat,
                    label = "Message",
                    bgColor = SamsungBlue,
                    iconTint = Color.White,
                    onClick = { /* Simulated SMS */ }
                )
                DetailActionCircle(
                    icon = Icons.Default.Videocam,
                    label = "Video",
                    bgColor = SamsungOrange,
                    iconTint = Color.White,
                    onClick = onCall
                )
                DetailActionCircle(
                    icon = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (contact.isFavorite) "Favorite" else "Add Fav",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = if (contact.isFavorite) SamsungOrange else MaterialTheme.colorScheme.onSurfaceVariant,
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
