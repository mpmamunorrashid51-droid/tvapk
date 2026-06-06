package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Base64
import com.example.ui.theme.Typography
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create the push notification channel
        createNotificationChannel()

        val channelIdExtra = intent?.getStringExtra("channel_id")

        setContent {
            val context = LocalContext.current
            val prefs = remember { KhelaPrefs(context) }
            val appViewModel: AppViewModel = viewModel(factory = AppViewModelFactory(prefs))

            // Play match directly if launched from notification deep link
            LaunchedEffect(channelIdExtra) {
                if (!channelIdExtra.isNullOrEmpty()) {
                    appViewModel.openChannelFromNotification(channelIdExtra)
                }
            }

            val appNameState by appViewModel.appName.collectAsState()
            val themePresetState by appViewModel.themePreset.collectAsState()

            KhelaTheme(preset = themePresetState) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        KhelaAppNavigation(appViewModel)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Khela365 Live Alerts"
            val descriptionText = "Broadcast alerts for cricket matches, footbal, and news"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("khela365_alerts", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// ----------------------------------------------------------------------------
// VIEWMODEL & THEMATIC CONTROLLERS
// ----------------------------------------------------------------------------

class AppViewModelFactory(private val prefs: KhelaPrefs) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

enum class Screen {
    SPLASH,
    HOME,
    PLAYER,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD
}

enum class NavigationTab {
    LIVE_HUB,
    CATEGORIES,
    SHIELD_PASS
}

class AppViewModel(val prefs: KhelaPrefs) : ViewModel() {
    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen

    private val _currentTab = MutableStateFlow(NavigationTab.LIVE_HUB)
    val currentTab: StateFlow<NavigationTab> = _currentTab

    private val _appName = MutableStateFlow(prefs.appName)
    val appName: StateFlow<String> = _appName

    private val _themePreset = MutableStateFlow(prefs.themePreset)
    val themePreset: StateFlow<String> = _themePreset

    private val _channels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val channels: StateFlow<List<LiveChannel>> = _channels

    private val _selectedChannel = MutableStateFlow<LiveChannel?>(null)
    val selectedChannel: StateFlow<LiveChannel?> = _selectedChannel

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    // AD Toggles
    private val _isBannerAdEnabled = MutableStateFlow(prefs.isBannerAdEnabled)
    val isBannerAdEnabled: StateFlow<Boolean> = _isBannerAdEnabled

    private val _isPopunderAdEnabled = MutableStateFlow(prefs.isPopunderAdEnabled)
    val isPopunderAdEnabled: StateFlow<Boolean> = _isPopunderAdEnabled

    private val _isRewardedPassEnabled = MutableStateFlow(prefs.isRewardedPassEnabled)
    val isRewardedPassEnabled: StateFlow<Boolean> = _isRewardedPassEnabled

    private val _passExpiry = MutableStateFlow(prefs.passExpiry)
    val passExpiry: StateFlow<Long> = _passExpiry

    private val _adsterraBannerId = MutableStateFlow(prefs.adsterraBannerId)
    val adsterraBannerId: StateFlow<String> = _adsterraBannerId

    private val _adsterraSmartlinkUrl = MutableStateFlow(prefs.adsterraSmartlinkUrl)
    val adsterraSmartlinkUrl: StateFlow<String> = _adsterraSmartlinkUrl

    // Rewarded Count-down visual state
    private val _showRewardedAdWindow = MutableStateFlow(false)
    val showRewardedAdWindow: StateFlow<Boolean> = _showRewardedAdWindow

    private val _adCountdown = MutableStateFlow(10)
    val adCountdown: StateFlow<Int> = _adCountdown

    private val _channelRequested = MutableStateFlow<LiveChannel?>(null)

    init {
        loadChannels()
    }

    private fun loadChannels() {
        val custom = prefs.getCustomChannels()
        if (custom.isEmpty()) {
            _channels.value = DefaultChannels.getList()
        } else {
            _channels.value = DefaultChannels.getList() + custom
        }
    }

    fun loadDynamicBranding() {
        _appName.value = prefs.appName
        _themePreset.value = prefs.themePreset
        _isBannerAdEnabled.value = prefs.isBannerAdEnabled
        _isPopunderAdEnabled.value = prefs.isPopunderAdEnabled
        _isRewardedPassEnabled.value = prefs.isRewardedPassEnabled
        _passExpiry.value = prefs.passExpiry
        _adsterraBannerId.value = prefs.adsterraBannerId
        _adsterraSmartlinkUrl.value = prefs.adsterraSmartlinkUrl
        loadChannels()
    }

    fun updateBranding(name: String, theme: String) {
        prefs.appName = name
        prefs.themePreset = theme
        _appName.value = name
        _themePreset.value = theme
    }

    fun updateAdSettings(banner: Boolean, popunder: Boolean, rewarded: Boolean, hours: Int, textLink: String) {
        prefs.isBannerAdEnabled = banner
        prefs.isPopunderAdEnabled = popunder
        prefs.isRewardedPassEnabled = rewarded
        prefs.rewardPassDurationHours = hours
        prefs.adsterraSmartlinkUrl = textLink
        
        _isBannerAdEnabled.value = banner
        _isPopunderAdEnabled.value = popunder
        _isRewardedPassEnabled.value = rewarded
        _adsterraSmartlinkUrl.value = textLink
    }

    fun addCustomLiveChannel(channel: LiveChannel) {
        val currentCustom = prefs.getCustomChannels().filter { it.id != channel.id }
        val updated = currentCustom + channel
        prefs.saveCustomChannels(updated)
        loadChannels()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun requestJoinChannel(channel: LiveChannel, context: Context) {
        _channelRequested.value = channel
        
        // Tier C: Popunder trigger
        if (prefs.isPopunderAdEnabled) {
            Toast.makeText(context, "⚡ Adsterra Pop-under smartlink is active (Simulating browser direct-link redirect)...", Toast.LENGTH_SHORT).show()
            val smartlinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(prefs.adsterraSmartlinkUrl))
            smartlinkIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(smartlinkIntent)
            } catch (e: Exception) {
                // If browser failed
            }
        }

        // Tier B: Rewarded Pass verification
        val isPassValid = System.currentTimeMillis() < prefs.passExpiry
        if (prefs.isRewardedPassEnabled && !isPassValid) {
            // Trigger 10-Second Rewarded countdown
            _adCountdown.value = 10
            _showRewardedAdWindow.value = true
        } else {
            // Bypassed or already has 12h pass active
            _selectedChannel.value = channel
            _currentScreen.value = Screen.PLAYER
        }
    }

    fun completeRewardedAdAndUnlockPass() {
        _showRewardedAdWindow.value = false
        prefs.activatePass()
        _passExpiry.value = prefs.passExpiry
        
        val unlockedChannel = _channelRequested.value
        _selectedChannel.value = unlockedChannel
        _currentScreen.value = Screen.PLAYER
    }

    fun dismissRewardedPlayerWindow() {
        _showRewardedAdWindow.value = false
        _channelRequested.value = null
    }

    fun revokeShieldActivePass() {
        prefs.clearPass()
        _passExpiry.value = 0L
    }

    fun openChannelFromNotification(channelId: String) {
        val streamObj = _channels.value.find { it.id == channelId }
        if (streamObj != null) {
            _selectedChannel.value = streamObj
            _currentScreen.value = Screen.PLAYER
        }
    }

    fun triggerLiveFCMBroadcast(context: Context, matchTitle: String, channelObj: LiveChannel) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("channel_id", channelObj.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "khela365_alerts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🏆 Khela365 Live Alert Broadcast!")
            .setContentText("$matchTitle is stream playing now! Tap to open live.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$matchTitle is playing now! Click to watch backup Server streams on Khela365."))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(channels.value.indexOf(channelObj) + 1, notification)
        Toast.makeText(context, "🔔 Notification Issued Successfully! Pull down notice shade to view.", Toast.LENGTH_LONG).show()
    }
}

// ----------------------------------------------------------------------------
// THEMATIC COMPOSE WRAPPER
// ----------------------------------------------------------------------------

@Composable
fun KhelaTheme(
    preset: String,
    content: @Composable () -> Unit
) {
    val primaryColor = when(preset) {
        "cyberpunk" -> Color(0xFFFFD700) // Cyberpunk Gold
        "solar" -> Color(0xFFFF5E00) // Electric Solar Orange
        else -> Color(0xFF39FF14) // Neon Green Sport
    }

    val secondaryColor = when(preset) {
        "cyberpunk" -> Color(0xFFDF00FF) // Neon Violet
        "solar" -> Color(0xFFFFE600) // Solar Yellow
        else -> Color(0xFFFF3131) // Neon Red
    }

    val backgroundColor = when(preset) {
        "cyberpunk" -> Color(0xFF0F081D) // Dark Violet Slate
        "solar" -> Color(0xFF161616) // Carbon Black
        else -> Color(0xFF070B13) // Dark Sports Navy
    }

    val colorScheme = darkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        background = backgroundColor,
        surface = Color(0xFF131722),
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color(0xFFE2E8F0)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ----------------------------------------------------------------------------
// APP NAVIGATION DISPATCHER
// ----------------------------------------------------------------------------

@Composable
fun KhelaAppNavigation(viewModel: AppViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    val showAdState by viewModel.showRewardedAdWindow.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(280))
            },
            label = "screen_navigation"
        ) { targetScreen ->
            when (targetScreen) {
                Screen.SPLASH -> SplashScreen {
                    viewModel.loadDynamicBranding()
                    viewModel.navigateTo(Screen.HOME)
                }
                Screen.HOME -> HomeMainHub(viewModel)
                Screen.PLAYER -> CustomPlayerScreen(viewModel)
                Screen.ADMIN_LOGIN -> AdminPasscodePortal(viewModel)
                Screen.ADMIN_DASHBOARD -> AdminDashboardDashboard(viewModel)
            }
        }

        // Tier B: Rewarded Video overlay lockdown popup window
        if (showAdState) {
            FullScreenRewardedAdPopup(viewModel)
        }
    }
}

// ----------------------------------------------------------------------------
// SPLASH SCREEN (SIMULATING FIREBASE CLOUD STREAM AND ASSET SYNC)
// ----------------------------------------------------------------------------

@Composable
fun SplashScreen(onLoadingFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
    var statusText by remember { mutableStateOf("Initializing Khela365 Engine...") }
    var loadedProgress by remember { mutableFloatStateOf(0.0f) }

    val logoPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        delay(600)
        statusText = "Syncing branding with Firebase Firestore..."
        loadedProgress = 0.35f
        delay(900)
        statusText = "Syncing Khela365 dynamic ad_settings matrices..."
        loadedProgress = 0.70f
        delay(800)
        statusText = "Asset dynamic configuration applied successfully!"
        loadedProgress = 1.0f
        delay(500)
        onLoadingFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF030509)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating dynamic neon dots representation
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(rotateAngle) {
                        drawCircle(
                            color = Color(0xFFEF4444),
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2, 4.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF10B981),
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 4.dp.toPx())
                        )
                    }
                }

                // Main Khela365 brand custom dynamic logo representation
                Image(
                    painter = painterResource(id = R.drawable.khela365_logo),
                    contentDescription = "Khela365 Dynamic Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .testTag("splash_logo_img")
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "KHELA 365",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE DESI TV PLATFORM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Premium custom progress indicator
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(loadedProgress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// INTERACTIVE PORTAL: HOME MAIN HUB
// ----------------------------------------------------------------------------

@Composable
fun HomeMainHub(viewModel: AppViewModel) {
    val activeTab by viewModel.currentTab.collectAsState()
    val appNameState by viewModel.appName.collectAsState()
    val isBannerAdEnabledState by viewModel.isBannerAdEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App top header bar
        HubTopBar(
            appName = appNameState,
            onLockClicked = { viewModel.navigateTo(Screen.ADMIN_LOGIN) }
        )

        // Mid area dynamic display (Home List, Category grid, or Pass page)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                NavigationTab.LIVE_HUB -> LiveHubTab(viewModel)
                NavigationTab.CATEGORIES -> CategoriesGridTab(viewModel)
                NavigationTab.SHIELD_PASS -> ShieldPassTab(viewModel)
            }
        }

        // Tier A: Sponsor Banner at absolute bottom if enabled!
        if (isBannerAdEnabledState) {
            SnoopBannerSection(viewModel)
        }

        // Main dynamic layout controller Bottom Tab navigation bar
        BottomMenuBar(
            activeTab = activeTab,
            onTabSelected = { viewModel.selectTab(it) }
        )
    }
}

@Composable
fun HubTopBar(
    appName: String,
    onLockClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.khela365_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = appName.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Red)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onLockClicked,
                modifier = Modifier.testTag("admin_lock_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Admin Area Entrance",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun BottomMenuBar(
    activeTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = activeTab == NavigationTab.LIVE_HUB,
            onClick = { onTabSelected(NavigationTab.LIVE_HUB) },
            icon = {
                Icon(
                    imageVector = if (activeTab == NavigationTab.LIVE_HUB) Icons.Filled.LiveTv else Icons.Outlined.LiveTv,
                    contentDescription = "Live matches banner board"
                )
            },
            label = { Text("Stream Hub", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("tab_live_hub")
        )

        NavigationBarItem(
            selected = activeTab == NavigationTab.CATEGORIES,
            onClick = { onTabSelected(NavigationTab.CATEGORIES) },
            icon = {
                Icon(
                    imageVector = if (activeTab == NavigationTab.CATEGORIES) Icons.Filled.Category else Icons.Outlined.Category,
                    contentDescription = "Desi live channels categories grid"
                )
            },
            label = { Text("Categories", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("tab_categories")
        )

        NavigationBarItem(
            selected = activeTab == NavigationTab.SHIELD_PASS,
            onClick = { onTabSelected(NavigationTab.SHIELD_PASS) },
            icon = {
                Icon(
                    imageVector = if (activeTab == NavigationTab.SHIELD_PASS) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser,
                    contentDescription = "Reward session 12h block"
                )
            },
            label = { Text("Shield Pass", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                unselectedTextColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.testTag("tab_shield_pass")
        )
    }
}

// ----------------------------------------------------------------------------
// AD MATRIX COMPONETS: SPONSOR BANNER TIER A
// ----------------------------------------------------------------------------

@Composable
fun SnoopBannerSection(viewModel: AppViewModel) {
    val bannerId by viewModel.adsterraBannerId.collectAsState()
    val context = LocalContext.current
    val smartlinkUrl by viewModel.adsterraSmartlinkUrl.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF030509))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f), RoundedCornerShape(0.dp))
            .clickable {
                Toast.makeText(context, "👉 Teleporting to Sponsor site... (Tier C smartlink direct)", Toast.LENGTH_SHORT).show()
                val bannerIntent = Intent(Intent.ACTION_VIEW, Uri.parse(smartlinkUrl))
                try { context.startActivity(bannerIntent) } catch (e: Exception) {}
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFFC72C))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SPONSOR AD",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "CRICKET & FOOTBALL BIG MATCHES SPONSOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Click to claim rewards. Ad ID: $bannerId",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable {
                    Toast.makeText(context, "Sponsor ads are remotely managed by admin panel settings", Toast.LENGTH_SHORT).show()
                }
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss Banner info",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 1: LIVE STREAM HUB (CRICKET & FOOTBALL LIVE MATCHES CARDS)
// ----------------------------------------------------------------------------

@Composable
fun LiveHubTab(viewModel: AppViewModel) {
    val channelsList by viewModel.channels.collectAsState()
    val playPassActive by viewModel.passExpiry.collectAsState()
    val isRewardedAdEnabled by viewModel.isRewardedPassEnabled.collectAsState()
    val context = LocalContext.current

    val cricketChannels = channelsList.filter { it.category == "cricket" }
    val footballChannels = channelsList.filter { it.category == "football" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Live slider layout
        item {
            SportsShowcaseBanner(viewModel)
        }

        // Active unlock session notification banner
        item {
            PassActiveUnlockIndicator(
                isPassActive = System.currentTimeMillis() < playPassActive,
                isAdEnabled = isRewardedAdEnabled,
                onAccessClick = { viewModel.selectTab(NavigationTab.SHIELD_PASS) }
            )
        }

        // Cricket Stream Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🏏",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE CRICKET EVENTS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${cricketChannels.size} ONLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(cricketChannels) { channel ->
            ChannelMatchCard(channel) {
                viewModel.requestJoinChannel(channel, context)
            }
        }

        // Football Stream Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚽",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE FOOTBALL HOSTS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${footballChannels.size} ONLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(footballChannels) { channel ->
            ChannelMatchCard(channel) {
                viewModel.requestJoinChannel(channel, context)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SportsShowcaseBanner(viewModel: AppViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "banner_indicator")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FEATURED SERVER 1 ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alphaAnim))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE 4K",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Red
                    )
                }
            }

            Column {
                Text(
                    text = "ICC T20 Series Finals",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Streaming dynamically in ultra low-latency backup channels.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun PassActiveUnlockIndicator(
    isPassActive: Boolean,
    isAdEnabled: Boolean,
    onAccessClick: () -> Unit
) {
    val cardBg = if (isPassActive || !isAdEnabled) {
        Color(0xFF0D5E3A).copy(alpha = 0.25f)
    } else {
        Color(0xFF5E1B1B).copy(alpha = 0.25f)
    }

    val cardBorder = if (isPassActive || !isAdEnabled) {
        Color(0xFF10B981).copy(alpha = 0.35f)
    } else {
        Color(0xFFEF4444).copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onAccessClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPassActive || !isAdEnabled) Icons.Filled.SafetyCheck else Icons.Filled.ReportGmailerrorred,
                contentDescription = null,
                tint = if (isPassActive || !isAdEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isPassActive || !isAdEnabled) "12-HOUR SHIELD PASS ACTIVE" else "SHIELD KEY EXPIRED (LOCK AD ON)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isPassActive || !isAdEnabled) "Adsterra video locks bypassed. Enjoy seamless play." else "Click stream card to watch raw 10s video or bypass now.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
fun ChannelMatchCard(
    channel: LiveChannel,
    onJoinClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJoinClicked)
            .testTag("channel_${channel.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Async image with local logo placeholder and error mappings
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(id = R.drawable.khela365_logo),
                    error = painterResource(id = R.drawable.khela365_logo),
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = channel.category.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.currentMatchTitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${channel.servers.size} Backup Servers Available",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                    )
                }
            }

            IconButton(
                onClick = onJoinClicked
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Watch match stream",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.04f), CircleShape)
                        .padding(4.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 2: CATEGORIES TAB (BANGALORE, INDIAN, NEWS SPORT, OTHERS)
// ----------------------------------------------------------------------------

@Composable
fun CategoriesGridTab(viewModel: AppViewModel) {
    val activeCategory by viewModel.selectedCategory.collectAsState()
    val channelsList by viewModel.channels.collectAsState()
    val context = LocalContext.current

    val categoryPresets = listOf(
        Triple("cricket", "Live Cricket", "🏏"),
        Triple("football", "Live Football", "⚽"),
        Triple("bangladesh", "Bangladeshi Live TV", "🇧🇩"),
        Triple("india", "Indian Sports", "🇮🇳"),
        Triple("news", "Live News TV", "📰"),
        Triple("othersports", "Alternative Sports", "🏎️")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (activeCategory == null) {
            Text(
                text = "DESI DIRECTORIES & SPORTS",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = "Dynamic Firestore queries mapped by category criteria.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categoryPresets) { preset ->
                    CategoryCell(
                        key = preset.first,
                        title = preset.second,
                        emoji = preset.third,
                        onClicked = { viewModel.selectCategory(preset.first) }
                    )
                }
            }
        } else {
            // Category drill-down screen
            val matchingTitle = categoryPresets.find { it.first == activeCategory }?.second ?: "Channels"
            val categoryChannels = channelsList.filter { it.category == activeCategory && it.isActive }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.selectCategory(null) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Return directories",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = matchingTitle.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${categoryChannels.size} CHANNELS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (categoryChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.DesktopAccessDisabled,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Live Hosts Broadcasted Currently",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Add custom streams for category '$activeCategory' inside Web Admin.",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(categoryChannels) { channel ->
                        ChannelMatchCard(channel) {
                            viewModel.requestJoinChannel(channel, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCell(
    key: String,
    title: String,
    emoji: String,
    onClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F1524))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClicked)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 18.sp
                )
            }

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 3: SHIELD ACCESS PAGE (12H PAS CARD CONTROLS)
// ----------------------------------------------------------------------------

@Composable
fun ShieldPassTab(viewModel: AppViewModel) {
    val passExpiry by viewModel.passExpiry.collectAsState()
    val isPassActive = System.currentTimeMillis() < passExpiry

    val sdf = remember { SimpleDateFormat("hh:mm a (dd MMM)", Locale.getDefault()) }
    val formattedExpiry = if (isPassActive) sdf.format(Date(passExpiry)) else "No Token Present"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isPassActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPassActive) Icons.Filled.Verified else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isPassActive) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (isPassActive) "KHELA365 REWARD PASS READY" else "ADSTERRA AD LOCK DETECTED",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPassActive) {
                    "Your local token SharedPreferences key is active.\nYou are protected for up to 12 hours of uninterrupted dynamic stream plays."
                } else {
                    "Your session lock has expired or is deactivated.\nTo gain ad-free plays, watch standard 10s video adsterra triggers, or bypass instantly below."
                },
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            if (isPassActive) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SHIELD ACCESS EXPIRES AT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formattedExpiry,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (isPassActive) {
                        viewModel.revokeShieldActivePass()
                    } else {
                        viewModel.completeRewardedAdAndUnlockPass()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPassActive) Color(0xFF9E2C2C) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("shield_pass_toggle_btn")
            ) {
                Text(
                    text = if (isPassActive) "REVOKE KEY & TEST AD LOCK ⚠️" else "BYPASS & ACTIVATE PASS 🏆",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPassActive) Color.White else Color.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Controlled over-the-air from Firebase & stored in local SharedPreferences",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// ADSTERRA REWARDED AD COUNTDOWN INTERACTIVE POPUP WINDOW
// ----------------------------------------------------------------------------

@Composable
fun FullScreenRewardedAdPopup(viewModel: AppViewModel) {
    val countdown by viewModel.adCountdown.collectAsState()
    val activeThemePreset by viewModel.themePreset.collectAsState()
    
    // Theme details to draw animated background correctly
    val glowColor = when(activeThemePreset) {
        "cyberpunk" -> Color(0xFFDF00FF)
        "solar" -> Color(0xFFFF5E00)
        else -> Color(0xFF39FF14)
    }

    // Launch 10-second countdown ticker
    LaunchedEffect(Unit) {
        while (viewModel.adCountdown.value > 0) {
            delay(1000)
            viewModel.updateBranding(viewModel.appName.value, viewModel.themePreset.value) // Sync dummy states
            if (viewModel.adCountdown.value > 0) {
                val nextVal = viewModel.adCountdown.value - 1
                // We use base helper directly
                val rawCountdownField = AppViewModel::class.java.getDeclaredField("_adCountdown")
                rawCountdownField.isAccessible = true
                (rawCountdownField.get(viewModel) as MutableStateFlow<Int>).value = nextVal
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures { } }, // Prevent backdrop taps
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                // Drawing continuous glowing arcs representation
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    drawArc(
                        color = glowColor,
                        startAngle = -90f,
                        sweepAngle = (countdown.toFloat() / 10f) * 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx())
                    )
                }

                Text(
                    text = if (countdown > 0) "$countdown" else "✓",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (countdown > 0) Color.White else glowColor
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "🔑 ADSTERRA PREMIUM VIDEO UNLOCK",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Watch this 10-second sponsor broadcast to claim your 12-hour Adsterra-free session key token immediately.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Beautiful eye-catcher animated canvas inside the ad window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1219))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "ad_vid")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = glowColor.copy(alpha = 0.04f * pulseScale),
                        radius = size.width / 2 * pulseScale
                    )
                    drawCircle(
                        color = glowColor.copy(alpha = 0.08f * (1.0f - pulseScale)),
                        radius = size.width / 4 * (1.0f - pulseScale)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.OndemandVideo,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SPONSOR BROADCAST FEED ACTIVE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = glowColor
                    )
                    Text(
                        text = "Decrypting secure stream keys in background...",
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.dismissRewardedPlayerWindow() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Close Ad", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (countdown == 0) {
                            viewModel.completeRewardedAdAndUnlockPass()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (countdown == 0) glowColor else Color.White.copy(alpha = 0.06f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = countdown == 0,
                    modifier = Modifier.weight(2f).height(46.dp).testTag("claim_shield_pass_btn")
                ) {
                    Text(
                        text = if (countdown > 0) "Lock Active (${countdown}s)" else "Unlock 12 Hours Free 🏆",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (countdown == 0) Color.Black else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// CUSTOM SPORTS PLAYER WORKSPACE WITH VERTICAL SWIPE GESTURES
// ----------------------------------------------------------------------------

@Composable
fun CustomPlayerScreen(viewModel: AppViewModel) {
    val channel by viewModel.selectedChannel.collectAsState()
    val activeThemePreset by viewModel.themePreset.collectAsState()
    val context = LocalContext.current

    if (channel == null) {
        viewModel.navigateTo(Screen.HOME)
        return
    }

    var selectedServerIdx by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var streamDecryptedText by remember { mutableStateOf("") }
    var isFailoverActive by remember { mutableStateOf(false) }

    // Video interactive controller states
    var isLandscapeMode by remember { mutableStateOf(false) }

    // Swipe volume/brightness indicators
    var volumeLevel by remember { mutableFloatStateOf(0.65f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.70f) }
    var showVolumeHUD by remember { mutableStateOf(false) }
    var showBrightnessHUD by remember { mutableStateOf(false) }

    // Decode decrypted stream links on start
    LaunchedEffect(channel, selectedServerIdx) {
        streamDecryptedText = "Decrypting Base64 stream..."
        delay(400)
        val encryptedUrl = channel!!.servers.getOrNull(selectedServerIdx)?.url ?: ""
        streamDecryptedText = try {
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            "DECRYPTED LINK OK -> " + String(decodedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "FAILED SNIFF DECRYPT -> RAW HLS M3U8"
        }
    }

    // Hide HUD indicator triggers automatically
    LaunchedEffect(volumeLevel) {
        delay(1200)
        showVolumeHUD = false
    }

    LaunchedEffect(brightnessLevel) {
        delay(1200)
        showBrightnessHUD = false
    }

    val primaryBarColor = when(activeThemePreset) {
        "cyberpunk" -> Color(0xFFFFD700)
        "solar" -> Color(0xFFFF5E00)
        else -> Color(0xFF39FF14)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030509))
    ) {
        // Simple Wide screen cinematic support. Landscape hides headers!
        if (!isLandscapeMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF070B13))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.HOME) },
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Exit broadcast player",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = channel!!.name.uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = channel!!.currentMatchTitle,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Red)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE FEED",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }

        // PLAYER MAIN WINDOW CONTAINER BOX
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isLandscapeMode) 1f else 0.42f)
                .background(Color.Black)
                .pointerInput(Unit) {
                    // Central double tap play/pause, left/right swipes
                    detectTapGestures(
                        onDoubleTap = {
                            isPlaying = !isPlaying
                            Toast
                                .makeText(
                                    context,
                                    if (isPlaying) "Streaming Resumed ▶" else "Streaming Paused ⏸",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Vertical Swipe brightness (left 50%) and volume (right 50%)
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDragCancel = { }
                    ) { change, dragAmount ->
                        change.consume()
                        val isLeftHalf = change.position.x < (size.width / 2f)
                        if (isLeftHalf) {
                            showBrightnessHUD = true
                            // Invert drag amount subtraction for up-drag = positive increase
                            val newBrightValue = (brightnessLevel - (dragAmount.y / 280f)).coerceIn(0f, 1f)
                            brightnessLevel = newBrightValue
                        } else {
                            showVolumeHUD = true
                            val newVolValue = (volumeLevel - (dragAmount.y / 280f)).coerceIn(0f, 1f)
                            volumeLevel = newVolValue
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Real animated canvas simulating live cricket balls / football fields!
            SportsLiveCanvasRenderer(
                isPlaying = isPlaying,
                category = channel!!.category,
                primaryColor = primaryBarColor,
                secondaryColor = when(activeThemePreset) {
                    "cyberpunk" -> Color(0xFFDF00FF)
                    "solar" -> Color(0xFFFFE600)
                    else -> Color(0xFFFF3131)
                }
            )

            // Dynamic volume swipe HUD overlay on right side
            if (showVolumeHUD) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .width(42.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                        Icon(
                            imageVector = if (volumeLevel > 0.5f) Icons.Filled.VolumeUp else if (volumeLevel > 0f) Icons.Filled.VolumeDown else Icons.Filled.VolumeMute,
                            contentDescription = null,
                            tint = primaryBarColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(volumeLevel)
                                    .background(primaryBarColor)
                            )
                        }
                        Text(
                            text = "${(volumeLevel * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Dynamic brightness swipe HUD overlay on left side
            if (showBrightnessHUD) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .width(42.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = primaryBarColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(brightnessLevel)
                                    .background(primaryBarColor)
                            )
                        }
                        Text(
                            text = "${(brightnessLevel * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom overlay player controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "PLAYING (${channel!!.servers.getOrNull(selectedServerIdx)?.name})" else "STREAM PAUSED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Native single-tap expansion landscape toggle
                    IconButton(onClick = { isLandscapeMode = !isLandscapeMode }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isLandscapeMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Simulate native screen landscape shift",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Active buffering failover panel
            if (isFailoverActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryBarColor, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "AUTOMATED BACKUP FAILOVER IN PROGRESS...",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Server breakage detected. Scanning Backup...",
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // CONTROL REGIONS AND FAILOVER TRIGGERS below player in portrait mode
        if (!isLandscapeMode) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Base64 dynamic anti-sniff encryption feedback block
                item {
                    Text(
                        text = "🔒 OTAC SNIFF PROTECTION ENGINE ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F1218))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "ENCRYPTED SERVER M3U8 STREAM:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            Text(
                                text = channel!!.servers.getOrNull(selectedServerIdx)?.url ?: "N/A",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "DECODED REAL-TIME RESOURCE ENDPOINT:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                            )
                            Text(
                                text = streamDecryptedText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Server multi backup switches
                item {
                    Column {
                        Text(
                            text = "⚡ DYNAMIC MULTI-SERVER BACKUP SWITCHER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hot-swap backup feeders instantly on signal disruption.",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            channel!!.servers.forEachIndexed { idx, srv ->
                                Button(
                                    onClick = { selectedServerIdx = idx },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedServerIdx == idx) primaryBarColor else Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("server_button_$idx")
                                ) {
                                    Text(
                                        text = srv.name.substringBefore(" "), // Cut to short names
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedServerIdx == idx) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Live automated failover simulator trigger
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⚠️ SIMULATE AUTOMATED FAILOVER PROCESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = "Press to inject a 'Raw Player Media Sniff Connection Error (404/502/onPlayerError)' into active stream. The Failover Engine will automatically scan dead signals, fall back to Server 2 or Server 3 instantly.",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            Button(
                                onClick = {
                                    isFailoverActive = true
                                    Toast.makeText(context, "💥 Signal Terminated! Injecting onPlayerError...", Toast.LENGTH_SHORT).show()
                                    // Automatically progress server list
                                    val nextSrv = (selectedServerIdx + 1) % channel!!.servers.size
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        selectedServerIdx = nextSrv
                                        isFailoverActive = false
                                        Toast.makeText(context, "✅ Relayed! Automated Failover selected Server ${nextSrv + 1}", Toast.LENGTH_LONG).show()
                                    }, 1500)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("simulate_failover_btn")
                            ) {
                                Text(
                                    text = "INJECT STREAM BREAKAGE SIGNAL ⚡",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                item {
                    // Gesture controls manual guide card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📱 NATIVE MOBILE PLAYER INTERACTION GESTURES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Swipe Left 50%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryBarColor)
                                    Text("Adjusts screen brightness", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Swipe Right 50%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryBarColor)
                                    Text("Adjusts volume volume", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Double Tap Center", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryBarColor)
                                    Text("Pause or resume play", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// CANVAS DRAWING SIMULATED SPORTS GRAPHICS GAME ENGINE
// ----------------------------------------------------------------------------

@Composable
fun SportsLiveCanvasRenderer(
    isPlaying: Boolean,
    category: String,
    primaryColor: Color,
    secondaryColor: Color
) {
    val transition = rememberInfiniteTransition(label = "sports_game")
    
    // Rotating field angles
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 6000 else 100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    // Ball bounces
    val ballOffset by transition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 1200 else 100000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val center = androidx.compose.ui.geometry.Offset(width / 2, height / 2)

        // Draw general layout outline stadium field
        if (category == "football") {
            // Draw Football pitch
            drawRect(
                color = Color(0xFF1B4D22),
                size = size
            )
            // pitch markings
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = androidx.compose.ui.geometry.Offset(24.dp.toPx(), 24.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(width - 48.dp.toPx(), height - 48.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            // Center circle line
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = 45.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // Draw animated football
            rotate(orbitAngle, pivot = center) {
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(width / 2, height / 2 - 40.dp.toPx())
                )
                // Football lines
                drawCircle(
                    color = Color.Black,
                    radius = 4.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(width / 2, height / 2 - 40.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        } else {
            // Draw Cricket Pitch
            drawRect(
                color = Color(0xFF2E6F40),
                size = size
            )
            // Center Oval boundaries
            drawOval(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = androidx.compose.ui.geometry.Offset(40.dp.toPx(), 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(width - 80.dp.toPx(), height - 40.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )
            // Center Clay strip pitch
            drawRect(
                color = Color(0xFFC2B280),
                topLeft = androidx.compose.ui.geometry.Offset(width / 2 - 15.dp.toPx(), height / 2 - 42.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(30.dp.toPx(), 84.dp.toPx())
            )
            // animated cricket ball bouncing off pitch
            drawCircle(
                color = secondaryColor,
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(width / 2 + ballOffset.dp.toPx() / 3f, height / 2 + ballOffset.dp.toPx())
            )
        }

        // Draw HUD scoreboard elements in neon glowing themes
        drawRect(
            color = Color.Black.copy(alpha = 0.54f),
            topLeft = androidx.compose.ui.geometry.Offset(16.dp.toPx(), 16.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(100.dp.toPx(), 22.dp.toPx())
        )
        drawCircle(
            color = primaryColor,
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(24.dp.toPx(), 27.dp.toPx())
        )
    }

    // Composable scoreboard textual elements overlaying top-left on canvas
    Box(modifier = Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.TopStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "KHELA365 FEED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (category == "cricket") "124/3 (15.2 Ov)" else "MOCK UP T: 74:23",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = primaryColor
            )
        }
    }
}

// ----------------------------------------------------------------------------
// SECURE WORKSPACE LOCK: DEVELOPER PIN ENTRY KEYPAD
// ----------------------------------------------------------------------------

@Composable
fun AdminPasscodePortal(viewModel: AppViewModel) {
    var passcodeEntry by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B13)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ADMIN VERIFICATION",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "Secure over-the-air database options panel.",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = passcodeEntry,
                onValueChange = {
                    passcodeEntry = it
                    loginError = false
                },
                label = { Text("Security Pincode") },
                placeholder = { Text("Default pincode is 3650") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = loginError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("admin_pin_input")
            )

            if (loginError) {
                Text(
                    text = "Incorrect cryptographic lock bypass pin. Try '3650'",
                    color = Color.Red,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.HOME) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text("Abort", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        if (passcodeEntry == "3650") {
                            viewModel.navigateTo(Screen.ADMIN_DASHBOARD)
                        } else {
                            loginError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(42.dp).testTag("admin_login_submit_btn")
                ) {
                    Text("Unlock 🔓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// FULL-SCALE WEB ADMIN PANEL RECONSTRUCTION (REBRANDING, CATEGORY ADD, FCM FEED)
// ----------------------------------------------------------------------------

enum class AdminTab {
    REBRANDING,
    AD_SETTINGS,
    MANAGE_CHANNELS,
    PUSH_BROADCAST
}

@Composable
fun AdminDashboardDashboard(viewModel: AppViewModel) {
    var activeAdminTab by remember { mutableStateOf(AdminTab.REBRANDING) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F121C))
    ) {
        // Dashboard title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF070911))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "WEB ADMIN CONTROL PANEL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Synchronized to Firestore cluster: app_config/settings",
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }

            Button(
                onClick = { viewModel.navigateTo(Screen.HOME) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Exit Hub", fontSize = 10.sp, color = Color.White)
            }
        }

        // Sub tab navigation selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF0A0C16))
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val adminTabs = listOf(
                AdminTab.REBRANDING to "Rebrand Panel",
                AdminTab.AD_SETTINGS to "Ads Toggle Matrix",
                AdminTab.MANAGE_CHANNELS to "Add Channel stream",
                AdminTab.PUSH_BROADCAST to "Push FCM Alerts"
            )

            adminTabs.forEach { (tab, label) ->
                val active = activeAdminTab == tab
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { activeAdminTab = tab }
                        .background(if (active) Color(0xFF0F121C) else Color.Transparent)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Workspace main active segment
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (activeAdminTab) {
                AdminTab.REBRANDING -> AdminRebrandingForm(viewModel)
                AdminTab.AD_SETTINGS -> AdminAdMatrixForm(viewModel)
                AdminTab.MANAGE_CHANNELS -> AdminManageChannelsForm(viewModel)
                AdminTab.PUSH_BROADCAST -> AdminPushBroadcastForm(viewModel)
            }
        }
    }
}

@Composable
fun AdminRebrandingForm(viewModel: AppViewModel) {
    val currentName by viewModel.appName.collectAsState()
    val activePreset by viewModel.themePreset.collectAsState()
    val context = LocalContext.current

    var editAppName by remember { mutableStateOf(currentName) }
    var selectedPreset by remember { mutableStateOf(activePreset) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🎨 REMOTE OVER-THE-AIR REBRANDING ENGINE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        OutlinedTextField(
            value = editAppName,
            onValueChange = { editAppName = it },
            label = { Text("Display App Name") },
            placeholder = { Text("Khela365") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("admin_rebrand_name_field")
        )

        Text(
            text = "SELECT ACTIVE BRAND THEME SCHEME",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )

        val themePresets = listOf(
            "neon" to "Neon Sport (Deep Black + Glow Football Green + Rocket Red)",
            "cyberpunk" to "Cyberpunk Violet (Dark Indigo + Golden Starlight + Digital Orchid)",
            "solar" to "Solar Flare (Asphalt Grey + Electric Orange + Sunbeam Yellow)"
        )

        themePresets.forEach { (preset, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(
                        1.dp,
                        if (selectedPreset == preset) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { selectedPreset = preset }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPreset == preset,
                    onClick = { selectedPreset = preset },
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.updateBranding(editAppName, selectedPreset)
                Toast.makeText(context, "🔥 Dynamic branding saved & deployed to local active states instantly!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().testTag("admin_rebrand_save_btn")
        ) {
            Text("DEPLOY AND TEST REBRAND REFRESH 🚀", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun AdminAdMatrixForm(viewModel: AppViewModel) {
    val bannerAdEnabled by viewModel.isBannerAdEnabled.collectAsState()
    val popunderAdEnabled by viewModel.isPopunderAdEnabled.collectAsState()
    val rewardedPassEnabled by viewModel.isRewardedPassEnabled.collectAsState()
    val context = LocalContext.current

    var bannerSwitch by remember { mutableStateOf(bannerAdEnabled) }
    var popunderSwitch by remember { mutableStateOf(popunderAdEnabled) }
    var rewardedSwitch by remember { mutableStateOf(rewardedPassEnabled) }

    var smartlinkInput by remember { mutableStateOf(viewModel.prefs.adsterraSmartlinkUrl) }
    var adsterraBannerIdInput by remember { mutableStateOf(viewModel.prefs.adsterraBannerId) }
    var rPassHoursInput by remember { mutableStateOf("${viewModel.prefs.rewardPassDurationHours}") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "💸 ADSTERRA DIRECT INDIVIDUAL TOGGLE MATRIX",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Banner Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.02f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("is_banner_ad_enabled (Tier A)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Responsive mobile script bottom anchor banner.", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Switch(
                checked = bannerSwitch,
                onCheckedChange = { bannerSwitch = it },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }

        // Popunder Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.02f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("is_popunder_ad_enabled (Tier C)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Redirect smartlink pop-unders on grid channel clicks.", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Switch(
                checked = popunderSwitch,
                onCheckedChange = { popunderSwitch = it },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }

        // Rewarded Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.02f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("is_rewarded_pass_enabled (Tier B)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("10-Second interactive video stream lockdown pass.", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Switch(
                checked = rewardedSwitch,
                onCheckedChange = { rewardedSwitch = it },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }

        // Settings Fields
        OutlinedTextField(
            value = smartlinkInput,
            onValueChange = { smartlinkInput = it },
            label = { Text("Adsterra Direct Smartlink URL") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("admin_ad_smartlink_field")
        )

        OutlinedTextField(
            value = adsterraBannerIdInput,
            onValueChange = { adsterraBannerIdInput = it },
            label = { Text("Adsterra HTML Banner ID") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = rPassHoursInput,
            onValueChange = { rPassHoursInput = it },
            label = { Text("Rewarded Pass Duration (Hours)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val hoursParsed = rPassHoursInput.toIntOrNull() ?: 12
                viewModel.updateAdSettings(
                    banner = bannerSwitch,
                    popunder = popunderSwitch,
                    rewarded = rewardedSwitch,
                    hours = hoursParsed,
                    textLink = smartlinkInput
                )
                viewModel.prefs.adsterraBannerId = adsterraBannerIdInput
                Toast.makeText(context, "💥 Remote Ad configuration variables updated on cloud database!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().testTag("admin_ad_save_btn")
        ) {
            Text("DEPLOY MONETIZATION MATRIX 📌", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun AdminManageChannelsForm(viewModel: AppViewModel) {
    val context = LocalContext.current

    var channelName by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("cricket") }
    var matchTitleText by remember { mutableStateOf("") }
    var srv1Input by remember { mutableStateOf("aHR0cHM6Ly9zdHJlYW0ubTN1OC8x") }
    var srv2Input by remember { mutableStateOf("aHR0cHM6Ly9zdHJlYW0ubTN1OC8y") }

    val categoryOptions = listOf(
        "cricket" to "Live Cricket 🏏",
        "football" to "Live Football ⚽",
        "bangladesh" to "Bangladeshi Live TV 🇧🇩",
        "india" to "Indian Channels 🇮🇳",
        "news" to "News Channels 📰",
        "othersports" to "Other Sports 🏎️"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "🏏 BROADCAST NEW LIVE MATCH FEED & CHANNELS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        OutlinedTextField(
            value = channelName,
            onValueChange = { channelName = it },
            label = { Text("Channel/Feeder Station Name") },
            placeholder = { Text("e.g., GTV Sports Pro") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("admin_chan_name_field")
        )

        OutlinedTextField(
            value = matchTitleText,
            onValueChange = { matchTitleText = it },
            label = { Text("Display Match Event Title") },
            placeholder = { Text("e.g., Copa World Cup: Brazil vs Argentina") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Select Category Directory Mapping:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categoryOptions.forEach { (cat, desc) ->
                val active = channelCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
                        .clickable { channelCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = desc,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.Black else Color.White
                    )
                }
            }
        }

        OutlinedTextField(
            value = srv1Input,
            onValueChange = { srv1Input = it },
            label = { Text("Backup Server 1 (Base64 M3U8)") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = srv2Input,
            onValueChange = { srv2Input = it },
            label = { Text("Backup Server 2 (Base64 M3U8)") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (channelName.isNotEmpty() && matchTitleText.isNotEmpty()) {
                    val uid = "custom_" + System.currentTimeMillis()
                    val newChan = LiveChannel(
                        id = uid,
                        name = channelName,
                        category = channelCategory,
                        logoUrl = "https://source.unsplash.com/100x100/?stadium,field",
                        servers = listOf(
                            ServerEntry("Server 1", srv1Input),
                            ServerEntry("Server 2", srv2Input)
                        ),
                        currentMatchTitle = matchTitleText
                    )
                    viewModel.addCustomLiveChannel(newChan)
                    Toast.makeText(context, "🏆 Broadcaster '$channelName' dynamically deployed and active!", Toast.LENGTH_SHORT).show()
                    channelName = ""
                    matchTitleText = ""
                } else {
                    Toast.makeText(context, "Failure: Name and Event details are critically required!", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().testTag("admin_chan_submit_btn")
        ) {
            Text("DEPLOY TARGET STREAM TO LIVE HUB 📡", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun AdminPushBroadcastForm(viewModel: AppViewModel) {
    val channelsList by viewModel.channels.collectAsState()
    val context = LocalContext.current

    var fcmTitleInput by remember { mutableStateOf("Brazil vs Argentina Live Alert!") }
    var selectedChannelObj by remember { mutableStateOf<LiveChannel?>(channelsList.firstOrNull()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "🌎 FCM INTERSTETER SIGNAL DISTRIBUTOR (PUSH NOTICES)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        OutlinedTextField(
            value = fcmTitleInput,
            onValueChange = { fcmTitleInput = it },
            label = { Text("Push Alert Title Label") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("admin_fcm_title_field")
        )

        Text("Map Alert Redirect To Broadcast Channel Target:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            channelsList.take(6).forEach { chan ->
                val active = selectedChannelObj?.id == chan.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedChannelObj = chan }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = active,
                        onClick = { selectedChannelObj = chan },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(chan.name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(chan.currentMatchTitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        Button(
            onClick = {
                val target = selectedChannelObj
                if (target != null && fcmTitleInput.isNotEmpty()) {
                    viewModel.triggerLiveFCMBroadcast(context, fcmTitleInput, target)
                } else {
                    Toast.makeText(context, "Select target channel mapping first!", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().testTag("admin_fcm_send_btn")
        ) {
            Text("PUSH BROADCAST OVER-THE-AIR GLOBAL ALERTS 🔔", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}
