// The app shell: Application, MainActivity and the Hilt graph root. Feature and core code lives in its own modules.
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.StringReader
import java.util.Properties

plugins {
    id("wallper.android.application")
    id("wallper.android.compose")
    id("wallper.hilt")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

/** The upload key's values beside the key in singning/, which git ignores. */
val localSigning = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("singning/signing.properties"))
        .asText.orNull?.let { load(StringReader(it)) }
}

/** A signing value from ~/.gradle/gradle.properties, the environment or singning/; never from the repository. */
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
        ?: localSigning.getProperty(name)

android {
    namespace = "com.onthecrow.wallper"

    // The Play upload key. Without these properties the release build stays unsigned, never debug-signed.
    signingConfigs {
        val storeFile = signingValue("WALLPER_UPLOAD_STORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = signingValue("WALLPER_UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("WALLPER_UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("WALLPER_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.onthecrow.wallper"
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        }
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// google-services.json isn't in git: CI builds without it, so with no Firebase app.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
    implementation(projects.core.coroutines)
    implementation(projects.core.diagnostics)
    implementation(projects.core.designsystem)
    implementation(projects.core.engine.impl)
    implementation(projects.core.navigation.impl)
    implementation(projects.core.resources)
    implementation(projects.core.storage)
    implementation(projects.feature.wallpapers.logicImpl)
    implementation(projects.feature.wallpapers.uiApi)
    implementation(projects.feature.wallpapers.uiImpl)
    implementation(projects.feature.editor.logicImpl)
    implementation(projects.feature.editor.uiImpl)
    implementation(projects.feature.blackhole.logicImpl)
    implementation(projects.feature.blackhole.uiImpl)
    implementation(projects.feature.settings.logicImpl)
    implementation(projects.feature.settings.uiImpl)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.timber)
    // Firebase is in release builds only: a debug build neither starts it nor carries it.
    releaseImplementation(platform(libs.firebase.bom))
    releaseImplementation(libs.firebase.analytics)
    releaseImplementation(libs.firebase.crashlytics)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
}
