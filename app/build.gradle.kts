import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
val signingProperties = Properties().apply {
    val f=rootProject.file("keystore.properties")
    if(f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "io.github.oshin.windowcapture"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.oshin.windowcapture"
        minSdk = 33; targetSdk = 37
        versionCode = 15; versionName = "2.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    if(signingProperties.isNotEmpty()) signingConfigs.create("personal") {
        storeFile=rootProject.file(signingProperties.getProperty("storeFile"))
        storePassword=signingProperties.getProperty("storePassword")
        keyAlias=signingProperties.getProperty("keyAlias")
        keyPassword=signingProperties.getProperty("keyPassword")
    }
    buildTypes { release {
        isMinifyEnabled=true; isShrinkResources=true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if(signingProperties.isNotEmpty()) signingConfig=signingConfigs.getByName("personal")
    } }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
    buildFeatures { buildConfig=true; compose=true }
}
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.2")
    testImplementation("junit:junit:4.13.2")
}
