plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.guarddroid.samples"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Each flavor is one installable test APK with a distinct malware-archetype
    // permission profile. The apps are BENIGN — they only declare permissions
    // so GuardDroid's permission-based classifier has something to score.
    flavorDimensions += "archetype"
    productFlavors {
        fun archetype(name: String, appId: String, label: String) {
            create(name) {
                dimension = "archetype"
                applicationId = appId
                resValue("string", "app_name_flavor", label)
            }
        }
        archetype("smsTrojan", "com.guarddroid.samples.smstrojan", "TEST SMS Trojan")
        archetype("spyware", "com.guarddroid.samples.spyware", "TEST Spyware")
        archetype("bankingOverlay", "com.guarddroid.samples.bankingoverlay", "TEST Banking Overlay")
        archetype("ransomware", "com.guarddroid.samples.ransomware", "TEST Ransomware")
        archetype("premiumDialer", "com.guarddroid.samples.premiumdialer", "TEST Premium Dialer")
        archetype("adware", "com.guarddroid.samples.adware", "TEST Adware")
        archetype("deviceAdminBot", "com.guarddroid.samples.deviceadminbot", "TEST Device-Admin Bot")
        archetype("stalkerware", "com.guarddroid.samples.stalkerware", "TEST Stalkerware")
        archetype("dropper", "com.guarddroid.samples.dropper", "TEST Dropper")
        archetype("benignFlashlight", "com.guarddroid.samples.benignflashlight", "TEST Benign Flashlight")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Intentionally dependency-free — these fixtures use only the platform SDK.
}
