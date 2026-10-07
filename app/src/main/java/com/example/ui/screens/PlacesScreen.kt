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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PhoneViewModel
import com.example.ui.components.OneUiHeader
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed

data class PlaceServiceItem(
    val name: String,
    val category: String,
    val phone: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun PlacesScreen(
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    val speedDials by viewModel.speedDials.collectAsState()

    val quickServices = listOf(
        PlaceServiceItem("Emergency Services", "Urgent Assistance", "911", Icons.Default.Emergency, SamsungRed),
        PlaceServiceItem("City Hospital Clinic", "Healthcare & Urgent Care", "+1 (555) 890-1234", Icons.Default.LocalHospital, SamsungBlue),
        PlaceServiceItem("Express Pharmacy 24/7", "Prescriptions & Care", "+1 (555) 777-3344", Icons.Default.LocalPharmacy, SamsungGreen),
        PlaceServiceItem("Tony's Pizzeria", "Food Delivery", "+1 (555) 789-0123", Icons.Default.LocalPizza, SamsungOrange),
        PlaceServiceItem("City Cab & Rideshare", "Transportation", "+1 (555) 444-8899", Icons.Default.LocalTaxi, SamsungOrange),
        PlaceServiceItem("Mobile Carrier Support", "Account & Tech Support", "611", Icons.Default.SupportAgent, SamsungBlue)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // One UI Header
        OneUiHeader(
            title = "Places & Speed Dial",
            subtitle = "Quick directory & speed keys",
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            // Speed Dial section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEED DIAL KEYS (2-9)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SamsungGreen
                    )
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SamsungBlue,
                        modifier = Modifier
                            .clickable { viewModel.openSpeedDialDialog() }
                            .padding(4.dp)
                    )
                }

                if (speedDials.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.openSpeedDialDialog() }
                    ) {
                        Text(
                            text = "Tap to assign contacts to keys 2 through 9",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        speedDials.forEach { sd ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.startCall(sd.phoneNumber, sd.contactName) }
                                    .testTag("speed_dial_item_${sd.keyDigit}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(SamsungGreen.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Text(
                                            text = sd.keyDigit.toString(),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = SamsungGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sd.contactName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = sd.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.startCall(sd.phoneNumber, sd.contactName) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(SamsungGreen, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Call",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Places & Directory
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "LOCAL DIRECTORY & PLACES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = SamsungGreen,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            items(quickServices) { service ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.startCall(service.phone, service.name) }
                        .testTag("place_service_${service.phone}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .background(service.color.copy(alpha = 0.14f), CircleShape)
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = null,
                                tint = service.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = service.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "${service.category} • ${service.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.startCall(service.phone, service.name) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SamsungGreen.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                tint = SamsungGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
