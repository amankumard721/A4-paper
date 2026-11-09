plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Debug always uses Google's demo inventory, even when production IDs are supplied.
val liveAdsEnabled = providers.gradleProperty("adsEnabled").orElse("false").get().toBooleanStrict()
val liveAdmobAppId = providers.gradleProperty("admobAppId").orElse("").get()
val liveBannerId = providers.gradleProperty("admobBannerId").orElse("").get()
val liveInterstitialId = providers.gradleProperty("admobInterstitialId").orElse("").get()
val demoInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val demoAppId = "ca-app-pub-3940256099942544~3347511713"
val demoBannerId = "ca-app-pub-3940256099942544/9214589741"

if (liveAdsEnabled) {
    require(
        Regex("ca-app-pub-[0-9]{16}~[0-9]{10}").matches(liveAdmobAppId) &&
            !liveAdmobAppId.startsWith("ca-app-pub-3940256099942544")
    ) {
        "Live ads require your AdMob App ID via -PadmobAppId. Demo IDs cannot enable release ads."
    }
    require(
        Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(liveBannerId) &&
            !liveBannerId.startsWith("ca-app-pub-3940256099942544")
    ) {
        "Live ads require your banner unit ID via -PadmobBannerId."
    }
    require(liveInterstitialId.isEmpty() || (
        Regex("ca-app-pub-[0-9]{16}/[0-9]{10}").matches(liveInterstitialId) &&
            !liveInterstitialId.startsWith("ca-app-pub-3940256099942544")
        )) { "Supply a production interstitial ID via -PadmobInterstitialId." }

}

android {
    namespace = "com.a4sizepaper.typing.texttopdf"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.a4sizepaper.typing.texttopdf"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.7.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Optional read-only reference to an existing local debug signing store.
    providers.environmentVariable("A4_DEBUG_STORE").orNull?.let { debugStore ->
        signingConfigs.getByName("debug").storeFile = file(debugStore)
    }
    // Secrets are supplied by the local environment, never checked into source or source archives.
    val uploadStore = providers.environmentVariable("LEKHAK_UPLOAD_STORE").orNull
    if (!uploadStore.isNullOrBlank()) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = providers.environmentVariable("LEKHAK_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("LEKHAK_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("LEKHAK_UPLOAD_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = demoAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "ADS_DEMO_MODE", "true")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$demoInterstitialId\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$demoBannerId\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = if (liveAdsEnabled) liveAdmobAppId else demoAppId
            buildConfigField("boolean", "ADS_ENABLED", liveAdsEnabled.toString())
            buildConfigField("boolean", "ADS_DEMO_MODE", "false")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${if (liveAdsEnabled) liveInterstitialId else ""}\"")
            buildConfigField(
                "String",
                "ADMOB_BANNER_ID",
                "\"${if (liveAdsEnabled) liveBannerId else ""}\"",
            )
            if (!uploadStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("upload")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            val hostTemp =
                layout.buildDirectory.dir("tmp/host-tests").get().asFile.apply { mkdirs() }
            test.systemProperty("java.io.tmpdir", hostTemp.absolutePath)
            providers.environmentVariable("A4_ROBOLECTRIC_DEPS").orNull?.let { dependencies ->
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", dependencies)
            }
        }
    }
    // Both languages remain available after a Play install, including offline language switching.
    bundle { language { enableSplit = false } }
    lint { abortOnError = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12") {
        exclude(group = "log4j", module = "log4j")
    }
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.ads.mediation:facebook:6.21.0.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    // GMA brings Guava at runtime and resolves listenablefuture to an empty artifact.
    // WorkManager's ListenableFuture API must also remain on the compile classpath.
    implementation("com.google.guava:guava:31.1-android")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
