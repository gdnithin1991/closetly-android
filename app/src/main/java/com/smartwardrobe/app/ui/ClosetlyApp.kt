package com.smartwardrobe.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.smartwardrobe.app.ClosetlyNotificationScheduler
import com.smartwardrobe.app.R
import com.smartwardrobe.app.data.FirebaseProfileRepository
import com.smartwardrobe.app.data.AiRecommendationClient
import com.smartwardrobe.app.data.LocalCacheRepository
import com.smartwardrobe.app.data.SmsPurchaseScanner
import com.smartwardrobe.app.data.WeatherRepository
import com.smartwardrobe.app.data.defaultSections
import com.smartwardrobe.app.data.demoWardrobe
import com.smartwardrobe.app.domain.Availability
import com.smartwardrobe.app.domain.AppSettings
import com.smartwardrobe.app.domain.ClothingItem
import com.smartwardrobe.app.domain.Formality
import com.smartwardrobe.app.domain.ItemType
import com.smartwardrobe.app.domain.NotificationPreferences
import com.smartwardrobe.app.domain.Occasion
import com.smartwardrobe.app.domain.OutfitRecommendation
import com.smartwardrobe.app.domain.Owner
import com.smartwardrobe.app.domain.PurchaseReminder
import com.smartwardrobe.app.domain.RecommendationEngine
import com.smartwardrobe.app.domain.RecommendationRequest
import com.smartwardrobe.app.domain.WardrobeSection
import com.smartwardrobe.app.domain.WeatherMood
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClosetlyApp() {
    val engine = remember { RecommendationEngine() }
    val repository = remember { FirebaseProfileRepository() }
    val weatherRepository = remember { WeatherRepository() }
    val aiClient = remember { AiRecommendationClient() }
    val smsScanner = remember { SmsPurchaseScanner() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { LocalCacheRepository(context) }
    val wardrobe = remember { mutableStateListOf<ClothingItem>() }
    val sections = remember { mutableStateListOf<WardrobeSection>() }
    val reminders = remember { mutableStateListOf<PurchaseReminder>() }
    var prefs by remember { mutableStateOf(NotificationPreferences()) }
    var appSettings by remember { mutableStateOf(AppSettings()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ClosetlyNotificationScheduler.sync(context, prefs)
    }
    var page by remember { mutableStateOf(AppPage.HOME) }
    var owner by remember { mutableStateOf(Owner.ME) }
    var weather by remember { mutableStateOf(WeatherMood.RAIN) }
    var householdId by remember { mutableStateOf<String?>(auth.currentUser?.uid) }
    var signedInUser by remember { mutableStateOf(auth.currentUser) }
    val primaryProfileName = signedInUser?.displayName?.takeIf { it.isNotBlank() }
        ?: signedInUser?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "You"
    val profileNames = mapOf(
        Owner.ME to primaryProfileName,
        Owner.WIFE to appSettings.secondProfileName.ifBlank { "Household member" }
    )
    var cloudMessage by remember {
        mutableStateOf(
            signedInUser?.email?.let { "Signed in as $it" }
                ?: "Firebase connected. Sign in with Google to save household data."
        )
    }
    var photoUploadMessage by remember { mutableStateOf("Cloud photos save to Firestore on Spark after sign-in.") }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val found = smsScanner.scanRecentClothingPurchases(context)
            reminders.addAll(0, found)
            householdId?.let { id -> found.forEach { reminder -> repository.savePurchaseReminder(id, reminder) { } } }
            cloudMessage = "SMS scan found ${found.size} purchase reminder(s)."
        } else {
            cloudMessage = "SMS permission denied. Manual reminders still work."
        }
    }

    fun loadCloudHousehold(id: String) {
        repository.loadWardrobeItems(id) { result ->
            result.onSuccess { items ->
                if (items.isNotEmpty()) {
                    wardrobe.clear()
                    wardrobe.addAll(items)
                    cloudMessage = "Loaded ${items.size} cloud wardrobe items."
                } else {
                    cloudMessage = "Cloud household ready. No saved wardrobe items yet."
                }
            }
            result.onFailure { cloudMessage = it.message ?: "Could not load cloud wardrobe." }
        }
        repository.loadWardrobeSections(id) { result ->
            result.onSuccess { cloudSections ->
                if (cloudSections.isNotEmpty()) {
                    sections.clear()
                    sections.addAll(cloudSections)
                }
            }
        }
        repository.loadPurchaseReminders(id) { result ->
            result.onSuccess { cloudReminders ->
                reminders.clear()
                reminders.addAll(cloudReminders)
            }
        }
        repository.loadAppSettings(id) { result ->
            result.onSuccess { appSettings = it }
        }
    }

    fun syncLocalHousehold(id: String) {
        wardrobe.forEach { item -> repository.saveWardrobeItem(id, item) { } }
        sections.forEach { section -> repository.saveWardrobeSection(id, section) { } }
        reminders.forEach { reminder -> repository.savePurchaseReminder(id, reminder) { } }
        repository.saveAppSettings(id, appSettings) { }
        cloudMessage = "Sync started for ${wardrobe.size} items and ${sections.size} sections."
    }

    fun saveLocalCache() {
        cache.save(wardrobe, sections, appSettings)
    }

    fun saveChangedItem(updated: ClothingItem) {
        updateItem(wardrobe, updated)
        householdId?.let { id -> repository.saveWardrobeItem(id, updated) { } }
    }

    LaunchedEffect(signedInUser?.uid) {
        val user = signedInUser ?: return@LaunchedEffect
        val id = user.uid
        householdId = id
        repository.createProfileAndHousehold { result ->
            result.onSuccess { loadCloudHousehold(it) }
            result.onFailure { cloudMessage = it.message ?: "Cloud household setup failed." }
        }
    }

    LaunchedEffect(Unit) {
        cache.load()?.let { snapshot ->
            wardrobe.clear()
            wardrobe.addAll(snapshot.items)
            sections.clear()
            sections.addAll(snapshot.sections)
            appSettings = snapshot.settings
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ClosetlyNotificationScheduler.sync(context, prefs)
        }
    }

    LaunchedEffect(wardrobe.size, sections.size, appSettings) {
        saveLocalCache()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClosetlyLogo()
                        Column {
                            Text("Closetly", fontWeight = FontWeight.Bold)
                            Text("Smart wardrobe for your household", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            SegmentRow(AppPage.entries, page, { page = it }) { it.label() }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileChip(profileNames.nameFor(Owner.ME), owner == Owner.ME) { owner = Owner.ME }
                ProfileChip(profileNames.nameFor(Owner.WIFE), owner == Owner.WIFE) { owner = Owner.WIFE }
                AssistChip(onClick = { }, label = { Text(weather.label()) })
            }
            Spacer(Modifier.height(12.dp))
            when (page) {
                AppPage.HOME -> HomeScreen(
                    owner = owner,
                    wardrobe = wardrobe,
                    engine = engine,
                    weather = weather,
                    homeCity = appSettings.homeCity,
                    profileNames = profileNames,
                    reminders = reminders,
                    onMarkWorn = { recommendation ->
                        markWorn(wardrobe, recommendation).forEach { changed ->
                            householdId?.let { id -> repository.saveWardrobeItem(id, changed) { } }
                        }
                    },
                    onOpenPlanner = { page = AppPage.PLAN },
                    onOpenWardrobe = { page = AppPage.WARDROBE }
                )

                AppPage.WARDROBE -> WardrobeScreen(
                    owner = owner,
                    wardrobe = wardrobe,
                    sections = sections,
                    profileNames = profileNames,
                    onAddItem = { item ->
                        wardrobe.add(0, item)
                        householdId?.let { id ->
                            repository.saveWardrobeItem(id, item) { saveResult ->
                                saveResult.onFailure { photoUploadMessage = it.message ?: "Item metadata sync failed." }
                            }
                            item.photoUri?.let { localUri ->
                                repository.saveItemPhotoInFirestore(context, id, item.id, localUri) { uploadResult ->
                                    uploadResult.onSuccess { photo ->
                                        val cloudItem = item.copy(photoData = photo.first, photoByteSize = photo.second)
                                        updateItem(wardrobe, cloudItem)
                                        photoUploadMessage = "Compressed photo saved to Firestore."
                                    }
                                    uploadResult.onFailure {
                                        photoUploadMessage = it.message ?: "Photo save failed. Use a smaller image."
                                    }
                                }
                            }
                        } ?: run {
                            photoUploadMessage = "Item saved locally. Sign in to upload photos to cloud."
                        }
                    },
                    onUpdateItem = { updated ->
                        saveChangedItem(updated)
                    },
                    onAddSection = { section ->
                        sections.add(section)
                        householdId?.let { id -> repository.saveWardrobeSection(id, section) { } }
                    },
                    onAddDemoData = {
                        if (wardrobe.isEmpty()) wardrobe.addAll(demoWardrobe())
                        if (sections.isEmpty()) sections.addAll(defaultSections())
                        householdId?.let { id -> syncLocalHousehold(id) }
                    },
                    photoUploadMessage = photoUploadMessage
                )

                AppPage.PLAN -> PlannerScreen(
                    owner = owner,
                    wardrobe = wardrobe,
                    engine = engine,
                    weather = weather,
                    profileNames = profileNames,
                    appSettings = appSettings,
                    onAppSettingsChange = {
                        appSettings = it
                        householdId?.let { id -> repository.saveAppSettings(id, it) { } }
                    },
                    aiClient = aiClient,
                    onWeatherChange = { weather = it },
                    onMarkPacked = { updated -> saveChangedItem(updated.copy(availability = Availability.PACKED)) }
                )

                AppPage.SETTINGS -> SettingsScreen(
                    preferences = prefs,
                    onPreferencesChange = {
                        prefs = it
                        ClosetlyNotificationScheduler.sync(context, it)
                        householdId?.let { id -> repository.saveNotificationPreferences(id, it) { } }
                    },
                    reminders = reminders,
                    onAddReminder = { reminder ->
                        reminders.add(0, reminder)
                        householdId?.let { id -> repository.savePurchaseReminder(id, reminder) { } }
                    },
                    appSettings = appSettings,
                    onAppSettingsChange = {
                        appSettings = it
                        householdId?.let { id -> repository.saveAppSettings(id, it) { } }
                    },
                    onFetchWeather = {
                        scope.launch {
                            weatherRepository.fetch(appSettings.homeCity)
                                .onSuccess {
                                    weather = it.mood
                                    appSettings = appSettings.copy(lastWeatherSummary = it.summary)
                                    householdId?.let { id -> repository.saveAppSettings(id, appSettings.copy(lastWeatherSummary = it.summary)) { } }
                                }
                                .onFailure { cloudMessage = it.message ?: "Weather fetch failed." }
                        }
                    },
                    onScanSms = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                            val found = smsScanner.scanRecentClothingPurchases(context)
                            reminders.addAll(0, found)
                            householdId?.let { id -> found.forEach { reminder -> repository.savePurchaseReminder(id, reminder) { } } }
                            cloudMessage = "SMS scan found ${found.size} purchase reminder(s)."
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                    onCreateInvite = {
                        val id = householdId
                        if (id == null) {
                            cloudMessage = "Sign in before creating an invite code."
                        } else {
                            repository.createHouseholdInvite(id) { result ->
                                cloudMessage = result.fold(
                                    onSuccess = { "Invite code: $it" },
                                    onFailure = { it.message ?: "Could not create invite." }
                                )
                            }
                        }
                    },
                    onAcceptInvite = { code ->
                        repository.acceptHouseholdInvite(code) { result ->
                            result.onSuccess {
                                householdId = it
                                loadCloudHousehold(it)
                            }
                            result.onFailure { cloudMessage = it.message ?: "Could not join household." }
                        }
                    },
                    cloudMessage = cloudMessage,
                    signedInUser = signedInUser,
                    onGoogleSignIn = {
                        scope.launch {
                            signInWithGoogle(context, auth) { result ->
                                result.onSuccess { user ->
                                    signedInUser = user
                                    cloudMessage = "Signed in as ${user.email ?: user.uid}"
                                    repository.createProfileAndHousehold { household ->
                                        householdId = household.getOrNull()
                                        household.onSuccess { loadCloudHousehold(it) }
                                        household.onFailure { cloudMessage = it.message ?: "Cloud household save failed." }
                                    }
                                }
                                result.onFailure { cloudMessage = it.message ?: "Google sign-in failed." }
                            }
                        }
                    },
                    onSignOut = {
                        auth.signOut()
                        signedInUser = null
                        householdId = null
                        cloudMessage = "Signed out. Local wardrobe remains on this device."
                    },
                    onPrivateCloudSignIn = {
                        auth.signInAnonymously()
                            .addOnSuccessListener { result ->
                                result.user?.let { user ->
                                    signedInUser = user
                                    cloudMessage = "Private cloud session created."
                                    repository.createProfileAndHousehold { household ->
                                        householdId = household.getOrNull()
                                        household.onSuccess { loadCloudHousehold(it) }
                                        household.onFailure { cloudMessage = it.message ?: "Cloud household save failed." }
                                    }
                                } ?: run {
                                    cloudMessage = "Firebase did not return a private user."
                                }
                            }
                            .addOnFailureListener {
                                cloudMessage = it.message ?: "Private cloud sign-in failed. Enable Anonymous provider in Firebase Auth."
                            }
                    },
                    onCreateCloudProfile = {
                        repository.createProfileAndHousehold { result ->
                            householdId = result.getOrNull()
                            cloudMessage = result.fold(
                                onSuccess = {
                                    syncLocalHousehold(it)
                                    "Cloud sync started: $it"
                                },
                                onFailure = { "Sign in is needed before cloud sync can save live data." }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    owner: Owner,
    wardrobe: List<ClothingItem>,
    engine: RecommendationEngine,
    weather: WeatherMood,
    homeCity: String,
    profileNames: Map<Owner, String>,
    reminders: List<PurchaseReminder>,
    onMarkWorn: (OutfitRecommendation) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenWardrobe: () -> Unit
) {
    val today = engine.suggest(
        wardrobe,
        RecommendationRequest(owner = owner, occasion = Occasion.OFFICE, weatherMood = weather)
    ).firstOrNull()
    val week = engine.weeklyOfficePlan(wardrobe, owner, weather)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Good morning", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${profileNames.nameFor(owner)} profile - $homeCity - ${weather.label()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            FeatureCard(
                title = "Today's office outfit",
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) }
            ) {
                if (today == null) {
                    Text("Add a top, bottom and shoes to start receiving outfit suggestions.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenWardrobe) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add wardrobe item")
                    }
                } else {
                    Text(today.items.joinToString(" + ") { it.name }, fontWeight = FontWeight.SemiBold)
                    Text(today.reason)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onMarkWorn(today) }) { Text("Mark worn") }
                        OutlinedButton(onClick = onOpenPlanner) { Text("Change") }
                    }
                }
            }
        }
        item {
            FeatureCard(
                title = "This week at office",
                icon = { Icon(Icons.Outlined.Event, contentDescription = null) }
            ) {
                if (wardrobe.none { it.owner == owner && it.availability == Availability.AVAILABLE }) {
                    Text("Add your first few office items to generate a weekly plan.")
                } else {
                    week.forEach { (day, outfit) ->
                        Text("$day - ${outfit?.items?.joinToString(" + ") { it.shortName() } ?: "Needs more items"}")
                    }
                }
            }
        }
        item {
            FeatureCard(
                title = "Purchase reminders",
                icon = { Icon(Icons.Outlined.ShoppingBag, contentDescription = null) }
            ) {
                reminders.filterNot { it.handled }.take(2).ifEmpty {
                    listOf(PurchaseReminder("none", "No pending reminders", "", ""))
                }.forEach { reminder ->
                    Text("${reminder.merchant} ${reminder.amount}".trim())
                }
            }
        }
    }
}

@Composable
private fun WardrobeScreen(
    owner: Owner,
    wardrobe: List<ClothingItem>,
    sections: List<WardrobeSection>,
    profileNames: Map<Owner, String>,
    onAddItem: (ClothingItem) -> Unit,
    onUpdateItem: (ClothingItem) -> Unit,
    onAddSection: (WardrobeSection) -> Unit,
    onAddDemoData: () -> Unit,
    photoUploadMessage: String
) {
    var addDialogPhoto by remember { mutableStateOf<Uri?>(null) }
    var showSectionDialog by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        addDialogPhoto = uri
    }
    val visibleItems = wardrobe.filter { it.owner == owner || !it.privateToOwner }

    if (addDialogPhoto != null) {
        AddItemDialog(
            owner = owner,
            sections = sections,
            photoUri = addDialogPhoto?.toString(),
            profileNames = profileNames,
            onDismiss = { addDialogPhoto = null },
            onSave = {
                onAddItem(it)
                addDialogPhoto = null
            }
        )
    }
    if (showSectionDialog) {
        AddSectionDialog(
            onDismiss = { showSectionDialog = false },
            onSave = {
                onAddSection(it)
                showSectionDialog = false
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Checkroom, contentDescription = null)
                    Text(
                        "${profileNames.nameFor(owner)} wardrobe",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = { photoPicker.launch("image/*") }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add item")
                }
            }
            Text("${visibleItems.size} visible items - clothes, shoes, watches and accessories are all included.")
            Text(photoUploadMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (wardrobe.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAddDemoData) { Text("Load demo wardrobe") }
            }
        }
        item {
            FeatureCard(
                title = "Wardrobe layout",
                icon = { Icon(Icons.Outlined.CloudDone, contentDescription = null) }
            ) {
                if (sections.isEmpty()) {
                    Text("Add wardrobe sections such as shelves, drawers, shoe racks or hanging space.")
                } else {
                    sections.forEach { section ->
                        val used = wardrobe.count { it.owner == owner && it.wardrobeSection == section.name }
                        Text("${section.name}: $used/${section.capacity}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showSectionDialog = true }) { Text("Add section") }
            }
        }
        items(visibleItems, key = { it.id }) { item ->
            WardrobeItemCard(owner = owner, item = item, profileNames = profileNames, onUpdateItem = onUpdateItem)
        }
    }
}

@Composable
private fun PlannerScreen(
    owner: Owner,
    wardrobe: List<ClothingItem>,
    engine: RecommendationEngine,
    weather: WeatherMood,
    profileNames: Map<Owner, String>,
    appSettings: AppSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    aiClient: AiRecommendationClient,
    onWeatherChange: (WeatherMood) -> Unit,
    onMarkPacked: (ClothingItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var occasion by remember { mutableStateOf(Occasion.OFFICE) }
    var eventText by remember(appSettings.defaultFunctionContext) { mutableStateOf(appSettings.defaultFunctionContext) }
    var destination by remember(appSettings.defaultTravelDestination) { mutableStateOf(appSettings.defaultTravelDestination) }
    var outfitCountText by remember { mutableStateOf("5") }
    var aiEnabled by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf(emptyList<OutfitRecommendation>()) }
    val today = LocalDate.now().toEpochDay()
    val usedToday = if (appSettings.aiSuggestionEpochDay == today) appSettings.aiSuggestionsUsedToday else 0
    val aiRemaining = (2 - usedToday).coerceAtLeast(0)
    val request = RecommendationRequest(
        owner = owner,
        occasion = occasion,
        weatherMood = weather,
        placeOrEvent = eventText,
        destination = destination,
        outfitCount = outfitCountText.toIntOrNull() ?: 5
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Plan outfits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SegmentRow(Occasion.entries, occasion, { occasion = it }) { it.label() }
        }
        item {
            Text("Weather", fontWeight = FontWeight.SemiBold)
            CompactChipRow(WeatherMood.entries, weather, onWeatherChange) { it.label() }
        }
        if (occasion == Occasion.FUNCTION) {
            item {
                OutlinedTextField(
                    value = eventText,
                    onValueChange = {
                        eventText = it
                        onAppSettingsChange(appSettings.copy(defaultFunctionContext = it))
                    },
                    label = { Text("Place or event") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (occasion == Occasion.TRAVEL) {
            item {
                OutlinedTextField(
                    value = destination,
                    onValueChange = {
                        destination = it
                        onAppSettingsChange(appSettings.copy(defaultTravelDestination = it))
                    },
                    label = { Text("Destination") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = outfitCountText,
                    onValueChange = { outfitCountText = it.filter(Char::isDigit).ifBlank { "1" } },
                    label = { Text("Number of outfits") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            FeatureCard(
                title = "Recommendation mode",
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI enhanced", modifier = Modifier.weight(1f))
                    Switch(checked = aiEnabled, onCheckedChange = { aiEnabled = it })
                }
                Text("$aiRemaining of 2 AI suggestions left today. Local suggestions are unlimited.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { results = engine.suggest(wardrobe, request) }) { Text("Local suggestions") }
                    OutlinedButton(
                        enabled = aiEnabled && aiRemaining > 0,
                        onClick = {
                            onAppSettingsChange(appSettings.copy(aiSuggestionsUsedToday = usedToday + 1, aiSuggestionEpochDay = today))
                            val local = engine.suggest(wardrobe, request)
                            if (appSettings.hostedAiEndpoint.isBlank()) {
                                results = local.map {
                                    it.copy(score = (it.score + 4).coerceAtMost(100), reason = "AI endpoint not configured. Local enhanced: ${it.reason}")
                                }
                            } else {
                                scope.launch {
                                    aiClient.enhance(
                                        endpoint = appSettings.hostedAiEndpoint,
                                        apiKey = appSettings.hostedAiApiKey,
                                        occasion = occasion,
                                        context = eventText.ifBlank { destination },
                                        candidates = local
                                    ).onSuccess { results = it }
                                        .onFailure {
                                            results = local.map { recommendation ->
                                                recommendation.copy(reason = "AI call failed, using local result: ${recommendation.reason}")
                                            }
                                        }
                                }
                            }
                        }
                    ) { Text("AI enhanced") }
                }
            }
        }
        if (occasion == Occasion.OFFICE) {
            item {
                FeatureCard(title = "Monday to Friday plan", icon = { Icon(Icons.Outlined.Event, null) }) {
                    engine.weeklyOfficePlan(wardrobe, owner, weather).forEach { (day, outfit) ->
                        Text("$day - ${outfit?.items?.joinToString(" + ") { it.shortName() } ?: "Add more items"}")
                    }
                }
            }
        }
        if (occasion == Occasion.TRAVEL) {
            item {
                FeatureCard(title = "Packing list", icon = { Icon(Icons.Outlined.Luggage, null) }) {
                    engine.packingList(wardrobe, owner, outfitCountText.toIntOrNull() ?: 5).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onMarkPacked(item) }) { Text("Packed") }
                        }
                    }
                }
            }
        }
        items(results, key = { it.items.joinToString { item -> item.id } }) { result ->
            RecommendationCard(result)
        }
    }
}

@Composable
private fun SettingsScreen(
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    reminders: List<PurchaseReminder>,
    onAddReminder: (PurchaseReminder) -> Unit,
    appSettings: AppSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    onFetchWeather: () -> Unit,
    onScanSms: () -> Unit,
    onCreateInvite: () -> Unit,
    onAcceptInvite: (String) -> Unit,
    cloudMessage: String,
    signedInUser: FirebaseUser?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPrivateCloudSignIn: () -> Unit,
    onCreateCloudProfile: () -> Unit
) {
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FeatureCard(title = "Cloud and privacy", icon = { Icon(Icons.Outlined.CloudDone, null) }) {
                Text(cloudMessage)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signedInUser == null) {
                        Button(onClick = onGoogleSignIn) { Text("Sign in with Google") }
                        OutlinedButton(onClick = onPrivateCloudSignIn) { Text("Private cloud") }
                    } else {
                        Button(onClick = onCreateCloudProfile) { Text("Sync household") }
                        OutlinedButton(onClick = onSignOut) { Text("Sign out") }
                    }
                }
                Text("Private items stay tagged per owner. On Spark, small compressed photos are saved in Firestore.")
            }
        }
        item {
            FeatureCard(title = "Household settings", icon = { Icon(Icons.Outlined.Settings, null) }) {
                OutlinedTextField(
                    value = appSettings.homeCity,
                    onValueChange = { onAppSettingsChange(appSettings.copy(homeCity = it)) },
                    label = { Text("Home city") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appSettings.secondProfileName,
                    onValueChange = { onAppSettingsChange(appSettings.copy(secondProfileName = it)) },
                    label = { Text("Second household profile name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(appSettings.lastWeatherSummary)
                OutlinedButton(onClick = onFetchWeather) { Text("Fetch weather") }
                OutlinedTextField(
                    value = appSettings.hostedAiEndpoint,
                    onValueChange = { onAppSettingsChange(appSettings.copy(hostedAiEndpoint = it)) },
                    label = { Text("Hosted AI endpoint") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appSettings.hostedAiApiKey,
                    onValueChange = { onAppSettingsChange(appSettings.copy(hostedAiApiKey = it)) },
                    label = { Text("Hosted AI API key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            FeatureCard(title = "Notifications", icon = { Icon(Icons.Outlined.Notifications, null) }) {
                PreferenceRow("Daily outfit", preferences.dailyOutfit) {
                    onPreferencesChange(preferences.copy(dailyOutfit = it))
                }
                PreferenceRow("Weekly plan", preferences.weeklyPlan) {
                    onPreferencesChange(preferences.copy(weeklyPlan = it))
                }
                PreferenceRow("Laundry follow-up", preferences.laundryFollowUp) {
                    onPreferencesChange(preferences.copy(laundryFollowUp = it))
                }
                PreferenceRow("Purchase reminder", preferences.purchaseReminder) {
                    onPreferencesChange(preferences.copy(purchaseReminder = it))
                }
                PreferenceRow("Travel packing", preferences.travelPacking) {
                    onPreferencesChange(preferences.copy(travelPacking = it))
                }
            }
        }
        item {
            FeatureCard(title = "Manual purchase reminder", icon = { Icon(Icons.Outlined.ShoppingBag, null) }) {
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onScanSms) { Text("Scan SMS for clothing purchases") }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = merchant.isNotBlank() || amount.isNotBlank(),
                    onClick = {
                        onAddReminder(
                            PurchaseReminder(
                                id = UUID.randomUUID().toString(),
                                merchant = merchant.ifBlank { "Purchase" },
                                amount = amount.ifBlank { "Amount not set" },
                                source = "Manual"
                            )
                        )
                        merchant = ""
                        amount = ""
                    }
                ) { Text("Remind me to add items") }
                reminders.take(3).forEach { Text("${it.source}: ${it.merchant} ${it.amount}") }
            }
        }
        item {
            FeatureCard(title = "Household invite", icon = { Icon(Icons.Outlined.Person, null) }) {
                Button(onClick = onCreateInvite) { Text("Create invite code") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text("Join with invite code") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(enabled = inviteCode.isNotBlank(), onClick = { onAcceptInvite(inviteCode) }) {
                    Text("Join household")
                }
            }
        }
    }
}

@Composable
private fun WardrobeItemCard(
    owner: Owner,
    item: ClothingItem,
    profileNames: Map<Owner, String>,
    onUpdateItem: (ClothingItem) -> Unit
) {
    val hidden = item.owner != owner && item.privateToOwner
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(item)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (hidden) "Private item" else item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!hidden) {
                    Text("${profileNames.nameFor(item.owner)} - ${item.type.label()} - ${item.color} - ${item.formality.label()}")
                    Text("Section: ${item.wardrobeSection}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { onUpdateItem(item.copy(availability = item.availability.next())) }, label = { Text(item.availability.label()) })
                        AssistChip(onClick = { onUpdateItem(item.copy(privateToOwner = !item.privateToOwner)) }, label = { Text(if (item.privateToOwner) "Private" else "Shared") })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(result: OutfitRecommendation) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.items.joinToString(" + ") { it.name }, fontWeight = FontWeight.Bold)
            Text(result.reason)
            Text("Match score: ${result.score}")
        }
    }
}

@Composable
private fun AddItemDialog(
    owner: Owner,
    sections: List<WardrobeSection>,
    photoUri: String?,
    profileNames: Map<Owner, String>,
    onDismiss: () -> Unit,
    onSave: (ClothingItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedOwner by remember { mutableStateOf(owner) }
    var type by remember { mutableStateOf(ItemType.TOP) }
    var color by remember { mutableStateOf("") }
    var formality by remember { mutableStateOf(Formality.SMART_CASUAL) }
    var section by remember { mutableStateOf(sections.firstOrNull()?.name ?: "Unassigned") }
    var privateItem by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add wardrobe item") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.fillMaxWidth()) }
                item { Text("Owner", fontWeight = FontWeight.SemiBold); CompactChipRow(Owner.entries, selectedOwner, { selectedOwner = it }) { profileNames.nameFor(it) } }
                item { Text("Type", fontWeight = FontWeight.SemiBold); CompactChipRow(ItemType.entries, type, { type = it }) { it.label() } }
                item { Text("Formality", fontWeight = FontWeight.SemiBold); CompactChipRow(Formality.entries, formality, { formality = it }) { it.label() } }
                item { Text("Section", fontWeight = FontWeight.SemiBold); CompactChipRow(sections.map { it.name }.ifEmpty { listOf("Unassigned") }, section, { section = it }) { it } }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Private to owner", modifier = Modifier.weight(1f))
                        Switch(checked = privateItem, onCheckedChange = { privateItem = it })
                    }
                    Text(photoUri?.let { "Photo selected" } ?: "No photo selected")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ClothingItem(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            owner = selectedOwner,
                            type = type,
                            color = color.trim().ifBlank { "Unknown" },
                            formality = formality,
                            privateToOwner = privateItem,
                            wardrobeSection = section,
                            photoUri = photoUri
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSectionDialog(onDismiss: () -> Unit, onSave: (WardrobeSection) -> Unit) {
    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add wardrobe section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Section name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = capacity, onValueChange = { capacity = it.filter(Char::isDigit) }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        WardrobeSection(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            capacity = capacity.toIntOrNull() ?: 10
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PreferenceRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FeatureCard(title: String, icon: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(title, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun <T> SegmentRow(values: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        values.forEach { value ->
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun <T> CompactChipRow(values: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        values.forEach { value ->
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun ProfileChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun ClosetlyLogo() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun PhotoThumb(item: ClothingItem) {
    val context = LocalContext.current
    val swatch = item.color.toSwatch()
    val imageBitmap = remember(item.photoUri, item.photoData) {
        runCatching {
            when {
                item.photoData != null -> {
                    val bytes = Base64.decode(item.photoData, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                item.photoUri != null && !item.photoUri.startsWith("firestore:") -> {
                    context.contentResolver.openInputStream(Uri.parse(item.photoUri))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }
                else -> null
            }
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(swatch.copy(alpha = 0.22f))
            .border(1.dp, swatch.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(if (item.photoUri != null) "IMG" else item.type.label().take(2).uppercase(), fontWeight = FontWeight.Bold, color = swatch)
        }
    }
}

private fun markWorn(items: MutableList<ClothingItem>, recommendation: OutfitRecommendation): List<ClothingItem> {
    val today = LocalDate.now().toEpochDay()
    val changed = mutableListOf<ClothingItem>()
    recommendation.items.forEach { worn ->
        val index = items.indexOfFirst { it.id == worn.id }
        if (index >= 0) {
            val updated = items[index].copy(lastWornEpochDay = today, availability = Availability.LAUNDRY)
            items[index] = updated
            changed.add(updated)
        }
    }
    return changed
}

private fun updateItem(items: MutableList<ClothingItem>, updated: ClothingItem) {
    val index = items.indexOfFirst { it.id == updated.id }
    if (index >= 0) items[index] = updated
}

private fun Map<Owner, String>.nameFor(owner: Owner): String =
    this[owner]?.takeIf { it.isNotBlank() } ?: when (owner) {
        Owner.ME -> "You"
        Owner.WIFE -> "Household member"
    }

private fun ItemType.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun Formality.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun Occasion.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun WeatherMood.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun ClothingItem.shortName() = name.split(" ").take(2).joinToString(" ")

private fun Availability.label() = when (this) {
    Availability.AVAILABLE -> "Available"
    Availability.LAUNDRY -> "Laundry"
    Availability.DRY_CLEANING -> "Dry cleaning"
    Availability.PACKED -> "Packed"
    Availability.REPAIR -> "Repair"
    Availability.UNAVAILABLE -> "Unavailable"
}

private fun Availability.next() = when (this) {
    Availability.AVAILABLE -> Availability.LAUNDRY
    Availability.LAUNDRY -> Availability.DRY_CLEANING
    Availability.DRY_CLEANING -> Availability.PACKED
    Availability.PACKED -> Availability.REPAIR
    Availability.REPAIR -> Availability.UNAVAILABLE
    Availability.UNAVAILABLE -> Availability.AVAILABLE
}

private fun String.toSwatch(): Color = when (lowercase()) {
    "navy" -> Color(0xFF1C2D59)
    "white" -> Color(0xFFDFDFD8)
    "beige" -> Color(0xFFC9A46A)
    "charcoal" -> Color(0xFF33343A)
    "brown" -> Color(0xFF7A4B28)
    "silver" -> Color(0xFF8F9AA3)
    "teal" -> Color(0xFF167D7F)
    "green" -> Color(0xFF367A4B)
    "black" -> Color(0xFF151515)
    "maroon" -> Color(0xFF7D2539)
    else -> Color(0xFF6B5AA6)
}
