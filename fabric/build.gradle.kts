@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val loader = prop("loom.platform")!!
val minecraft: String = stonecutter.current.version
val common: Project = requireNotNull(stonecutter.node.sibling("")?.project) {
    "No common project for $project"
}

// Data pack layout / JSON shape comes from the common node's versions/<mc>/gradle.properties
extra["kvr.recipe_folder"] = common.mod.prop("recipe_folder")
extra["kvr.loot_folder"] = common.mod.prop("loot_folder")
extra["kvr.ingredient_style"] = common.mod.prop("ingredient_style")
extra["kvr.result_key"] = common.mod.prop("result_key")
apply(from = rootProject.file("gradle/kvr-recipes.gradle.kts"))

version = "${mod.version}+$minecraft"
base {
    archivesName.set("${mod.id}-$loader")
}
// 1.21.11 renamed net.minecraft.resources.ResourceLocation to ...Identifier (same class, same API).
// The Stonecutter DSL cannot live in buildSrc (plugin types), so this block is repeated per module.
stonecutter {
    replacements.string(stonecutter.eval(stonecutter.current.version, ">=1.21.11")) {
        replace("ResourceLocation", "Identifier")
    }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val commonBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    get("developmentFabric").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    // Registry freeze relaxation for content mods lives in Fabric API, not in the loader
    modImplementation("net.fabricmc.fabric-api:fabric-api:${common.mod.dep("fabric_api")}")
    modCompileOnly("com.terraformersmc:modmenu:11.0.3")
    implementation("dev.architectury:architectury-injectables:${mod.dep("architectury_injectables")}")

    // Chicory runtime bundle
    implementation("com.dylibso.chicory:runtime:1.4.0")
    implementation("com.dylibso.chicory:wasi:1.4.0")
    implementation("com.dylibso.chicory:wasm:1.4.0")
    implementation("com.dylibso.chicory:log:1.4.0")
    include("com.dylibso.chicory:runtime:1.4.0")
    include("com.dylibso.chicory:wasi:1.4.0")
    include("com.dylibso.chicory:wasm:1.4.0")
    include("com.dylibso.chicory:log:1.4.0")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
}

loom {
    decompilers {
        get("vineflower").apply {
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        isIdeConfigGenerated = true
        runDir = "../../../run"
        vmArgs("-Dmixin.debug.export=true")
    }
}

applyKvrJavaVersion(stonecutter.eval(minecraft, ">=1.20.5"))

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.processResources {
    properties(listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to project.version,
        "minecraft" to common.mod.prop("mc_dep_fabric"),
        "loader_version" to mod.dep("fabric_loader"),
        "fabric_api_version" to common.mod.dep("fabric_api")
    )
}

tasks.build {
    group = "versioned"
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    from(tasks.remapJar.get().archiveFile, tasks.remapSourcesJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}/$loader"))
    dependsOn("build")
}
