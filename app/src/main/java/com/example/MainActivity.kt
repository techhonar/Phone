package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telecom.CallManager
import com.example.ui.PhoneTab
import com.example.ui.PhoneViewModel
import com.example.ui.components.SamsungBottomNavBar
import com.example.ui.dialogs.CreateContactDialog
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.screens.CallStatsScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.InCallScreen
import com.example.ui.screens.KeypadScreen
import com.example.ui.screens.PlacesScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.screens.SpeedDialScreen
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungPhoneTheme

class MainActivity : ComponentActivity() {

    private val phoneViewModel: PhoneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDialIntent(intent)

        setContent {
            SamsungPhoneTheme {
                SamsungPhoneApp(viewModel = phoneViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        phoneViewModel.refreshSystemState()
    }

    private fun handleDialIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data
        if ((action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) && data?.scheme == "tel") {
            val number = data.schemeSpecificPart.orEmpty()
            if (number.isNotBlank()) {
                phoneViewModel.setDialedNumber(number)
                phoneViewModel.selectTab(PhoneTab.KEYPAD)
            }
        } else if (action == Intent.ACTION_CALL_BUTTON) {
            phoneViewModel.selectTab(PhoneTab.KEYPAD)
        }
    }
}

@Composable
fun SamsungPhoneApp(viewModel: PhoneViewModel) {
    val context = LocalContext.current
    val telecomState by viewModel.telecomState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val missedLogs by viewModel.missedCallLogs.collectAsState()

    val showCreateContact by viewModel.showCreateContactDialog.collectAsState()
    val prefilledNumber by viewModel.prefilledNumberForContact.collectAsState()
    val showSpeedDial by viewModel.showSpeedDialScreen.collectAsState()
    val showCallStats by viewModel.showCallStatsScreen.collectAsState()
    val showSettings by viewModel.showSettingsDialog.collectAsState()
    val ussdDialogMessage by viewModel.ussdDialogMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var requestedInitialPermissions by rememberSaveable { mutableStateOf(false) }

    val corePermissions = remember {
        buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSystemState()
    }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemState()
    }

    val requestPermissionsAction = {
        permissionsLauncher.launch(corePermissions)
    }

    val requestDefaultDialerAction = {
        val intent = CallManager.createDefaultDialerIntent(context)
        if (intent != null) {
            defaultDialerLauncher.launch(intent)
        }
    }

    // Request real telephony permissions smoothly on first launch
    LaunchedEffect(Unit) {
        if (!requestedInitialPermissions) {
            requestedInitialPermissions = true
            permissionsLauncher.launch(corePermissions)
        }
    }

    // Collect transient call/system feedback messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var isMinimizedToDialer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(telecomState.calls.isEmpty()) {
        if (telecomState.calls.isEmpty()) {
            isMinimizedToDialer = false
        }
    }

    // Back button returns from secondary tabs to Keypad
    BackHandler(enabled = !showSpeedDial && !showCallStats && selectedTab != PhoneTab.KEYPAD && (telecomState.calls.isEmpty() || isMinimizedToDialer)) {
        viewModel.selectTab(PhoneTab.KEYPAD)
    }

    if (telecomState.calls.isNotEmpty() && !isMinimizedToDialer) {
        // Real active/incoming Telecom / ConnectionService call UI
        InCallScreen(
            telecomState = telecomState,
            onAddCall = {
                viewModel.selectTab(PhoneTab.KEYPAD)
                isMinimizedToDialer = true
            },
            modifier = Modifier.fillMaxSize()
        )
    } else if (showSpeedDial) {
        SpeedDialScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeSpeedDialDialog() },
            modifier = Modifier.fillMaxSize()
        )
    } else if (showCallStats) {
        CallStatsScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeCallStatsScreen() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        SamsungBottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            missedCallsCount = missedLogs.size,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                },
                contentWindowInsets = WindowInsets.statusBars,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    telecomState.primaryCall?.let { activeCall ->
                        Surface(
                            color = SamsungGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isMinimizedToDialer = false }
                                .testTag("return_to_call_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Return to active call",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ongoing call (${activeCall.displayName ?: activeCall.number}) • Tap to return",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (useNavigationRail) {
                            SamsungNavigationRail(
                                selectedTab = selectedTab,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
                                when (tab) {
                                    PhoneTab.KEYPAD -> KeypadScreen(
                                        viewModel = viewModel,
                                        onRequestDefaultDialer = requestDefaultDialerAction,
                                        onRequestPermissions = requestPermissionsAction
                                    )
                                    PhoneTab.RECENTS -> RecentsScreen(
                                        viewModel = viewModel,
                                        onRequestPermissions = requestPermissionsAction
                                    )
                                    PhoneTab.CONTACTS -> ContactsScreen(
                                        viewModel = viewModel,
                                        onRequestPermissions = requestPermissionsAction
                                    )
                                    PhoneTab.PLACES -> PlacesScreen(
                                        viewModel = viewModel,
                                        onRequestDefaultDialer = requestDefaultDialerAction
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateContact) {
        CreateContactDialog(
            initialNumber = prefilledNumber,
            onDismiss = { viewModel.closeCreateContact() },
            onSave = { name, number, email, phoneLabel, notes ->
                viewModel.saveContact(name, number, email, phoneLabel, notes)
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onRequestDefaultDialer = requestDefaultDialerAction,
            onRequestPermissions = requestPermissionsAction,
            onDismiss = { viewModel.closeSettingsDialog() }
        )
    }

    ussdDialogMessage?.let { (code, response) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUssdDialog() },
            title = { Text("Carrier USSD ($code)") },
            text = { Text(response) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUssdDialog() }) {
                    Text("OK", color = SamsungGreen)
                }
            }
        )
    }
}

@Composable
private fun SamsungNavigationRail(
    selectedTab: PhoneTab,
    onTabSelected: (PhoneTab) -> Unit
) {
    val items = listOf(
        Triple(PhoneTab.KEYPAD, "Keypad", Pair(Icons.Default.Dialpad, Icons.Outlined.Dialpad)),
        Triple(PhoneTab.RECENTS, "Recents", Pair(Icons.Default.Schedule, Icons.Outlined.Schedule)),
        Triple(PhoneTab.CONTACTS, "Contacts", Pair(Icons.Default.Person, Icons.Outlined.Person)),
        Triple(PhoneTab.PLACES, "Quick", Pair(Icons.Default.Place, Icons.Outlined.Place))
    )
    NavigationRail {
        items.forEach { (tab, label, icons) ->
            val selected = selectedTab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) icons.first else icons.second,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = SamsungGreen,
                    selectedTextColor = SamsungGreen
                )
            )
        }
    }
}
