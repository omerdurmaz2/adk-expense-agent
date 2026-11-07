plugins {
    id("com.android.application") version "9.3.1" apply false
    // AGP 9 compiles Kotlin itself; this line only pins the Kotlin version it uses.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
