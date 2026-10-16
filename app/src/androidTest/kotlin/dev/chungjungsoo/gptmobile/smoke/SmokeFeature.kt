package dev.chungjungsoo.gptmobile.smoke

/**
 * Tags a smoke test with the product feature it covers.
 *
 * Add a new test under `smoke/<feature>/` and pass the same name here.
 * [scripts/test-report.sh] groups results by this name (and by that package)
 * so later features can land without rewriting the report.
 *
 * Current names: chat, bots, projects, share, widget, voice, settings, vault, migration.
 * Tests for a feature that is not in the APK yet call [assumeFeature] and are skipped.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SmokeFeature(val name: String)
