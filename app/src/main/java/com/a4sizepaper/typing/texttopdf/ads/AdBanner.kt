package com.a4sizepaper.typing.texttopdf.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.a4sizepaper.typing.texttopdf.ui.AppTheme
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a4sizepaper.typing.texttopdf.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** Banner slot. Inline mode is bounded to a compact creative within scrolling content. */
@Composable
fun AdBanner(controller: AdsController, modifier: Modifier = Modifier, language: String = "en", inline: Boolean = false) {
    val palette = AppTheme.colors
    val state by controller.state.collectAsStateWithLifecycle()
    if (!state.requestReady) return
    val context = LocalContext.current
    val activity = remember(context) { context.adsActivity() } ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orientation = LocalConfiguration.current.orientation
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Keep the SDK creative inside rounded card corners without clipping it.
        val width = maxWidth.value.toInt() - if (inline) 24 else 0
        if (width < 120) return@BoxWithConstraints
        key(width, orientation, state.generation, activity, inline) {
            var failed by remember { mutableStateOf(false) }
            val session = remember {
                val view = AdView(activity)
                view.adUnitId = BuildConfig.ADMOB_BANNER_ID
                view.setAdSize(
                    if (inline) AdSize.getInlineAdaptiveBannerAdSize(width, 50)
                    else AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width)
                )
                view.adListener =
                    object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            failed = true
                        }

                        override fun onAdLoaded() {
                            failed = false
                        }
                    }
                BannerSession(view, controller, state.generation)
            }
            DisposableEffect(session, lifecycle) {
                val unregister = controller.onInvalidated { session.destroy() }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> session.resume()
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP -> session.pause()
                        Lifecycle.Event.ON_DESTROY -> session.destroy()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.resume()
                else session.pause()
                onDispose {
                    unregister()
                    lifecycle.removeObserver(observer)
                    session.destroy()
                }
            }
            LaunchedEffect(failed) { if (failed) session.destroy() }
            if (!failed) {
                Surface(
                    modifier = Modifier.padding(bottom = if (inline) 0.dp else 12.dp).drawWithContent {
                        drawContent()
                        if (inline) drawRoundRect(palette.adBorder, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    },
                    shape = RoundedCornerShape(if (inline) 16.dp else 0.dp),
                    color = palette.surface,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = if (inline) 12.dp else 0.dp, vertical = if (inline) 4.dp else 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text =
                                if (BuildConfig.ADS_DEMO_MODE) {
                                    if (language == "hi") "टेस्ट विज्ञापन" else "Test advertisement"
                                } else if (language == "hi") "विज्ञापन" else "Advertisement",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        AndroidView(
                            factory = { session.view },
                            modifier =
                                Modifier.width(width.dp).then(
                                    if (inline) Modifier.heightIn(min = 50.dp).wrapContentHeight()
                                    else Modifier.height((session.view.adSize?.height ?: 50).dp)
                                ),
                        )
                    }
                }
            }
            // No retry loop or placeholder button: failed/no-fill inventory simply collapses.
        }
    }
}

/** One request per view lifetime; never resume or request after consent invalidation/disposal. */
private class BannerSession(
    val view: AdView,
    private val controller: AdsController,
    private val generation: Long,
) {
    private var requested = false
    private var destroyed = false

    fun resume() {
        if (destroyed || !controller.canRequest(generation)) return
        view.resume()
        if (!requested) {
            requested = true
            view.loadAd(AdRequest.Builder().build())
        }
    }

    fun pause() {
        if (!destroyed) view.pause()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        view.visibility = View.GONE
        view.adListener = object : AdListener() {}
        view.destroy()
    }
}

private fun Context.adsActivity(): Activity? {
    var current: Context = this
    val visited = mutableSetOf<Context>()
    while (visited.add(current)) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
