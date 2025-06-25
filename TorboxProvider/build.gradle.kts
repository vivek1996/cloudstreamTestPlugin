dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // For making HTTP requests and parsing JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0") // Or other version
    // implementation("io.ktor:ktor-client-core:2.3.5") // Example, if not using app.get
    // implementation("io.ktor:ktor-client-cio:2.3.5") // Example
    implementation("androidx.preference:preference-ktx:1.2.1") // Added for PreferenceFragmentCompat
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.
    description = "Integrates with Torbox.app debrid service" // Updated description
    authors = listOf("YourName") // Update author

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie", "TvSeries", "Torrent") // Updated TvTypes

    requiresResources = true // Keep true for settings UI
    language = "en"

    iconUrl = "https://torbox.app/favicon.ico" // Using Torbox favicon, or a more specific logo if available
}

android {
    namespace = "com.torbox" // Set the namespace
    buildFeatures {
        buildConfig = true
        viewBinding = true // Keep for settings UI if it uses view binding
    }
}
