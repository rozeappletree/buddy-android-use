plugins {
    id("com.android.application")
}

// Version lives in BuildInfo.java so the Gradle and on-device (build.sh) builds always agree.
val buildInfo = file("src/main/java/app/buddy/assistant/BuildInfo.java").readText()
val appVersionName = Regex("""VERSION = "([^"]+)"""").find(buildInfo)!!.groupValues[1]
val appVersionCode = Regex("""VERSION_CODE = (\d+)""").find(buildInfo)!!.groupValues[1].toInt()

android {
    // proot for the built-in Linux ships as native libraries and must be extracted to disk to run
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    namespace = "app.buddy.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.buddy.assistant"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Release signing comes from environment variables (set as GitHub Actions secrets).
    // Without them, assembleRelease produces an unsigned APK.
    val keystorePath = System.getenv("BUDDY_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BUDDY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BUDDY_KEY_ALIAS") ?: "buddy"
                keyPassword = System.getenv("BUDDY_KEY_PASSWORD") ?: System.getenv("BUDDY_KEYSTORE_PASSWORD")
            }
        }
    }

    // github: the release on GitHub, which updates itself from GitHub releases.
    // fdroid: built and signed by F-Droid from this source; the same app without the self-updater (F-Droid updates it).
    flavorDimensions += "store"
    productFlavors {
        create("github") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    // No Google-only dependency blob and no git details in the APK: F-Droid requires the first, and both keep the
    // fdroid APK the same whoever builds it (F-Droid's reproducible build compares its APK with the one on GitHub).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            vcsInfo.include = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        // Buddy as a home screen (Android Settings > Default apps > Home app). Everything for it lives in
        // src/launcher, so this block is the only upstream file touched. Build: ./gradlew assembleFdroidLauncher
        create("launcher") {
            initWith(getByName("release"))
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }
}

// Local addition (not upstream). Windows without Developer Mode checks git symlinks out as small text files holding
// the target path: app/src/main/assets/linux is then a 17-byte file instead of the scripts, and Buddy's Linux set-up
// fails ("install failed: linux/buddy-run"). Build from a copy of the assets with the links followed. Where links
// work (Linux, macOS, CI, Windows with Developer Mode) nothing is broken and nothing changes.
fun brokenLinkTarget(f: File): File? {
    if (!f.isFile || f.length() > 512) return null
    val t = f.readText()
    if (t.contains('\n') || !t.startsWith("../")) return null
    return f.parentFile.resolve(t).normalize().takeIf { it.exists() }
}
fun copyFollowingLinks(src: File, dst: File) {
    val real = brokenLinkTarget(src) ?: src
    if (real.isDirectory) {
        dst.mkdirs()
        real.listFiles()!!.forEach { copyFollowingLinks(it, File(dst, it.name)) }
    } else {
        real.copyTo(dst, overwrite = true)
    }
}
val mainAssets = file("src/main/assets")
if (mainAssets.listFiles().orEmpty().any { brokenLinkTarget(it) != null }) {
    // copied while Gradle configures the build (well under 1 MB), so every task that reads assets sees it
    val resolvedAssets = layout.buildDirectory.dir("resolved-assets").get().asFile
    resolvedAssets.deleteRecursively()
    copyFollowingLinks(mainAssets, resolvedAssets)
    android.sourceSets.getByName("main").assets.setSrcDirs(listOf(resolvedAssets))
}
