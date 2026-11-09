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
        uri?.let { model.offerImport(it, external = true) }
    }
}
