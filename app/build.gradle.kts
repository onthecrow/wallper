// The app shell: Application, MainActivity and the Hilt graph root. Feature and core code lives in its own modules.
plugins {
    id("wallper.android.application")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper"

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
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    implementation(projects.core.coroutines)
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
    implementation(projects.feature.blackhole.uiImpl)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.timber)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
}
