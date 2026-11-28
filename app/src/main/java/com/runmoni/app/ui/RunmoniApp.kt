@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runmoni.app.ui

import android.content.res.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runmoni.app.R
import com.runmoni.app.domain.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RunmoniApp(state: AppState, actions: AppActions, updates: AppUpdateUiState = AppUpdateUiState(), onCheckUpdates: () -> Unit = {}, onExternalAppLink: ((String) -> Unit)? = null) {
    RunmoniTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var crewNavigation by rememberSaveable { mutableStateOf<String?>(null) }
        var partyNavigation by rememberSaveable { mutableStateOf<String?>(null) }
        var communityNavigationEpoch by rememberSaveable { mutableIntStateOf(0) }
        var settings by rememberSaveable { mutableStateOf(false) }
        var closet by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = closet) { closet = false }
        val mainStateHolder = rememberSaveableStateHolder()
        var signIn by rememberSaveable { mutableStateOf(false) }
        var profileFromSettings by rememberSaveable { mutableStateOf(false) }
        var appInformation by rememberSaveable { mutableStateOf(false) }
        var appInformationFromSettings by rememberSaveable { mutableStateOf(false) }
        val settingsScroll = rememberScrollState()
        var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
        var recordOpening by remember { mutableStateOf(Any()) }
        var recordSnapshot by remember { mutableStateOf<RunRecord?>(null) }
        var deletingRecordId by rememberSaveable { mutableStateOf<String?>(null) }
        var sharingRecordId by rememberSaveable { mutableStateOf<String?>(null) }
        var requestedSaveCueId by remember { mutableStateOf<String?>(null) }
        var newlySavedRecordId by remember { mutableStateOf<String?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    requestedSaveCueId = null
                    newlySavedRecordId = null
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val recordStateHolder = rememberSaveableStateHolder()
        fun openRecord(record: RunRecord) {
            recordOpening = Any()
            newlySavedRecordId = null
            recordSnapshot = record
            selectedRecordId = record.id
            deletingRecordId = null
            sharingRecordId = null
            settings = false
            closet = false
            appInformation = false
            signIn = false
        }
        fun closeRecord() {
            recordOpening = Any()
            newlySavedRecordId = null
            selectedRecordId?.let { id ->
                recordStateHolder.removeState("record_$id")
                recordStateHolder.removeState("share_$id")
            }
            selectedRecordId = null
            recordSnapshot = null
            deletingRecordId = null
            sharingRecordId = null
        }
        val selectedRecord = state.records.firstOrNull { it.id == selectedRecordId }
            ?: recordSnapshot?.takeIf { it.id == selectedRecordId }
        val latestState by rememberUpdatedState(state)
        val latestActions by rememberUpdatedState(actions)
        LaunchedEffect(selectedRecordId, deletingRecordId, state.records, state.busy,
            state.runPersistence.initialized, state.runPersistence.historyUnavailable) {
            // Keep failures visible; return only after canonical removal.
            if (deletingRecordId != null && deletingRecordId == selectedRecordId &&
                state.runPersistence.initialized && !state.runPersistence.historyUnavailable &&
                !state.busy && state.records.none { it.id == deletingRecordId }) closeRecord()
        }
        var finishRunId by rememberSaveable { mutableStateOf<String?>(null) }
        var outdoorRunId by rememberSaveable { mutableStateOf<String?>(null) }
        var handledRunNavigationToken by rememberSaveable { mutableStateOf<Long?>(null) }
        var lastActiveId by remember { mutableStateOf(state.activeRun.id.takeIf { it.isNotBlank() }) }
        var mileageInformation by rememberSaveable { mutableStateOf(false) }
        var communitySubpage by rememberSaveable { mutableStateOf(false) }
        val communityDeep = tab == 3 && communitySubpage
        val outdoorFocused = tab == 2 && state.activeRun.phase != RunPhase.IDLE &&
            state.activeRun.id.isNotBlank() && outdoorRunId == state.activeRun.id
        val chatOpen = state.openedRoomId.isNotBlank()
        // One root page at a time, including asynchronous chat arrivals.
        val rootPage = when {
            chatOpen -> "chat"
            selectedRecordId != null -> "record"
            signIn -> "profile"
            appInformation -> "information"
            mileageInformation -> "mileage"
            settings -> "settings"
            closet -> "closet"
            else -> "main"
        }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(state.openedRoomId) { if (chatOpen) focusManager.clearFocus(force = true) }
        var completedId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.activeRun.phase, state.activeRun.id) {
            if (state.activeRun.phase == RunPhase.IDLE || outdoorRunId != state.activeRun.id) outdoorRunId = null
            if (state.activeRun.phase == RunPhase.IDLE) {
                lastActiveId?.let { completedId = it }
                lastActiveId = null
            } else if (state.activeRun.id.isNotBlank()) lastActiveId = state.activeRun.id
        }
        LaunchedEffect(completedId, state.records) {
            completedId?.let { id ->
                state.records.firstOrNull { it.id == id }?.let { confirmed ->
                    openRecord(confirmed)
                    if (requestedSaveCueId == id && confirmed.finishedAtMillis > 0 && !chatOpen &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) newlySavedRecordId = id
                    requestedSaveCueId = null
                    completedId = null
                }
            }
        }
        LaunchedEffect(state.runNavigationRequest, state.runPersistence.initialized, state.activeRun.id, state.activeRun.phase) {
            val request = state.runNavigationRequest ?: return@LaunchedEffect
            if (!state.runPersistence.initialized || request.token == handledRunNavigationToken) return@LaunchedEffect
            handledRunNavigationToken = request.token
            val openLive = request.target == RunNavigationTarget.ACTIVE && request.runId.isNotBlank() &&
                state.activeRun.id == request.runId && state.activeRun.phase != RunPhase.IDLE
            val preparation = request.target == RunNavigationTarget.PREPARE
            val result = request.record?.takeIf { request.target == RunNavigationTarget.RESULT &&
                it.id == request.runId && it.finishedAtMillis > 0 }
            if (openLive || preparation || result != null) {
                focusManager.clearFocus(force = true)
                settings = false
                closet = false
                signIn = false
                appInformation = false
                appInformationFromSettings = false
                profileFromSettings = false
                mileageInformation = false
                closeRecord()
                completedId = null
                crewNavigation = null
                partyNavigation = null
                communityNavigationEpoch++
                if (state.openedRoomId.isNotBlank()) actions.closeChat()
                if (state.error != null) actions.dismissError()
                tab = 2
                finishRunId = request.runId.takeIf { openLive && request.confirmFinish && !state.runPersistence.finishing &&
                    !(state.runPersistence.saving && state.runPersistence.issue != null) }
                if (result != null) openRecord(result)
            }
            actions.consumeRunNavigation(request.token)
        }
        LaunchedEffect(state.activeRun.id, state.activeRun.phase, state.runPersistence.finishing) {
            if (finishRunId != state.activeRun.id || state.activeRun.phase == RunPhase.IDLE || state.runPersistence.finishing)
                finishRunId = null
        }
        val tabs = listOf("홈", "탐색", "러닝", "크루", "나")
        val icons = listOf(R.drawable.ic_nav_home, R.drawable.ic_nav_explore, R.drawable.ic_nav_run, R.drawable.ic_nav_crew, R.drawable.ic_nav_me)
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Use this actual window's constraints: keep room for run data in
        // low landscape or enlarged-text short windows. Other tabs and regular
        // portrait keep their header/navigation; font scaling and recorder
        // callbacks are unchanged. Version/settings remain on the other tabs.
        val configuration = LocalConfiguration.current
        val shortWindow = (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                maxWidth > maxHeight && maxHeight <= 360.dp) ||
                (LocalDensity.current.fontScale >= 1.3f && maxHeight <= 640.dp)
        val compactActiveRun = tab == 2 && state.activeRun.phase != RunPhase.IDLE && shortWindow
        val compactExplore = tab == 1 && shortWindow
        PageTransitionFrame("$rootPage:${sharingRecordId != null}", animateEntrance = newlySavedRecordId == null) {
        if (rootPage == "main") mainStateHolder.SaveableStateProvider("main_navigation") {
        Scaffold(
            contentWindowInsets = when {
                communityDeep -> WindowInsets(0)
                outdoorFocused -> WindowInsets.safeDrawing
                else -> ScaffoldDefaults.contentWindowInsets
            },
            containerColor = RunmoniColors.Cream,
            topBar = {
                if (!communityDeep && !outdoorFocused) Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    if (!compactActiveRun) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val gutter = pageGutter(maxWidth)
                            val separateVersion = !compactExplore && (maxWidth < 360.dp ||
                                (LocalDensity.current.fontScale >= 1.3f && maxWidth < 480.dp))
                            Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                                .align(Alignment.TopCenter).padding(start = gutter, end = gutter, top = 4.dp, bottom = 4.dp)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconMark(size = 28.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("runmoni", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.8).sp),
                                        modifier = Modifier.weight(1f).testTag("app_wordmark"), maxLines = 1, softWrap = false)
                                    if (!separateVersion && !compactExplore) AppVersionShortcut(updates) { appInformationFromSettings = false; appInformation = true }
                                    val showSettingsUpdate = compactExplore && updates.updateAvailable
                                    IconButton(onClick = { settings = true }, modifier = Modifier.size(48.dp)
                                        .testTag("settings_button").semantics {
                                            if (showSettingsUpdate) stateDescription = "새 버전 업데이트 가능"
                                        }) {
                                        BadgedBox(badge = {
                                            if (showSettingsUpdate) Badge(Modifier.size(6.dp).testTag("compact_explore_update_dot"))
                                        }) {
                                            Icon(Icons.Outlined.Settings, "계정 및 러닝 설정", Modifier.size(22.dp))
                                        }
                                    }
                                }
                                if (separateVersion) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    AppVersionShortcut(updates) { appInformationFromSettings = false; appInformation = true }
                                }
                            }
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                if (!communityDeep && !outdoorFocused) Surface(color = RunmoniColors.Paper) {
                    val selectedContainer = RunmoniColors.PrimarySoft
                    val selectedInk = RunmoniColors.OnPrimarySoft
                    val selectedBorder = RunmoniColors.Primary
                    Column {
                        HorizontalDivider(color = RunmoniColors.Line)
                        if (compactActiveRun || compactExplore) {
                            // NavigationBar keeps its80dp default height. Measure
                            // the actual enlarged labels after OS horizontal
                            // insets, then reserve exactly the space they need.
                            BoxWithConstraints(Modifier.fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                                val navDensity = LocalDensity.current
                                val navLabelStyle = MaterialTheme.typography.labelMedium
                                val textMeasurer = rememberTextMeasurer()
                                val measuredLabels = tabs.flatMap { label ->
                                    listOf(FontWeight.Medium, FontWeight.Bold).map { weight ->
                                        textMeasurer.measure(AnnotatedString(label),
                                            style = navLabelStyle.copy(fontWeight = weight),
                                            maxLines = 1, softWrap = false)
                                    }
                                }
                                val labelWidth = with(navDensity) { measuredLabels.maxOf { it.size.width }.toDp() }
                                val labelHeight = with(navDensity) { measuredLabels.maxOf { it.size.height }.toDp() }
                                val navHeight = maxOf(48.dp, labelHeight + 8.dp)
                                val itemWidth = maxWidth / tabs.size.toFloat()
                                val showIcons = itemWidth >= labelWidth + 24.dp + 8.dp + 16.dp
                                val labelOnlyWidth = maxOf(48.dp, labelWidth + 8.dp)
                                val labelsFit = itemWidth >= labelOnlyWidth
                                val navigationScroll = rememberScrollState()
                                Row(Modifier.fillMaxWidth().height(navHeight)
                                    .then(if (labelsFit) Modifier else Modifier.horizontalScroll(navigationScroll))
                                    .selectableGroup()) {
                                    tabs.forEachIndexed { index, label ->
                                        // In a narrower multiwindow keep all five
                                        // full labels, omitting decorative icons.
                                        // Extreme widths/scales scroll48dp-or-wider
                                        // tabs instead of clipping/shrinking text.
                                        val item = if (labelsFit) Modifier.weight(1f) else Modifier.width(labelOnlyWidth)
                                        Row(item.fillMaxHeight()
                                            .clip(RunmoniShapes.Action)
                                            .then(if (tab == index) Modifier
                                                .background(selectedContainer)
                                                .border(1.dp, selectedBorder, RunmoniShapes.Action) else Modifier)
                                            .selectable(selected = tab == index, role = Role.Tab, onClick = {
                                                if (index == 3) { crewNavigation = null; partyNavigation = null; communityNavigationEpoch++ }
                                                tab = index
                                            }).testTag(listOf("nav_home", "nav_explore", "nav_run", "nav_crew", "nav_me")[index])
                                            .padding(horizontal = if (showIcons) 8.dp else 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(if (showIcons) 8.dp else 0.dp)) {
                                            if (showIcons) Icon(painterResource(icons[index]), null, Modifier.size(24.dp),
                                                tint = if (tab == index) selectedInk else RunmoniColors.Muted)
                                            Text(label, style = navLabelStyle.copy(
                                                fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium),
                                                modifier = Modifier.weight(1f), maxLines = 1, softWrap = false,
                                                textAlign = if (showIcons) TextAlign.Start else TextAlign.Center,
                                                color = if (tab == index) selectedInk else RunmoniColors.Muted)
                                        }
                                    }
                                }
                            }
                        } else {
                            val navDensity = LocalDensity.current
                            // Keep a full label line below equal 48dp icon areas. The
                            // text's real scaled line height determines any extra height.
                            val navContentHeight = maxOf(72.dp, 48.dp + 4.dp + with(navDensity) {
                                MaterialTheme.typography.labelMedium.lineHeight.toDp()
                            } + 8.dp)
                            val navBottomInset = with(navDensity) { NavigationBarDefaults.windowInsets.getBottom(this).toDp() }
                            NavigationBar(modifier = Modifier.heightIn(min = navContentHeight + navBottomInset).selectableGroup(),
                                containerColor = RunmoniColors.Paper, tonalElevation = 0.dp) {
                                tabs.forEachIndexed { index, label ->
                                    Column(Modifier.weight(1f).heightIn(min = navContentHeight)
                                        .selectable(selected = tab == index, role = Role.Tab, onClick = {
                                            if (index == 3) { crewNavigation = null; partyNavigation = null; communityNavigationEpoch++ }
                                            tab = index
                                        }).testTag(listOf("nav_home", "nav_explore", "nav_run", "nav_crew", "nav_me")[index])
                                        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center) {
                                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                                            if (tab == index) Box(Modifier.width(56.dp).height(36.dp)
                                                .background(selectedContainer, RunmoniShapes.Action)
                                                .border(1.dp, selectedBorder, RunmoniShapes.Action))
                                            Icon(painterResource(icons[index]), null, Modifier.size(24.dp),
                                                tint = if (tab == index) selectedInk else RunmoniColors.Muted)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(label, style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium), maxLines = 1, softWrap = false,
                                            textAlign = TextAlign.Center, color = if (tab == index) RunmoniColors.Primary else RunmoniColors.Muted,
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(state, onRun = { tab = 2 }, onCrew = { crewNavigation = null; partyNavigation = null; communityNavigationEpoch++; tab = 3 }, onRecord = ::openRecord, onRetrySave = actions::retryRunPersistence, onWeather = actions::refreshWeather, onWeatherEntry = actions::prepareHomeConditions, onParty = { crewNavigation = it.crewId; partyNavigation = it.id; communityNavigationEpoch++; tab = 3 })
                    1 -> ExploreScreen(state, actions, onSignIn = { profileFromSettings = false; signIn = true }, onRooms = { crewNavigation = null; partyNavigation = null; communityNavigationEpoch++; tab = 3 }, onCrew = { crewNavigation = it; partyNavigation = null; communityNavigationEpoch++; tab = 3 })
                    2 -> RunningScreen(state, actions, useCompactPanel = compactActiveRun,
                        outdoorFocused = outdoorFocused, onOutdoorFocus = { enabled ->
                            outdoorRunId = state.activeRun.id.takeIf { enabled }
                        },
                        onCrew = { crewNavigation = null; partyNavigation = null; communityNavigationEpoch++; tab = 3 },
                        onProfile = { profileFromSettings = false; signIn = true },
                        onFinish = { finishRunId = state.activeRun.id })
                    3 -> CommunityRoomsScreen(state, actions, crewNavigation, partyNavigation, communityNavigationEpoch,
                        onSubpageChanged = { communitySubpage = it })
                    4 -> ProfileScreen(state, actions, onRun = { tab = 2 }, onRecord = ::openRecord, onSettings = { settings = true }, updates = updates, onAppInformation = { appInformationFromSettings = false; appInformation = true }, onCloset = { closet = true }, onProfile = { profileFromSettings = false; signIn = true }, onMileageHelp = { mileageInformation = true })
                }
            }
        }
        }
        if (rootPage == "closet") ClosetDialog(state.profile, actions) { closet = false }
        if (rootPage == "settings") SettingsScreen(state, actions, onDismiss = { settings = false }, onSignIn = { profileFromSettings = true; settings = false; signIn = true }, onAppInformation = { appInformationFromSettings = true; settings = false; appInformation = true }, updates = updates, onOpenProfile = { profileFromSettings = true; settings = false; signIn = true }, scrollState = settingsScroll)
        if (rootPage == "information") AppInformationDialog(updates, onCheckUpdates, onDismiss = { appInformation = false; if (appInformationFromSettings) settings = true; appInformationFromSettings = false }, promptUpdateInitially = updates.updateAvailable, onExternalLink = onExternalAppLink)
        if (rootPage == "profile") SignInDialog(state, actions) { signIn = false; if (profileFromSettings) settings = true; profileFromSettings = false }
        if (rootPage == "mileage") MileageExplanationDialog(onDismiss = { mileageInformation = false },
            serverWallet = state.signedIn && !state.communityGuest)
        RunnerSharingConsent(state, actions)
        if (rootPage == "chat") ChatScreen(state, actions)
        if (rootPage == "record") {
            if (selectedRecord != null) {
                val recordActions = object : AppActions by actions {
                    override fun deleteRun(id: String) {
                        if (id == selectedRecordId && state.records.any { it.id == id } && !state.busy) {
                            recordOpening = Any()
                            deletingRecordId = id
                            actions.deleteRun(id)
                        }
                    }
                }
                if (sharingRecordId == selectedRecord.id) {
                    recordStateHolder.SaveableStateProvider("share_${selectedRecord.id}") { key(selectedRecord.id) {
                        RunShareScreen(selectedRecord, state.profile.avatar, state.profile.name,
                            recordAvailable = RunShareAccess.allowed(state, selectedRecord),
                            authorize = { actions.canShareRun(selectedRecord) }, onBack = { sharingRecordId = null })
                    } }
                } else recordStateHolder.SaveableStateProvider("record_${selectedRecord.id}") {
                    val scope = rememberCoroutineScope()
                    val opening = recordOpening
                    val account = state.cloud.uid
                    var preparingTraining by remember(opening) { mutableStateOf(false) }
                    fun isCurrentRecord() = recordOpening === opening && selectedRecordId == selectedRecord.id &&
                        sharingRecordId == null && latestState.openedRoomId.isBlank() && latestActions === actions &&
                        latestState.cloud.uid == account && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        RunTrainingPreparation.available(latestState, selectedRecord)
                    val prepareAgain: () -> Unit = {
                        if (!preparingTraining && isCurrentRecord()) {
                            preparingTraining = true
                            scope.launch {
                                try {
                                    if (actions.prepareTrainingAgain(selectedRecord, ::isCurrentRecord) && isCurrentRecord()) {
                                        closeRecord()
                                        tab = 2
                                    }
                                } finally {
                                    preparingTraining = false
                                }
                            }
                        }
                    }
                    RunRecordScreen(selectedRecord, recordActions, state.cloud, state.runPersistence,
                        operationBusy = state.busy,
                        onShare = { if (actions.canShareRun(selectedRecord)) sharingRecordId = selectedRecord.id },
                        showNewlySavedMotion = newlySavedRecordId == selectedRecord.id,
                        onNewlySavedMotionShown = { if (newlySavedRecordId == selectedRecord.id) newlySavedRecordId = null },
                        comparisonRecords = state.records,
                        onPrepareAgain = prepareAgain.takeIf { RunTrainingPreparation.available(state, selectedRecord) },
                        onBack = ::closeRecord)
                }
            } else {
                BackHandler(onBack = ::closeRecord)
                Surface(Modifier.fillMaxSize(), color = RunmoniColors.Cream) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = ::closeRecord, modifier = Modifier.heightIn(min = 48.dp).testTag("close_record")) { Text("뒤로") }
                        Text(if (state.runPersistence.initialized) "이 기기에서 기록을 찾을 수 없어요." else "기록을 불러오는 중이에요.",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        }
        if (finishRunId != null && finishRunId == state.activeRun.id && state.activeRun.phase != RunPhase.IDLE &&
            state.runPersistence.initialized && !state.runPersistence.finishing) {
            DismissibleAlertDialog(onDismissRequest = { finishRunId = null }, title = { Text("오늘의 러닝을 마칠까요?") },
                text = { DialogMessage("${distance(state.activeRun.distanceMeters)} km · ${duration(state.activeRun.activeDurationMillis)}\n저장하면 결과를 바로 볼 수 있어요.") },
                confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = {
                    requestedSaveCueId = state.activeRun.id
                    finishRunId = null
                    actions.finishRun(state.activeRun.id)
                },
                    enabled = !(state.runPersistence.saving && state.runPersistence.issue != null),
                    modifier = Modifier.testTag("confirm_finish_run")) { Text("저장하고 마치기") } },
                dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { finishRunId = null }, modifier = Modifier.testTag("cancel_finish_run")) { Text("취소") } })
        }
        state.error?.let { error -> DismissibleAlertDialog(onDismissRequest = actions::dismissError, title = { Text("확인이 필요해요") }, text = { DialogMessage(error) }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = actions::dismissError) { Text("확인") } }) }
        }
    }
}

@Composable
private fun RunningScreen(state: AppState, actions: AppActions, useCompactPanel: Boolean,
    outdoorFocused: Boolean, onOutdoorFocus: (Boolean) -> Unit,
    onCrew: () -> Unit, onProfile: () -> Unit, onFinish: () -> Unit) {
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var goalPickerOpen by rememberSaveable { mutableStateOf(false) }
    var intervalPickerOpen by rememberSaveable { mutableStateOf(false) }
    val run = state.activeRun
    val supportsOutdoor = run.phase != RunPhase.IDLE && run.id.isNotBlank()
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val compactLive = run.phase != RunPhase.IDLE && (largeText || useCompactPanel)
    val persistence = state.runPersistence
    val permissionMissing = state.locationAccess.precisePermissionGranted == false
    val gpsDisabled = state.locationAccess.gpsEnabled == false
    val accuracy = run.accuracyMeters?.takeIf { !permissionMissing && !gpsDisabled && it.isFinite() && it >= 0f }
    val gpsReady = accuracy != null && accuracy <= 30f
    val liveMetrics = run.phase == RunPhase.RUNNING && gpsReady && !persistence.finishing
    val currentPace = run.currentPaceSecondsPerKm.takeIf { liveMetrics }
    val speedKmh = run.currentSpeedMetersPerSecond?.times(3.6)?.takeIf {
        liveMetrics && it.isFinite() && it >= 0
    }
    val gpsLabel = when { permissionMissing -> "위치 권한 필요"; gpsDisabled -> "GPS 꺼짐";
        gpsReady -> "GPS 수신 중"; else -> run.gpsStatus }
    val gpsTone = when {
        permissionMissing || gpsDisabled -> RunGpsTone.Attention
        persistence.finishing || run.phase == RunPhase.PAUSED || run.phase == RunPhase.RECOVERY -> RunGpsTone.Neutral
        gpsReady -> RunGpsTone.Ready
        run.phase == RunPhase.RUNNING -> RunGpsTone.Attention
        else -> RunGpsTone.Neutral
    }
    val statusTitle = when {
        persistence.finishing -> "러닝 마무리 중"
        persistence.issue == RunPersistenceIssue.CHECKPOINT -> "기록 저장 확인 필요"
        run.phase == RunPhase.IDLE -> "러닝 준비"
        run.phase == RunPhase.PAUSED && run.pauseReason == PauseReason.AUTOMATIC -> "자동 일시정지 중"
        run.phase == RunPhase.PAUSED -> if (compactLive) "일시정지 중" else "잠시 쉬는 중"
        run.phase == RunPhase.RECOVERY -> "러닝 이어가기"
        else -> if (compactLive) "기록 중" else "러닝 기록 중"
    }
    BackHandler(enabled = outdoorFocused) { onOutdoorFocus(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).testTag("running_scroll_viewport")) {
            if (outdoorFocused) ScreenContent(spacing = RunmoniLayout.ContentGap, topPadding = RunmoniLayout.ContentGap,
                centerWhenFits = !permissionMissing && !gpsDisabled && persistence.issue == null) {
                val outdoorGps = when {
                    permissionMissing || gpsDisabled -> gpsLabel
                    run.phase == RunPhase.PAUSED -> "GPS 기록 일시정지"
                    run.phase == RunPhase.RECOVERY -> "GPS 확인 대기"
                    else -> gpsLabel.substringBefore(" · ")
                }
                OutdoorRunPanel(run, statusTitle, outdoorGps, currentPace, gpsTone, onClose = { onOutdoorFocus(false) })
                RunPersistenceNotice(persistence, actions::retryRunPersistence)
                if (permissionMissing || gpsDisabled) LocationReadinessCard(permissionMissing, actions)
            } else ScreenContent(spacing = if (compactLive) RunmoniLayout.ContentGap else RunmoniLayout.SectionGap,
                centerWhenFits = run.phase == RunPhase.IDLE,
                topPadding = if (compactLive) RunmoniLayout.TightGap else RunmoniLayout.ContentGap) {
                if (!compactLive) Column(verticalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(statusTitle, style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.weight(1f).testTag("run_status").semantics { heading() })
                        if (supportsOutdoor) OutdoorFocusButton(onOpen = { onOutdoorFocus(true) }, compact = false)
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val gpsInk = when (gpsTone) {
                            RunGpsTone.Ready -> RunmoniColors.Green
                            RunGpsTone.Attention -> RunmoniColors.EnvironmentCaution
                            RunGpsTone.Neutral -> RunmoniColors.Muted
                        }
                        StatusPill(gpsLabel, gpsInk, "gps_status")
                        accuracy?.let { Text("정확도 ±${it.toInt()}m", modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted) }
                    }
                }
                if (run.phase == RunPhase.IDLE) {
                    Column(verticalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
                        AdaptiveActions(
                            first = { modifier ->
                                OutlinedButton(onClick = { goalPickerOpen = true }, shape = RunmoniShapes.Action,
                                    modifier = modifier.heightIn(min = 48.dp).testTag("choose_run_goal")) {
                                    Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(state.plannedRunGoal?.let { "목표 ${it.label} 변경" } ?: "오늘 목표 설정")
                                }
                            },
                            second = { modifier ->
                                OutlinedButton(onClick = { intervalPickerOpen = true }, shape = RunmoniShapes.Action,
                                    modifier = modifier.heightIn(min = 48.dp).testTag("choose_run_interval")) {
                                    Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.plannedRunInterval == null) "시간 인터벌" else "인터벌 변경")
                                }
                            },
                        )
                        state.plannedRunInterval?.let { SmallNote(runIntervalDescription(it)) }
                    }
                }
                if (run.phase == RunPhase.IDLE) Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.3f) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RunRecordingDescription()
                                    StudioAvatarStage(state.profile.avatar, Modifier.width(64.dp).height(96.dp))
                                }
                            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                StudioAvatarStage(state.profile.avatar, Modifier.width(88.dp).height(132.dp))
                                RunRecordingDescription(Modifier.weight(1f))
                            }
                        }
                        HorizontalDivider(color = RunmoniColors.Outline)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PhoneAndroid, null, tint = RunmoniColors.Primary)
                            Text("러닝 기록은 이 기기에 저장돼요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Navy)
                        }
                    }
                } else RunningMetricPanel(contentPadding = PaddingValues(if (compactLive) 12.dp else 20.dp),
                    spacing = if (compactLive) RunmoniLayout.TightGap else RunmoniLayout.ContentGap) {
                    if (compactLive) {
                        CompactRunStatus(statusTitle, gpsLabel.substringBefore(" · "), gpsTone)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("거리", style = MaterialTheme.typography.labelSmall, color = RunmoniTrack.Detail,
                                modifier = Modifier.weight(1f))
                            if (supportsOutdoor) OutdoorFocusButton(onOpen = { onOutdoorFocus(true) }, compact = true)
                        }
                        DistanceNumber(distance(run.distanceMeters), RunmoniTrack.Highlight,
                            Modifier.testTag("run_distance"), maxSize = 40f, unit = "km")
                    } else BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val showAvatar = maxWidth >= 280.dp
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RunmoniLayout.ContentGap)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
                                Text("달린 거리 (km)", color = RunmoniTrack.Detail, style = MaterialTheme.typography.bodyMedium)
                                DistanceNumber(distance(run.distanceMeters), RunmoniTrack.Highlight, Modifier.testTag("run_distance"))
                            }
                            if (showAvatar) AvatarMini(state.profile.avatar, Modifier.width(64.dp).height(96.dp)
                                .testTag("run_avatar"), running = run.phase == RunPhase.RUNNING, decorative = true)
                        }
                    }
                    HorizontalDivider(color = RunmoniTrack.Detail.copy(alpha = .4f))
                    MetricGroup(listOf(UiMetric(if (compactLive) "시간" else "러닝 시간",
                        duration(run.activeDurationMillis), tag = "run_duration"),
                        UiMetric(if (compactLive) "페이스" else "현재 페이스", pace(currentPace), "/km", "run_pace")),
                        onDark = true, compact = compactLive)
                    run.interval?.let { RunIntervalLive(it, run.activeDurationMillis) }
                    if (compactLive) run.goal?.let { ActiveRunGoal(it, run.distanceMeters, run.activeDurationMillis, compact = true) }
                    if (!compactLive) RunSecondaryMetrics(run, speedKmh, onTrack = true)
                }
                if (!compactLive) run.goal?.let { ActiveRunGoal(it, run.distanceMeters, run.activeDurationMillis) }
                if (compactLive) {
                    RunSecondaryMetrics(run, speedKmh, onTrack = false)
                    accuracy?.let { SmallNote("GPS 정확도 ±${it.toInt()}m") }
                }
                if (run.phase == RunPhase.IDLE) RunSharingPreparation(state, actions, onProfile)
                if (run.phase == RunPhase.IDLE && (permissionMissing || gpsDisabled)) LocationReadinessCard(permissionMissing, actions)
                RunPersistenceNotice(persistence, actions::retryRunPersistence)
                if (run.phase != RunPhase.IDLE && (permissionMissing || gpsDisabled)) LocationReadinessCard(permissionMissing, actions)
                if (run.phase != RunPhase.IDLE) MapPane(run.points, modifier = Modifier.fillMaxWidth()
                    .height(if (LocalDensity.current.fontScale >= 1.3f) 360.dp else 188.dp).clip(RunmoniShapes.Surface))
                if (state.runNotificationAccess.isBlocked) {
                    Column(Modifier.fillMaxWidth().testTag("run_notification_blocked")
                        .semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("러닝 알림이 꺼져 있어요", style = MaterialTheme.typography.titleMedium)
                        Text("상태 표시줄에서 기록을 보고 조작하려면 알림을 켜 주세요.", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        TextButton(shape = RunmoniShapes.Action, onClick = actions::openRunNotificationSettings, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("run_notification_settings")) { Text("알림 설정") }
                    }
                }
                if (run.phase != RunPhase.IDLE && !state.runNotificationAccess.isBlocked) {
                    Text("상단 알림을 펼치면 일시정지·재개할 수 있어요. 종료는 앱에서 확인해요.",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                        modifier = Modifier.testTag("run_notification_guide"))
                }
                if (run.phase == RunPhase.PAUSED && !persistence.finishing && persistence.issue != RunPersistenceIssue.CHECKPOINT)
                    SmallNote(when (run.pauseReason) {
                        PauseReason.AUTOMATIC -> "멈춰 있어 러닝 시간을 잠시 멈췄어요. 움직임이 확인되면 자동으로 이어져요. 지금 시작하려면 ‘계속 달리기’를 누르세요."
                        PauseReason.MANUAL -> "직접 일시정지했어요. 다시 달릴 때 ‘계속 달리기’를 누르세요."
                        null -> "러닝이 멈춰 있어요. 저장 상태를 확인한 뒤 이어갈 수 있어요."
                    })
                if (run.phase == RunPhase.RECOVERY) SmallNote("앱이 중단되기 전 저장한 지점까지만 복구해요. 중단된 구간의 거리를 추정해 더하지 않아요.")

                TextButton(shape = RunmoniShapes.Action, onClick = { optionsExpanded = !optionsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("run_options")
                        .semantics { stateDescription = if (optionsExpanded) "펼침" else "접힘" }) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (optionsExpanded) "러닝 옵션 접기" else "러닝 옵션")
                    Icon(if (optionsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                if (optionsExpanded) {
                    OutlinedButton(onClick = actions::addRunWidget, shape = RunmoniShapes.Action,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_run_widget")) {
                        Icon(Icons.Outlined.Widgets, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("홈 화면에 러닝 위젯 추가")
                    }
                    Text("러닝을 시작한 뒤 홈 화면에서도 기록을 확인해요.",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    if (run.interval != null || state.plannedRunInterval != null) {
                        SmallNote("인터벌에서는 회복 시간을 재기 위해 자동 일시정지를 사용하지 않아요.")
                    } else {
                        ToggleRow("자동 일시정지", "정확한 GPS로 10초 동안 멈춤이 확인되면 시간을 멈추고, 움직이면 이어가요",
                            state.autoPause, actions::setAutoPause)
                    }
                    RunLapSettings(state, actions)
                }
                val joined = state.parties.firstOrNull { it.joined && !it.cancelled }
                if (state.communityBackend == CommunityBackend.HTTP && (joined != null || state.audio.roomId.isNotBlank())) AudioPanel(state.audio, actions, state.audio.roomId.ifBlank { "party:${joined!!.id}" })
                else if (optionsExpanded) TextButton(shape = RunmoniShapes.Action, onClick = onCrew, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("함께 달릴 크루 둘러보기") }
            }
        }
        Surface(color = RunmoniColors.Paper, modifier = Modifier.fillMaxWidth().testTag("run_control_bar")) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = RunmoniColors.Line)
            BoxWithConstraints(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()) {
              Box(Modifier.fillMaxWidth()
                .padding(horizontal = pageGutter(maxWidth), vertical = if (compactLive) RunmoniLayout.TightGap else 12.dp)) {
                if (persistence.finishing) {
                    Button(onClick = actions::retryRunPersistence, enabled = persistence.canRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("retry_finish_run"), shape = RunmoniShapes.Action) {
                        Text(if (persistence.saving) "기기에 저장 중" else "저장 다시 시도", style = MaterialTheme.typography.titleMedium)
                    }
                } else if (run.phase == RunPhase.IDLE) {
                    Button(onClick = actions::startRun, enabled = !persistence.blocksNewRun && !(persistence.saving && persistence.issue != null), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("start_run"), shape = RunmoniShapes.Action) {
                        Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("러닝 시작", style = MaterialTheme.typography.titleMedium)
                    }
                } else AdaptiveActions(
                    first = { modifier -> Button(onClick = { when (run.phase) { RunPhase.RUNNING -> actions.pauseRun(); RunPhase.PAUSED -> actions.resumeRun(); else -> actions.recoverRun() } },
                        enabled = !persistence.finishing && !(persistence.saving && persistence.issue != null) && persistence.initialized,
                        modifier = modifier.heightIn(min = 56.dp).testTag(when (run.phase) { RunPhase.RUNNING -> "pause_run"; RunPhase.PAUSED -> "resume_run"; else -> "recover_run" }), shape = RunmoniShapes.Action) {
                        Text(when (run.phase) { RunPhase.RUNNING -> "일시정지"; RunPhase.PAUSED -> "계속 달리기"; else -> "이어 달리기" })
                    } },
                    second = { modifier -> OutlinedButton(onClick = onFinish, enabled = !persistence.finishing && !(persistence.saving && persistence.issue != null) && persistence.initialized, modifier = modifier.heightIn(min = 56.dp).testTag("finish_run"), shape = RunmoniShapes.Action) { Text("러닝 마치기") } },
                )
              }
                }
            }
            }
        }
    if (goalPickerOpen && run.phase == RunPhase.IDLE) RunGoalPicker(state.plannedRunGoal, onSelect = {
        actions.setRunGoal(it)
        goalPickerOpen = false
    }, onDismiss = { goalPickerOpen = false })
    if (intervalPickerOpen && run.phase == RunPhase.IDLE) RunIntervalPicker(state.plannedRunInterval, onSelect = {
        actions.setRunInterval(it)
        intervalPickerOpen = false
    }, onDismiss = { intervalPickerOpen = false })
}

@Composable
private fun CompactRunStatus(title: String, gpsLabel: String, gpsTone: RunGpsTone) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = RunmoniTrack.OnBoard, modifier = Modifier.testTag("run_status").semantics { heading() })
        TrackGpsStatus(gpsLabel, gpsTone)
    }
}

@Composable
private fun RunSecondaryMetrics(run: ActiveRun, speedKmh: Double?, onTrack: Boolean) {
    val ink = if (onTrack) RunmoniTrack.OnBoard else RunmoniColors.Ink
    val detail = if (onTrack) RunmoniTrack.Detail else RunmoniColors.Muted
    Column(verticalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RunmoniLayout.TightGap), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("현재 속도", color = detail, style = MaterialTheme.typography.bodyMedium)
            Text(if (speedKmh == null) "— km/h" else String.format(Locale.KOREA, "%.1f km/h", speedKmh),
                color = ink, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.testTag("run_speed"))
        }
        Text("평균 ${averagePace(run.distanceMeters, run.activeDurationMillis)} /km",
            color = detail, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun RunRecordingDescription(modifier: Modifier = Modifier) {
    Text("화면을 꺼도 거리·시간·GPS 경로 기록을 이어가요.", modifier = modifier,
        style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun RunSharingPreparation(state: AppState, actions: AppActions, onProfile: () -> Unit) {
    val sharing = state.sharingPreferenceEnabled || state.publicLocation
    val supported = state.communityBackend == CommunityBackend.FIREBASE
    val canPrepare = supported && state.signedIn && state.communityUid.isNotBlank()
    if (sharing || canPrepare) {
        val description = if (canPrepare) "출발 전에 설정하세요. 러닝 중에만 약 100m 단위로 보여요."
            else "이 기기에 저장된 위치 공유 설정을 끌 수 있어요."
        ToggleRow("근처 러너에게 위치 공유", description,
            sharing, actions::setPublicLocation, tag = "prepare_run_sharing")
    } else if (supported) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("근처 러너에게 위치 공유", style = MaterialTheme.typography.titleMedium)
            Text("프로필을 연결하면 출발 전에 공유를 설정할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            TextButton(onClick = onProfile, shape = RunmoniShapes.Action,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("prepare_run_sharing_profile")) { Text("프로필 연결") }
        }
    }
}

@Composable
private fun LocationReadinessCard(permissionMissing: Boolean, actions: AppActions) {
    Surface(color = RunmoniColors.EnvironmentCautionWash, contentColor = RunmoniColors.Ink, shape = RunmoniShapes.Surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (permissionMissing) "정확한 위치 권한이 필요해요" else "GPS가 꺼져 있어요",
                style = MaterialTheme.typography.titleMedium, color = RunmoniColors.EnvironmentCaution)
            Text(if (permissionMissing) "러닝 시작 시 위치 권한을 허용해 주세요. 권한 요청이 나오지 않으면 앱 설정에서 ‘정확한 위치’를 켜 주세요."
                else "GPS를 켜면 새 위치부터 경로를 기록해요. 신호가 없는 구간의 거리는 추정해 더하지 않아요.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(shape = RunmoniShapes.Action, onClick = if (permissionMissing) actions::openAppPermissionSettings else actions::openLocationSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(if (permissionMissing) "app_permission_settings" else "location_settings")) {
                Text(if (permissionMissing) "앱 권한 설정" else "위치 설정 열기")
            }
        }
    }
}

@Composable
private fun CrewScreen(state: AppState, actions: AppActions, onSignIn: () -> Unit) {
    CommunityRoomsScreen(state, actions)
}

@Composable
internal fun AudioPanel(audio: AudioUiState, actions: AppActions, roomId: String) {
    var sourcePicker by remember(roomId) { mutableStateOf(false) }
    val thisRoom = audio.connected && audio.roomId == roomId
    val canBroadcast = audio.canDj && thisRoom
    LaunchedEffect(canBroadcast) { if (!canBroadcast) sourcePicker = false }
    Surface(shape = RunmoniShapes.Surface, color = RunmoniColors.Paper) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Headphones, null); Spacer(Modifier.width(8.dp)); Text("음성 · 함께 듣기", style = MaterialTheme.typography.titleMedium) }
            Text(audio.status, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            AdaptiveActions(
                first = { modifier -> Button(shape = RunmoniShapes.Action, onClick = { if (thisRoom) actions.disconnectVoice() else actions.connectVoice(roomId) }, enabled = !audio.connecting, modifier = modifier.heightIn(min = 56.dp).testTag("join_voice")) { Text(if (thisRoom) "음성방 나가기" else if (audio.connecting) "연결 중" else "음성방 참여") } },
                second = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.setMicrophone(!audio.microphoneEnabled) }, enabled = thisRoom, modifier = modifier.heightIn(min = 56.dp).testTag("toggle_microphone")) { Text(if (audio.microphoneEnabled) "마이크 켜짐" else "마이크 꺼짐") } },
            )
            if (thisRoom) {
                Text("음성 음량 · ${(audio.voiceVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.voiceVolume, onValueChange = actions::setVoiceVolume, modifier = Modifier.testTag("voice_volume").semantics { contentDescription = "음성 음량"; stateDescription = "${(audio.voiceVolume * 100).toInt()}퍼센트" })
                Text("음악 음량 · ${(audio.musicVolume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = audio.musicVolume, onValueChange = actions::setMusicVolume, modifier = Modifier.testTag("music_volume").semantics { contentDescription = "음악 음량"; stateDescription = "${(audio.musicVolume * 100).toInt()}퍼센트" })
            }
            Text(audio.sourceLabel, style = MaterialTheme.typography.bodyMedium)
            if (canBroadcast) {
                AdaptiveActions(
                    first = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.shareExternalMusic("com.google.android.apps.youtube.music") }, modifier = modifier.heightIn(min = 56.dp).testTag("share_youtube_music")) { Text("YouTube Music") } },
                    second = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = { actions.shareExternalMusic("com.sec.android.app.music") }, modifier = modifier.heightIn(min = 56.dp).testTag("share_samsung_music")) { Text("Samsung Music") } },
                )
                AdaptiveActions(
                    first = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = actions::selectMusicFile, modifier = modifier.heightIn(min = 48.dp).testTag("select_music")) { Text("음악 파일 선택") } },
                    second = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = { sourcePicker = true }, modifier = modifier.heightIn(min = 48.dp).testTag("other_music_apps")) { Text("다른 음악 앱") } },
                )
                if (audio.broadcasting) TextButton(shape = RunmoniShapes.Action, onClick = actions::stopMusic, modifier = Modifier.heightIn(min = 48.dp).testTag("stop_music")) { Text("음악 공유 중지") }
                SmallNote("외부 앱이 오디오 캡처를 허용해야 해요. 무음이거나 잠금 중 공유가 중단되면 안내 상태를 확인해 주세요.")
            } else if (thisRoom) SmallNote("음악 방송은 크루장과 DJ가 제어해요. 내 음량은 따로 바꿀 수 있어요.")
        }
    }
    if (sourcePicker && canBroadcast) DismissibleAlertDialog(onDismissRequest = { sourcePicker = false }, title = { Text("공유할 음악 앱") }, text = { Column {
        listOf("Spotify" to "com.spotify.music", "멜론" to "com.iloen.melon", "지니" to "com.ktmusic.geniemusic", "FLO" to "skplanet.musicmate").forEach { (name, packageName) -> TextButton(shape = RunmoniShapes.Action, onClick = { actions.shareExternalMusic(packageName); sourcePicker = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share_music_$packageName")) { Text(name) } }
        SmallNote("각 앱이 캡처를 허용해야 실제 오디오가 전달돼요. 지원 여부는 기기에서 확인해 주세요.")
    } }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { sourcePicker = false }) { Text("닫기") } })
}

@Composable
internal fun ScreenContent(spacing: Dp = RunmoniLayout.PageGap, centerWhenFits: Boolean = false,
    topPadding: Dp = RunmoniLayout.TopInset, scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = pageGutter(maxWidth)
        Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
            .verticalScroll(scrollState).testTag("screen_scroll")
            .heightIn(min = maxHeight)
            .padding(start = gutter, end = gutter, top = topPadding, bottom = RunmoniLayout.PageGap),
            verticalArrangement = Arrangement.spacedBy(spacing,
                if (centerWhenFits) Alignment.CenterVertically else Alignment.Top), content = content)
    }
}

@Composable
internal fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
    }
}

@Composable
internal fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (action != null && (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                TextButton(shape = RunmoniShapes.Action, onClick = onClick,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            if (action != null) TextButton(shape = RunmoniShapes.Action, onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

@Composable
internal fun StatusPill(label: String, color: Color, tag: String = "") {
    Surface(color = color.copy(alpha = 0.10f), shape = RunmoniShapes.Status, modifier = Modifier.testTag(tag)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(label, color = color, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
internal fun EmptyCard(title: String, message: String, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper, RunmoniShapes.Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        if (action != null) TextButton(shape = RunmoniShapes.Action, onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(action); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
    }
}

@Composable
internal fun SmallNote(message: String) { Text(message, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true, tag: String = "") {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked).testTag(tag).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted) }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun RunLapSettings(state: AppState, actions: AppActions) {
    ToggleRow("음성 기록 안내", "1km 거리와 인터벌 단계 전환을 음성으로 알려줘요", state.voiceGuidance, actions::setVoiceGuidance)
    ToggleRow("구간 페이스 음성", "거리와 함께 방금 달린 1km 페이스를 들어요", state.lapPaceGuidance,
        actions::setLapPaceGuidance, enabled = state.voiceGuidance, tag = "run_lap_pace_guidance")
    ToggleRow("1km 진동", "1km를 지날 때 진동으로 알려줘요", state.lapVibration,
        actions::setLapVibration, tag = "run_lap_vibration")
}

@Composable
internal fun LocationSharingToggle(state: AppState, actions: AppActions) {
    ToggleRow("러닝 위치 공유", "미리 설정하면 다음 러닝부터 약 100m 단위로 공유해요", state.sharingPreferenceEnabled || state.publicLocation,
        actions::setPublicLocation, enabled = state.signedIn && !state.sharing.busy, tag = "share_location")
}

@Composable
internal fun PartySummary(party: Party, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = "파티 참가 정보 보기", onClick = onClick).testTag("party_${party.id}")) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dateTime(party.startsAtMillis), style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Primary)
                if (party.cancelled || party.joined) Text(if (party.cancelled) "취소됨" else "참가 중",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (party.cancelled) MaterialTheme.colorScheme.error else RunmoniColors.Primary)
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(party.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted, modifier = Modifier.size(24.dp))
            }
            Text("${party.distanceKm} km · ${party.paceLabel} · ${party.participantCount}/${party.capacity}명", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            val meeting = party.meetingLabel.trim()
            val kind = if (party.kind == PartyKind.LOCAL) "현장 모임" else "각자 출발"
            Text(if (meeting.isBlank() || meeting == kind) kind else "$kind · $meeting", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        }
        HorizontalDivider(color = RunmoniColors.Line)
    }
}

@Composable
internal fun RecordRow(record: RunRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "러닝 기록 자세히 보기", onClick = onClick).testTag("record_${record.id}").padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.labelMedium, color = RunmoniColors.Muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(buildAnnotatedString {
                    append(distance(record.distanceMeters))
                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = RunmoniColors.Muted)) {
                        append(" km")
                    }
                }, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = RunmoniColors.Muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Timer, null, Modifier.size(16.dp), tint = RunmoniColors.Muted)
                    Text(duration(record.activeDurationMillis), style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = RunmoniColors.Muted)
                    Text("${averagePace(record.distanceMeters, record.activeDurationMillis)} /km",
                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
    }
}

internal fun topColor(variant: Int): Color = when (variant % 4) { 1 -> Color(0xFF356F78); 2 -> Color(0xFF547FB3); 3 -> Color(0xFFD6AE48); else -> Color(0xFFF16D55) }
@Composable
internal fun AvatarMini(avatar: AvatarStyle, modifier: Modifier, running: Boolean = false, decorative: Boolean = false,
    onFrameRendered: ((Int) -> Unit)? = null) = MiniMi(avatar, modifier, running,
    if (decorative) null else "나의 러닝 미니미", onFrameRendered = onFrameRendered)
internal fun accessoryName(variant: Int) = when (variant) { 1 -> "headband"; 2 -> "cap"; 3 -> "headphones"; else -> "none" }
internal fun distance(meters: Double) = String.format(Locale.KOREAN, "%.2f", meters / 1000)
internal fun duration(millis: Long): String { val seconds = millis.coerceAtLeast(0) / 1000; return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60) }
internal fun pace(seconds: Int?) = if (seconds == null || seconds <= 0) "—" else "%d′%02d″".format(seconds / 60, seconds % 60)
internal fun averagePace(meters: Double, millis: Long) = if (meters < 10 || millis <= 0) "—" else pace((millis / meters).toInt())
internal fun localDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
internal fun dateTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN))
internal fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
