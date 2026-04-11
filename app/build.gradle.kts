import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun escapeBuildConfigValue(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

fun resolveConfigValue(primaryKey: String, secondaryKey: String? = null): String {
    val environmentValue = listOfNotNull(primaryKey, secondaryKey)
        .firstNotNullOfOrNull { key -> System.getenv(key)?.takeIf { it.isNotBlank() } }

    if (environmentValue != null) {
        return environmentValue
    }

    return listOfNotNull(primaryKey, secondaryKey)
        .firstNotNullOfOrNull { key -> localProperties.getProperty(key)?.takeIf { it.isNotBlank() } }
        .orEmpty()
}

val supabaseUrl = resolveConfigValue(primaryKey = "SUPABASE_URL")
val supabasePublicKey = resolveConfigValue(
    primaryKey = "SUPABASE_PUBLISHABLE_KEY",
    secondaryKey = "SUPABASE_ANON_KEY"
)
val isSupabaseConfigured = supabaseUrl.isNotBlank() && supabasePublicKey.isNotBlank()

android {
    namespace = "com.internshipuncle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.internshipuncle"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${escapeBuildConfigValue(supabaseUrl)}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLIC_KEY",
            "\"${escapeBuildConfigValue(supabasePublicKey)}\""
        )
        buildConfigField("boolean", "SUPABASE_CONFIGURED", isSupabaseConfigured.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
