package com.a4sizepaper.typing.texttopdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.a4sizepaper.typing.texttopdf.ads.AdsController
import com.a4sizepaper.typing.texttopdf.ui.LekhakApp
import com.a4sizepaper.typing.texttopdf.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private lateinit var ads: AdsController
    private var pushRegistrationStarted = false
    private val notificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPushRegistration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(160L)
                .withEndAction { provider.remove() }
                .start()
        }
        ads = AdsController(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) receiveDocument(intent)
        setContent { LekhakApp(model, ads) }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            startPushRegistration()
        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val preferences = getSharedPreferences("push_preferences", MODE_PRIVATE)
            if (!preferences.getBoolean("permission_requested", false)) {
                preferences.edit().putBoolean("permission_requested", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startPushRegistration() {
        if (pushRegistrationStarted) return
        pushRegistrationStarted = true
        registerForPushUpdates(this)
    }

    override fun onDestroy() {
        if (::ads.isInitialized) ads.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveDocument(intent)
    }

    private fun receiveDocument(intent: android.content.Intent?) {
        if (intent != null && intent.type?.startsWith("image/", ignoreCase = true) == true &&
            (intent.action == android.content.Intent.ACTION_SEND ||
                intent.action == android.content.Intent.ACTION_SEND_MULTIPLE)
        ) {
            val photos = if (intent.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
                androidx.core.content.IntentCompat.getParcelableArrayListExtra(
                    intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java
                ).orEmpty()
            } else {
                listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(
                    intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java
                ))
            }
            val clip = intent.clipData
            val received = photos.ifEmpty {
                (0 until (clip?.itemCount ?: 0)).mapNotNull { clip?.getItemAt(it)?.uri }
            }
            model.offerSharedPhotos(received)
            return
        }
        val uri =
            when (intent?.action) {
                android.content.Intent.ACTION_VIEW -> intent.data
                android.content.Intent.ACTION_SEND ->
                    androidx.core.content.IntentCompat.getParcelableExtra(
                        intent,
                        android.content.Intent.EXTRA_STREAM,
                        android.net.Uri::class.java,
                    )
                else -> null
            }
        uri?.let {
            val markup = intent?.action == android.content.Intent.ACTION_VIEW &&
                intent.component?.className == "$packageName.OpenPdfMarkup"
            model.offerImport(
                it,
                format = if (markup) com.a4sizepaper.typing.texttopdf.data.ImportFormat.PDF else null,
                external = true,
                openPdfMarkup = markup,
            )
        }
    }
}
