package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactEntity
import com.example.rust.RustPhoneBridge
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.OneUiHeader
import com.example.ui.components.PermissionEmptyStateCard
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: PhoneViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.filteredContacts.collectAsState()
    val favorites by viewModel.favoriteContacts.collectAsState()
    val searchQuery by viewModel.contactSearchQuery.collectAsState()
    val selectedContact by viewModel.selectedContactDetail.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()

    val blockedSet = remember(blockedNumbers) {
        blockedNumbers.map { RustPhoneBridge.normalizeForMatch(it.phoneNumber) }.toSet()
    }

    val groupedContacts = remember(contacts) {
        contacts.groupBy {
            val ch = it.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (ch in 'A'..'Z') ch else '#'
        }.toSortedMap()
    }

    // Track section start indices inside LazyColumn for instant A-Z scrubbing
    val letterIndices = remember(groupedContacts, favorites, searchQuery) {
        val map = mutableMapOf<Char, Int>()
        var idx = if (searchQuery.isBlank() && favorites.isNotEmpty()) 1 else 0
        groupedContacts.forEach { (letter, list) ->
            map[letter] = idx
            idx += 1 + list.size
        }
        map
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeScrubLetter by remember { mutableStateOf<Char?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OneUiHeader(
            title = "Contacts",
            subtitle = if (permissionStatus.hasReadContacts) "${contacts.size} contacts" else "Contacts access needed",
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onMenuStats = { viewModel.openCallStatsScreen() },
            onAddContact = { viewModel.openCreateContact() }
        )

        if (!permissionStatus.hasReadContacts && contacts.isEmpty()) {
            PermissionEmptyStateCard(
                icon = Icons.Default.Contacts,
                title = "Access Device Contacts",
                description = "Allow Contacts permission to browse, search with T9, favorite, and call your real address book contacts.",
                buttonText = "Allow Contacts Access",
                onGrantClick = onRequestPermissions
            )
            return
        }

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
                    contentDescription = "Search contacts",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setContactSearchQuery(it) },
                    placeholder = { Text("Search name, number, or email") },
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching contacts" else "No contacts found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 28.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (searchQuery.isBlank() && favorites.isNotEmpty()) {
                        item(key = "favorites_strip") {
                            Text(
                                text = "FAVORITES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = SamsungGreen,
                                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(favorites, key = { "fav_${it.id}" }) { contact ->
                                    FavoriteContactCard(
                                        contact = contact,
                                        onClick = { viewModel.openContactDetail(contact) },
                                        onCallClick = { viewModel.startCall(contact.phoneNumber) }
                                    )
                                }
                            }
                        }
                    }

                    groupedContacts.forEach { (initial, contactList) ->
                        item(key = "header_$initial") {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = SamsungGreen,
                                modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(contactList, key = { "${it.id}_${it.phoneNumber}" }) { contact ->
                            ContactRowItem(
                                contact = contact,
                                onClick = { viewModel.openContactDetail(contact) },
                                onCallClick = { viewModel.startCall(contact.phoneNumber) }
                            )
                        }
                    }
                }

                // A-Z Fast Scroller Rail
                val letters = remember(groupedContacts) { groupedContacts.keys.toList() }
                if (letters.size > 3) {
                    AlphabetFastScroller(
                        letters = letters,
                        onLetterSelected = { letter ->
                            activeScrubLetter = letter
                            letterIndices[letter]?.let { targetIdx ->
                                scope.launch { listState.scrollToItem(targetIdx) }
                            }
                        },
                        onScrubEnd = { activeScrubLetter = null },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp, top = 12.dp, bottom = 12.dp)
                    )
                }

                // Floating Scrub Letter Bubble
                activeScrubLetter?.let { ch ->
                    Surface(
                        shape = CircleShape,
                        color = SamsungGreen,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = ch.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedContact?.let { contact ->
        val isBlocked = RustPhoneBridge.normalizeForMatch(contact.phoneNumber).let {
            it.isNotEmpty() && it in blockedSet
        }
        ContactDetailSheet(
            contact = contact,
            isBlocked = isBlocked,
            onDismiss = { viewModel.closeContactDetail() },
            onCall = {
                viewModel.closeContactDetail()
                viewModel.startCall(contact.phoneNumber)
            },
            onMessage = {
                viewModel.closeContactDetail()
                viewModel.sendMessage(contact.phoneNumber)
            },
            onEmail = { viewModel.sendEmail(contact.email) },
            onToggleFavorite = { viewModel.toggleFavoriteContact(contact) },
            onToggleBlock = {
                if (isBlocked) viewModel.unblockNumber(contact.phoneNumber)
                else viewModel.blockNumber(contact.phoneNumber, contact.name)
            },
            onDelete = { viewModel.deleteContact(contact) }
        )
    }
}

@Composable
private fun AlphabetFastScroller(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var totalHeightPx by remember { mutableStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { totalHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() }
                ) { change, _ ->
                    change.consume()
                    val fraction = (change.position.y / totalHeightPx.toFloat()).coerceIn(0f, 0.999f)
                    val index = (fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    onLetterSelected(letters[index])
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable {
                        onLetterSelected(letter)
                        onScrubEnd()
                    }
                    .padding(vertical = 1.dp)
            )
        }
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .width(110.dp)
            .clickable { onClick() }
            .testTag("fav_contact_${contact.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                avatarColorHex = contact.avatarColorHex,
                size = 50
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(34.dp)
                    .background(SamsungGreen.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call ${contact.name}",
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
        shape = RoundedCornerShape(18.dp),
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
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                avatarColorHex = contact.avatarColorHex,
                size = 44
            )
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${contact.phoneLabel} • ${contact.phoneNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .defaultMinSize(48.dp, 48.dp)
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
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEmail: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleBlock: () -> Unit,
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
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                avatarColorHex = contact.avatarColorHex,
                size = 82
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = contact.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${contact.phoneLabel} • ${contact.phoneNumber}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(22.dp))

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
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Message",
                    bgColor = SamsungBlue,
                    iconTint = Color.White,
                    onClick = onMessage
                )
                DetailActionCircle(
                    icon = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (contact.isFavorite) "Starred" else "Favorite",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = if (contact.isFavorite) SamsungOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onToggleFavorite
                )
                DetailActionCircle(
                    icon = Icons.Default.Block,
                    label = if (isBlocked) "Unblock" else "Block",
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTint = if (isBlocked) SamsungRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onToggleBlock
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (contact.email.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEmail() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = SamsungBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
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
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "Phone Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = contact.phoneLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (contact.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
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

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SamsungRed.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDelete() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 13.dp),
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
    icon: ImageVector,
    label: String,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .clickable { onClick() }
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
                modifier = Modifier.size(25.dp)
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
