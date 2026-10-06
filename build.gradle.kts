plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// ~/Documents is synced by iCloud Drive, which creates "Foo 2.dex" conflict copies inside
// build outputs and breaks dexing. iCloud ignores anything named *.nosync.
allprojects {
    layout.buildDirectory.set(layout.projectDirectory.dir("build.nosync"))
}
