import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("release-keystore/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// ── Versione: versionCode a ogni build, versionName (maggiore.minore) a ogni RILASCIO ──────────────
// version.properties tiene versionCode + versionName e viene committato. Ogni build (debug o AAB) fa +1 al
// versionCode. Il versionName cambia solo quando si produce un AAB (bundle*): dalla serie 1.x si passa a "2.0",
// poi ogni AAB incrementa la minor release (2.1, 2.2, …). Le build di prova (assemble*) mostrano già la PROSSIMA
// versione (es. 2.53-debug con la 2.52 su Play) e si distinguono anche dal codice mostrato nel menu "GiroCalendar vX.Y (codice)".
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(FileInputStream(versionPropsFile))
}
val previousVersionCode = (versionProps.getProperty("versionCode") ?: "0").toInt()
val previousVersionName = versionProps.getProperty("versionName") ?: "0.0"

// Bump solo se la riga di comando chiede davvero un assemble/bundle (non per un ./gradlew tasks,
// sync IDE, o qualunque altra invocazione che valuta questo script senza produrre un APK/AAB) —
// e scritto SUBITO, non in un doFirst: defaultConfig legge appVersionCode/Name più sotto nella
// stessa fase di configurazione, quindi il file deve già riflettere la versione che finirà
// nell'APK.
val isBuildingApk = gradle.startParameter.taskNames.any {
    it.contains("assemble", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}
val isRelease = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
val appVersionCode: Int = if (isBuildingApk) previousVersionCode + 1 else previousVersionCode
// Versione dell'ULTIMO rilascio (quella su Play) e prossima minor: le build di prova (assemble*) mostrano già la
// prossima (es. 2.53-debug quando su Play c'è la 2.52), così non si confondono con la versione pubblicata; l'AAB
// successivo (bundle*) produce proprio quella versione, senza saltare numeri.
val nextVersionName: String = run {
    val parts = previousVersionName.split(".")
    if (parts.size == 2 && parts.all { it.toIntOrNull() != null }) {
        val major = parts[0].toInt(); val minor = parts[1].toInt()
        if (major < 2) "2.0" else "$major.${minor + 1}"
    } else previousVersionName
}
val appVersionName: String = if (isBuildingApk) nextVersionName else previousVersionName

if (isBuildingApk) {
    // il versionName nel file cambia solo con un AAB (rilascio); le build di prova lo mostrano ma non lo avanzano
    versionPropsFile.writeText("versionCode=$appVersionCode\nversionName=${if (isRelease) nextVersionName else previousVersionName}\n")
    println("GiroCalendar version -> $appVersionName ($appVersionCode)")
}

// Applicato solo se google-services.json (dalla console Firebase) è presente in app/ —
// altrimenti il plugin fallisce la build con "File google-services.json is missing".
// Fino ad allora l'app compila e gira, solo senza Firebase (ricerca/creazione disabilitate).
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "simplylucaapps.girocalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "simplylucaapps.girocalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // Solo le lingue dell'app (it/en): toglie dal resources.arsc le traduzioni delle librerie in altre lingue.
        androidResources.localeFilters += listOf("it", "en")
    }

    signingConfigs {
        // Pinnato esplicitamente (non lasciato alla risoluzione di default di AGP) perché
        // questa macchina ha due debug.keystore diversi (~/.android e ~/.config/.android).
        // Solo lo SHA-1 di ~/.android è registrabile in Firebase per Google Sign-In.
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropertiesFile.exists())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug") // fallback locale, NON per il Play Store
        }
        // ApplicationId diverso (+ nome app in src/debug/res) così un debug build si installa
        // fianco a fianco con una release Play Store invece di dare INSTALL_FAILED_UPDATE_INCOMPATIBLE.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Per chi prova l'app (APK pubblico): ottimizzato come la release (R8, ~14 MB invece di 34), ma con lo stesso
        // applicationId/firma/nome della debug, quindi si installa accanto alla versione Play e il login Google funziona.
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform(libs.compose.bom))
    implementation(libs.play.app.update)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Installa il baseline profile (src/main/baseline-prof.txt) anche fuori dal Play Store: avvio a freddo più rapido.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.exifinterface)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Trim + fade-in/out dell'audio evento (Editor "Audio"): Media3 Transformer decodifica/
    // ricodifica il clip invece di un semplice troncamento byte, che non funziona su formati
    // compressi come MP3/AAC.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)
}
