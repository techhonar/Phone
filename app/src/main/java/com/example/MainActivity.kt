package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ActiveCallState
import com.example.ui.PhoneTab
import com.example.ui.PhoneViewModel
import com.example.ui.components.SamsungBottomNavBar
import com.example.ui.dialogs.CreateContactDialog
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.dialogs.SpeedDialDialog
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.InCallScreen
import com.example.ui.screens.KeypadScreen
import com.example.ui.screens.PlacesScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.theme.SamsungPhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SamsungPhoneTheme {
                val viewModel: PhoneViewModel = viewModel()
                SamsungPhoneApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SamsungPhoneApp(viewModel: PhoneViewModel) {
    val callState by viewModel.callState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val missedLogs by viewModel.missedCallLogs.collectAsState()

    val showCreateContact by viewModel.showCreateContactDialog.collectAsState()
    val prefilledNumber by viewModel.prefilledNumberForContact.collectAsState()
    val showSpeedDial by viewModel.showSpeedDialDialog.collectAsState()
    val showSettings by viewModel.showSettingsDialog.collectAsState()

    // Handle back button on secondary tabs
    BackHandler(enabled = selectedTab != PhoneTab.KEYPAD && callState is ActiveCallState.Idle) {
        viewModel.selectTab(PhoneTab.KEYPAD)
    }

    if (callState !is ActiveCallState.Idle) {
        // Fullscreen In-Call Screen
        InCallScreen(
            callState = callState,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Scaffold(
            bottomBar = {
                SamsungBottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    missedCallsCount = missedLogs.size,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            contentWindowInsets = WindowInsets.statusBars,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        PhoneTab.KEYPAD -> KeypadScreen(viewModel = viewModel)
                        PhoneTab.RECENTS -> RecentsScreen(viewModel = viewModel)
                        PhoneTab.CONTACTS -> ContactsScreen(viewModel = viewModel)
                        PhoneTab.PLACES -> PlacesScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showCreateContact) {
        CreateContactDialog(
            initialNumber = prefilledNumber,
            onDismiss = { viewModel.closeCreateContact() },
            onSave = { name, number, email, group, notes ->
                viewModel.saveContact(name, number, email, group, notes)
            }
        )
    }

    if (showSpeedDial) {
        SpeedDialDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSpeedDialDialog() }
        )
    }

    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSettingsDialog() }
        )
    }
}
