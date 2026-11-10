package com.runmoni.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.runmoni.app.tracking.RunNotifications
import com.runmoni.app.ui.RunmoniRoot
import com.runmoni.app.ui.rememberAppUpdates
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var pendingPermission: UiRequest? = null
    private var capturePackage = ""
    private var handledRunNotificationEvent: String? = null
    private val locationChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshLocationAccess() }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshLocationAccess()
        val pending = pendingPermission; pendingPermission = null
        when (pending) {
            is UiRequest.RunPermission -> {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) model.beginWithPermission(pending.recover)
                else model.permissionDenied("러닝 코스를 기록하려면 정밀 위치 권한이 필요합니다. 설정에서 허용해 주세요.")
            }
            UiRequest.ResumeRunPermission -> {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) model.resumeWithPermission()
                else model.permissionDenied("다시 달리려면 정밀 위치 권한을 허용해 주세요.")
            }
            is UiRequest.MicrophonePermission -> {
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    if (pending.roomId != null) model.connectVoiceWithPermission(pending.roomId) else model.enableMicrophoneWithPermission()
                } else model.permissionDenied("마이크를 켜려면 녹음 권한이 필요합니다. 듣기만 하는 기능은 사용할 수 있습니다.")
            }
            is UiRequest.ExternalCapture -> {
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) launchCapture(pending.packageName)
                else model.permissionDenied("음악 앱의 소리를 공유하려면 오디오 캡처 권한이 필요합니다.")
            }
            else -> Unit
        }
    }
    private val musicFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            model.musicFileSelected(it)
        }
    }
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> model.captureResult(result.resultCode, result.data, capturePackage) }
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system already painted the launcher theme. Restore the ordinary
        // Activity theme for both fresh creation and saved-state recreation.
        setTheme(R.style.Theme_Runmoni)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) configurePlatformSplash()
        refreshLocationAccess()
        // The product currently has a light canvas in both system appearances.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        model.onEntryHostCreated()
        RunNotifications.createChannel(this)
        handledRunNotificationEvent = savedInstanceState?.getString("handledRunNotificationEvent")
        handleRunNotification(intent, force = false)
        setContent {
            val state by model.state.collectAsState()
            val updates = rememberAppUpdates()
            RunmoniRoot(state, model, updates.state, onCheckUpdates = { updates.check(force = true) })
        }
        lifecycleScope.launch {
            model.requests.collect { request ->
                when (request) {
                    is UiRequest.RunPermission -> {
                        requestRunningPermissions(request)
                    }
                    UiRequest.ResumeRunPermission -> {
                        requestRunningPermissions(request)
                    }
                    is UiRequest.MicrophonePermission -> {
                        // A listener can enter without microphone access.
                        if (request.roomId != null) model.connectVoiceWithPermission(request.roomId)
                        else if (hasPermission(Manifest.permission.RECORD_AUDIO)) model.enableMicrophoneWithPermission()
                        else requestPermissions(request, listOf(Manifest.permission.RECORD_AUDIO) + notificationPermission())
                    }
                    UiRequest.PickMusic -> musicFile.launch(arrayOf("audio/*"))
                    UiRequest.GoogleSignIn -> lifecycleScope.launch { model.googleSignIn(this@MainActivity) }
                    is UiRequest.EntryGoogleSignIn -> lifecycleScope.launch { model.googleSignInForEntry(this@MainActivity, request.token) }
                    UiRequest.LinkCommunityGoogle -> lifecycleScope.launch { model.linkCommunityGoogle(this@MainActivity) }
                    UiRequest.RestoreCommunityGoogle -> lifecycleScope.launch { model.linkCommunityGoogle(this@MainActivity, restore = true) }
                    UiRequest.RunNotificationSettings -> openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, RunNotifications.CHANNEL))
                    UiRequest.PinRunWidget -> {
                        if (!com.runmoni.app.widget.RunWidgetPublisher.requestPin(this@MainActivity))
                            model.permissionDenied("홈 화면을 길게 누른 뒤 ‘위젯’에서 런모니를 추가해 주세요.")
                    }
                    UiRequest.LocationSettings -> openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    UiRequest.AppPermissionSettings -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    is UiRequest.ExternalCapture -> {
                        if (hasPermission(Manifest.permission.RECORD_AUDIO)) launchCapture(request.packageName)
                        else requestPermissions(request, listOf(Manifest.permission.RECORD_AUDIO) + notificationPermission())
                    }
                    is UiRequest.ShareFile -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, request.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "러닝 코스 내보내기"))
                    is UiRequest.ShareText -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, request.text), "연결·휴대폰 점검 결과 공유"))
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun configurePlatformSplash() {
        splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled()) splash.remove()
            else {
                var removed = false
                fun removeSplash() { if (!removed) { removed = true; splash.remove() } }
                splash.animate().alpha(0f).setDuration(120L)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = removeSplash()
                        override fun onAnimationCancel(animation: Animator) = removeSplash()
                    }).start()
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRunNotification(intent, force = true)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("handledRunNotificationEvent", handledRunNotificationEvent)
        super.onSaveInstanceState(outState)
    }
    private fun handleRunNotification(source: Intent?, force: Boolean) {
        if (source?.action != RunNotifications.OPEN_RUN) return
        val event = source.getStringExtra(RunNotifications.EVENT)
        if (!force && event != null && event == handledRunNotificationEvent) return
        handledRunNotificationEvent = event
        model.openRunFromNotification(source.getStringExtra(RunNotifications.RUN_ID).orEmpty(),
            source.getBooleanExtra(RunNotifications.CONFIRM_FINISH, false))
        source.removeExtra(RunNotifications.RUN_ID)
        source.removeExtra(RunNotifications.CONFIRM_FINISH)
        source.action = Intent.ACTION_MAIN
        source.data = null
    }
    private fun requestRunningPermissions(request: UiRequest) {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) missing += listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val preferences = getSharedPreferences("runmoni", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS) &&
            !preferences.getBoolean("runNotificationPermissionAskedV1", false)) {
            preferences.edit().putBoolean("runNotificationPermissionAskedV1", true).apply()
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) requestPermissions(request, missing)
        else if (request is UiRequest.RunPermission) model.beginWithPermission(request.recover)
        else model.resumeWithPermission()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, locationChanges, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onResume() { super.onResume(); refreshLocationAccess() }
    override fun onStop() { unregisterReceiver(locationChanges); super.onStop() }
    private fun refreshLocationAccess() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        model.updateRunNotificationAccess(RunNotifications.enabled(this))
        model.updateLocationAccess(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { model.permissionDenied("설정 화면을 열 수 없어요. 기기 설정에서 런모니의 위치 권한과 위치 서비스를 확인해 주세요.") }
    }
    private fun launchCapture(packageName: String) { capturePackage = packageName; capture.launch(model.captureIntent()) }
    private fun requestPermissions(request: UiRequest, names: List<String>) { pendingPermission = request; permissions.launch(names.toTypedArray()) }
    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun notificationPermission() = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
}
