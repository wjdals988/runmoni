package com.runmoni.app

import android.Manifest
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
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.runmoni.app.ui.RunmoniApp
import com.runmoni.app.ui.rememberAppUpdates
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var pendingPermission: UiRequest? = null
    private var capturePackage = ""
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
        super.onCreate(savedInstanceState)
        refreshLocationAccess()
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsState()
            val updates = rememberAppUpdates()
            RunmoniApp(state, model, updates.state, onCheckUpdates = { updates.check(force = true) })
        }
        lifecycleScope.launch {
            model.requests.collect { request ->
                when (request) {
                    is UiRequest.RunPermission -> {
                        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) model.beginWithPermission(request.recover)
                        else requestPermissions(request, listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) + notificationPermission())
                    }
                    is UiRequest.MicrophonePermission -> {
                        // A listener can enter without microphone access.
                        if (request.roomId != null) model.connectVoiceWithPermission(request.roomId)
                        else if (hasPermission(Manifest.permission.RECORD_AUDIO)) model.enableMicrophoneWithPermission()
                        else requestPermissions(request, listOf(Manifest.permission.RECORD_AUDIO) + notificationPermission())
                    }
                    UiRequest.PickMusic -> musicFile.launch(arrayOf("audio/*"))
                    UiRequest.GoogleSignIn -> lifecycleScope.launch { model.googleSignIn(this@MainActivity) }
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
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, locationChanges, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onResume() { super.onResume(); refreshLocationAccess() }
    override fun onStop() { unregisterReceiver(locationChanges); super.onStop() }
    private fun refreshLocationAccess() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
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
