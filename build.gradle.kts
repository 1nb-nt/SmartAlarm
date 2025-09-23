// Top-level (project) build file — repositories are defined in settings.gradle.kts

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
