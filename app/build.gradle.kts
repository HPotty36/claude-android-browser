import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing reads keystore.properties (not in git). Without it, release builds use the debug key.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.agentbrowser"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.agentbrowser"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "0.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // A separate app id, so installing or testing a debug build never touches the release install's data.
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreProps.containsKey("storeFile")) "release" else "debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The default pattern leaves out folders that start with "_", and an extension keeps its strings in _locales.
    androidResources { ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~" }
    // Compress the engine's native libraries: the APK is about half the size to download.
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Downloads a pinned uBlock Origin release and unpacks it as the built-in extension assets/ublock.
 * uBlock Origin is GPLv3 and ships unmodified; see THIRD_PARTY_NOTICES.md.
 */
abstract class FetchUblock : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun run() {
        val xpi = File(temporaryDir, "ublock.xpi")
        URI(url.get()).toURL().openStream().use { input -> xpi.outputStream().use { input.copyTo(it) } }
        val actual = MessageDigest.getInstance("SHA-256").digest(xpi.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "uBlock Origin download has SHA-256 $actual, expected ${sha256.get()}" }

        val root = outputDir.get().asFile
        root.deleteRecursively()
        val target = File(root, "ublock").canonicalFile
        ZipInputStream(xpi.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // The signature only matters to the add-on store, not to a built-in extension.
                if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue
                val file = File(target, entry.name).canonicalFile
                check(file.startsWith(target)) { "unexpected path in the archive: ${entry.name}" }
                file.parentFile.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
            }
        }
    }
}

val ublockVersion = "1.76.4"
val fetchUblock = tasks.register<FetchUblock>("fetchUblock") {
    url = "https://github.com/gorhill/uBlock/releases/download/$ublockVersion/uBlock0_$ublockVersion.firefox.signed.xpi"
    sha256 = "66d0e3f21ce3b32b1bc0a9399b93cb2c9aa598ef18805d92118c76f66e266c1e"
    outputDir = layout.buildDirectory.dir("generated/ublock")
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(fetchUblock, FetchUblock::outputDir) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.mozilla.geckoview:geckoview:160.0.20261104181552")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
