import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
val props = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
    namespace = "com.farmcare.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.farmcare.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1; versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Public client values ONLY (URL + anon key). Never the service_role key.
        buildConfigField("String", "SUPABASE_URL", "\"${props.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${props.getProperty("supabase.anonKey", "")}\"")
    }
    signingConfigs {
        create("release") {
            props.getProperty("release.storeFile")?.let {
                storeFile = file(it); storePassword = props.getProperty("release.storePassword")
                keyAlias = props.getProperty("release.keyAlias"); keyPassword = props.getProperty("release.keyPassword")
            }
        }
    }
    buildTypes {
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material3)
    implementation(libs.compose.material3.wsc); implementation(libs.compose.icons); implementation(libs.activity.compose)
    implementation(libs.nav.compose); debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android); ksp(libs.hilt.compiler); implementation(libs.hilt.nav)
    implementation(libs.hilt.work); ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.work.runtime); implementation(libs.datastore); implementation(libs.security.crypto)
    implementation(libs.camera.core); implementation(libs.camera.camera2); implementation(libs.camera.lifecycle); implementation(libs.camera.view)
    implementation(libs.mlkit.barcode); implementation(libs.zxing)
    implementation(platform(libs.supabase.bom)); implementation(libs.supabase.postgrest); implementation(libs.supabase.auth); implementation(libs.supabase.storage)
    implementation(libs.ktor.okhttp); implementation(libs.serialization.json)
    testImplementation(libs.junit); testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.test)
}
