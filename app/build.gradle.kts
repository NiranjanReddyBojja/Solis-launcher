plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.solis.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.solis.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // play: Google Play Billing, for the Play Store listing (Play's payments policy requires it).
    // direct: Stripe Checkout, for the APK distributed outside Play. See server/stripe-checkout.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("direct") {
            dimension = "store"
            versionNameSuffix = "-direct"
            // Set both in ~/.gradle/gradle.properties or with -P. Neither is a secret.
            val checkoutUrl = providers.gradleProperty("solis.checkoutUrl").getOrElse("")
            val publicKey = providers.gradleProperty("solis.licensePublicKey").getOrElse("")
            buildConfigField("String", "CHECKOUT_URL", "\"$checkoutUrl\"")
            buildConfigField("String", "LICENSE_PUBLIC_KEY", "\"$publicKey\"")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // billing pulls in fragment 1.1.0 via play-services; ActivityResult APIs need >= 1.3.0.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    "playImplementation"(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
