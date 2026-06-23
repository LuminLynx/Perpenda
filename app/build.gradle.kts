import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.sentry.android.gradle") version "4.14.1"
}

// Single load of local.properties (gitignored). Individual properties are
// read defensively below — any one of them missing falls back to a sensible
// default (unsigned build / no mapping upload) so a fresh checkout or CI run
// still completes without local secrets.
val localProps: Properties = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    ?: Properties()

val releaseSigningProps: Properties? = localProps.takeIf {
    // Require both path + password before activating release signing.
    // Missing either → fall through to an unsigned release build instead of
    // failing at sign time.
    it.getProperty("RELEASE_KEYSTORE_PATH")?.isNotBlank() == true &&
        it.getProperty("RELEASE_KEYSTORE_PASSWORD")?.isNotBlank() == true
}

val sentryAuthToken: String? = localProps.getProperty("SENTRY_AUTH_TOKEN")
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("SENTRY_AUTH_TOKEN")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.perpenda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.perpenda"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2"
        buildConfigField("String", "API_BASE_URL", "\"https://api.perpenda.com/\"")
        // Sentry DSN is a public identifier (it ships in every APK by design;
        // Sentry rate-limits unknown clients). Safe to commit.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"https://b4b6960ba5c8f8a940cdb35e8f297ccd@o4511485646405632.ingest.de.sentry.io/4511485699620944\""
        )
        // Gate for the in-app sideload-update banner. Default OFF — Google Play's
        // Device and Network Abuse policy forbids Play-distributed apps from
        // prompting users to install APKs outside Play. The `release` buildType
        // below opts in because today's release artifact is the sideload APK
        // hosted on GitHub Releases. Any future Play build (separate flavor or
        // buildType) MUST leave this `false`.
        buildConfigField("boolean", "SIDELOAD_DISTRIBUTION", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseSigningProps != null) {
            create("release") {
                storeFile = file(releaseSigningProps.getProperty("RELEASE_KEYSTORE_PATH"))
                storePassword = releaseSigningProps.getProperty("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningProps.getProperty("RELEASE_KEY_ALIAS") ?: "perpenda-upload"
                keyPassword = releaseSigningProps.getProperty("RELEASE_KEY_PASSWORD")
                    ?: releaseSigningProps.getProperty("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            // Embed native debug symbols in the bundle so Google Play (Android
            // vitals) can symbolicate native crashes & ANRs from the bundled
            // native libs — clears Play's "App Bundle contains native code …
            // upload a symbol file" warning. Inherited by playRelease via
            // initWith(). FULL = function names + line numbers; switch to
            // "SYMBOL_TABLE" if the size bump matters.
            ndk { debugSymbolLevel = "FULL" }
            // Sideload distribution: this artifact is the signed APK on GitHub
            // Releases. The in-app update banner is active. Build via
            // `:app:assembleRelease`.
            buildConfigField("boolean", "SIDELOAD_DISTRIBUTION", "true")
        }
        create("playRelease") {
            // Inherits everything from `release` (signing, minify, ProGuard,
            // Sentry mapping upload via the plugin), then turns the update
            // banner OFF — Google Play's Device and Network Abuse policy
            // forbids Play-distributed apps from steering users to install
            // APKs outside Play. Build the Play artifact with
            // `:app:bundlePlayRelease` (produces an .aab for upload).
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "SIDELOAD_DISTRIBUTION", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Sentry Gradle plugin — uploads the R8 mapping file to Sentry on release
// builds so obfuscated stack traces are deobfuscated in the dashboard. The
// auth token is read from local.properties (gitignored) first, then the
// SENTRY_AUTH_TOKEN environment variable as a fallback (the standard CI /
// release-shell pattern). Without either, upload is skipped and the build
// still succeeds — the only cost is that stack traces remain obfuscated
// for that release.
sentry {
    org.set("perpenda")
    projectName.set("android")
    autoUploadProguardMapping.set(sentryAuthToken != null)
    includeProguardMapping.set(true)
    // Upload native debug symbols to Sentry too (only when authed, same as the
    // mapping upload) so native crashes deobfuscate in the Sentry dashboard.
    uploadNativeSymbols.set(sentryAuthToken != null)
    if (sentryAuthToken != null) {
        authToken.set(sentryAuthToken)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("io.sentry:sentry-android:7.21.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
