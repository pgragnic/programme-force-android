import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val cfg = Properties().apply {
    val f = rootProject.file("supabase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "fr.pgragnic.force"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.pgragnic.force"
        minSdk = 28
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: ("0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0"))
        buildConfigField("String", "SUPABASE_URL", "\"" + (cfg.getProperty("url") ?: "") + "\"")
        buildConfigField("String", "SUPABASE_KEY", "\"" + (cfg.getProperty("key") ?: "") + "\"")
        // identifiant « client Web » Google (Google Cloud → Identifiants) pour « Se connecter avec Google » ; vide = bouton masqué
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"" + (cfg.getProperty("google") ?: "") + "\"")
    }
    signingConfigs {
        getByName("debug") {
            // clé de signature stable fournie par le dépôt pour que les mises à jour s'installent par-dessus
            val ks = rootProject.file("keystore/debug.keystore")
            if (ks.exists()) { storeFile = ks; storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" }
        }
    }
    signingConfigs {
        create("upload") {
            // clé de publication Google Play : déchiffrée par la CI à partir de keystore/upload.jks.gpg (mot de passe dans le secret UPLOAD_PASS)
            val ks = file(System.getenv("UPLOAD_KS") ?: "/nonexistent")
            if (ks.exists()) { storeFile = ks; storePassword = System.getenv("UPLOAD_PASS"); keyAlias = "upload"; keyPassword = System.getenv("UPLOAD_PASS") }
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }
        getByName("release") {
            isMinifyEnabled = false
            if (System.getenv("UPLOAD_KS") != null) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
