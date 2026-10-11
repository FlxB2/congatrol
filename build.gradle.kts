plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlint) apply false
}

// Resolved here, in the root script, because the type-safe `libs` accessor isn't visible against
// a subproject receiver inside the subprojects block below.
val composeRulesKtlint = libs.compose.rules.ktlint

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    // mrmans0n/compose-rules as a ktlint ruleset. ktlint runs over every source set, which is where
    // the composables actually live (commonMain).
    dependencies {
        add("ktlintRuleset", composeRulesKtlint)
    }
    // ktlint pulls generated source dirs (Compose resources) into its inputs; declare ordering so
    // Gradle doesn't flag an undeclared dependency on the generators. Rules are switched off for
    // build/ in the root .editorconfig.
    tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
        mustRunAfter(tasks.matching { it.name.startsWith("generate") })
    }
}
