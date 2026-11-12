plugins { id("com.android.application") }
android {
    namespace="io.github.oshin.windowcapture.probe"
    compileSdk=37
    defaultConfig { applicationId="io.github.oshin.windowcapture.probe"; minSdk=33; targetSdk=37; versionCode=1;versionName="1.0" }
    compileOptions {sourceCompatibility=JavaVersion.VERSION_21;targetCompatibility=JavaVersion.VERSION_21}
}
