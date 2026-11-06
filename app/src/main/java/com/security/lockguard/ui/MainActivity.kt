package com.security.lockguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.security.lockguard.applock.AppLockManager
import com.security.lockguard.applock.AppLockService
import com.security.lockguard.applock.InstalledAppItem
import com.security.lockguard.ml.FaceEmbeddingComparator
import com.security.lockguard.security.MasterPinCryptoManager
import com.security.lockguard.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var faceComparator: FaceEmbeddingComparator
    private lateinit var pinManager: MasterPinCryptoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockManager = AppLockManager(this)
        faceComparator = FaceEmbeddingComparator(this)
        pinManager = MasterPinCryptoManager(this)

        // Ensure AppLockService is active
        AppLockService.start(this)

        setContent {
            LockGuardTheme {
                MainDashboardScreen(
                    appLockManager = appLockManager,
                    faceComparator = faceComparator,
                    pinManager = pinManager,
                    onOpenFaceEnrollment = {
                        val intent = Intent(this, FaceEnrollmentActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLockService.start(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    appLockManager: AppLockManager,
    faceComparator: FaceEmbeddingComparator,
    pinManager: MasterPinCryptoManager,
    onOpenFaceEnrollment: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: App Lock, 1: Settings
    var appList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isUsageGranted by remember { mutableStateOf(appLockManager.isUsageAccessGranted()) }
    var isOverlayGranted by remember { mutableStateOf(appLockManager.isOverlayPermissionGranted()) }

    // Face & PIN State
    var isFaceEnrolled by remember { mutableStateOf(faceComparator.isFaceEnrolled()) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isUsageGranted = appLockManager.isUsageAccessGranted()
                isOverlayGranted = appLockManager.isOverlayPermissionGranted()
                isFaceEnrolled = faceComparator.isFaceEnrolled()
                if (isUsageGranted && isOverlayGranted) {
                    AppLockService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun refreshApps() {
        coroutineScope.launch {
            isLoadingApps = true
            val apps = withContext(Dispatchers.IO) {
                appLockManager.getInstalledLaunchableApps()
            }
            appList = apps
            isLoadingApps = false
        }
    }

    LaunchedEffect(Unit) {
        refreshApps()
        isFaceEnrolled = faceComparator.isFaceEnrolled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LOCKGUARD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardDark,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        isUsageGranted = appLockManager.isUsageAccessGranted()
                        isOverlayGranted = appLockManager.isOverlayPermissionGranted()
                    },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "App Locker") },
                    label = { Text("App Locker") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0x2600E5FF)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        isFaceEnrolled = faceComparator.isFaceEnrolled()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = Color(0x2600E5FF)
                    )
                )
            }
        },
        containerColor = BgDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedTab == 0) {
                // TAB 0: APP LOCKER
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // 1. Display Over Other Apps Permission Banner
                    if (!isOverlayGranted) {
                        Surface(
                            color = Color(0xFF2A1518),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Display Over Other Apps Required",
                                        color = CrimsonAlert,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "LockGuard needs 'Display over other apps' (Appear on top) to lock and pop up over protected apps.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Grant Overlay Permission", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // 2. Usage Stats Permission Banner if not granted
                    if (!isUsageGranted) {
                        Surface(
                            color = Color(0xFF2A1518),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Usage Access Required",
                                        color = CrimsonAlert,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To detect when locked apps open, please enable Usage Access for LockGuard.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Open Settings -> Usage Access -> LockGuard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Grant Usage Access", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // 3. Engine Active Status Badge if both are granted
                    if (isOverlayGranted && isUsageGranted) {
                        Surface(
                            color = Color(0xFF0F2624),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "App Lock Engine Active",
                                        color = CyanAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Monitoring locked apps. Biometric screen prompts immediately upon opening.",
                                        color = Color(0xFFA0D2DB),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Search and Stats Row
                    val lockedCount = appList.count { it.isLocked }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$lockedCount Apps Protected",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanAccent
                        )
                        IconButton(onClick = { refreshApps() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.LightGray)
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed applications...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0xFF2B3345),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    if (isLoadingApps) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyanAccent)
                        }
                    } else {
                        val filteredApps = appList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { appItem ->
                                AppLockRow(
                                    appItem = appItem,
                                    onToggleLock = {
                                        if (!isOverlayGranted || !isUsageGranted) {
                                            Toast.makeText(
                                                context,
                                                "⚠️ Please grant 'Display Over Other Apps' & 'Usage Access' above so LockGuard can intercept!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        val nowLocked = appLockManager.toggleAppLock(appItem.packageName)
                                        appList = appList.map {
                                            if (it.packageName == appItem.packageName) it.copy(isLocked = nowLocked) else it
                                        }
                                        AppLockService.start(context)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: SETTINGS (Face ID, Fingerprint, Master PIN)
                SettingsScreen(
                    isFaceEnrolled = isFaceEnrolled,
                    onChangeFace = onOpenFaceEnrollment,
                    onDeleteFace = {
                        faceComparator.clearEnrolledFace()
                        isFaceEnrolled = false
                        Toast.makeText(context, "Face ID removed successfully", Toast.LENGTH_SHORT).show()
                    },
                    onChangePin = { showChangePinDialog = true }
                )
            }
        }

        // Change Master PIN Dialog
        if (showChangePinDialog) {
            ChangePinDialog(
                pinManager = pinManager,
                onDismiss = { showChangePinDialog = false },
                onSuccess = {
                    showChangePinDialog = false
                    Toast.makeText(context, "Master Security PIN updated successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun AppLockRow(
    appItem: InstalledAppItem,
    onToggleLock: () -> Unit
) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B202E)),
                contentAlignment = Alignment.Center
            ) {
                if (appItem.icon != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                setImageDrawable(appItem.icon)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App Name & Package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = appItem.packageName,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            // Lock Toggle Button
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (appItem.isLocked) Color(0x3300E5FF) else Color(0xFF1B202E))
            ) {
                Icon(
                    imageVector = if (appItem.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (appItem.isLocked) "Locked" else "Unlocked",
                    tint = if (appItem.isLocked) CyanAccent else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isFaceEnrolled: Boolean,
    onChangeFace: () -> Unit,
    onDeleteFace: () -> Unit,
    onChangePin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "SECURITY SETTINGS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyanAccent,
            letterSpacing = 1.sp
        )

        // 1. Face ID Card
        Surface(color = CardDark, shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Face, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Face ID Biometrics", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = if (isFaceEnrolled) "Status: Enrolled (Active)" else "Status: Not Registered",
                                color = if (isFaceEnrolled) Color(0xFF00E676) else CrimsonAlert,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onChangeFace,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFaceEnrolled) "Change Face ID" else "Register Face ID", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (isFaceEnrolled) {
                        OutlinedButton(
                            onClick = onDeleteFace,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert)
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Fingerprint Card
        Surface(color = CardDark, shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFC39BD3), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Fingerprint Scanner", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Uses hardware sensor registered in phone settings", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Master PIN Card
        Surface(color = CardDark, shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Password, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Master Security PIN", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("PBKDF2-HMAC-SHA512 Encrypted 6-digit PIN", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onChangePin,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B202E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change 6-digit Master PIN", color = CyanAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // 4. Background Security Status
        Surface(color = CardDark, shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("App Lock Background Service", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Active & monitoring protected apps", color = Color(0xFF00E676), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    pinManager: MasterPinCryptoManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master PIN", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = CrimsonAlert, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 6) oldPin = it },
                    label = { Text("Current 6-digit PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6) newPin = it },
                    label = { Text("New 6-digit PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) confirmPin = it },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (oldPin.length != 6 || newPin.length != 6 || confirmPin.length != 6) {
                        errorMsg = "All PINs must be exactly 6 digits"
                        return@Button
                    }
                    if (!pinManager.verifyPin(oldPin)) {
                        errorMsg = "Current PIN is incorrect!"
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        errorMsg = "New PIN and Confirm PIN do not match!"
                        return@Button
                    }
                    pinManager.registerMasterPin(newPin)
                    onSuccess()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Update PIN", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = CardDark
    )
}

