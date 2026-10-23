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
    neoForge()
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
    get("developmentNeoForge").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    "neoForge"("net.neoforged:neoforge:${common.mod.dep("neoforge_loader")}")
    implementation("dev.architectury:architectury-injectables:${mod.dep("architectury_injectables")}")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionNeoForge")) { isTransitive = false }
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

tasks.jar {
    archiveClassifier = "dev"
}

tasks.remapJar {
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

applyKvrJavaVersion(stonecutter.eval(minecraft, ">=1.20.5"))

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
    exclude("fabric.mod.json", "architectury.common.json")
}

// NeoForge reads META-INF/mods.toml before 1.20.5 and META-INF/neoforge.mods.toml from 1.20.5 on,
// so one template is emitted under the name the target version expects.
val modsTomlName = if (stonecutter.eval(minecraft, ">=1.20.5")) "neoforge.mods.toml" else "mods.toml"
val modsToml = mapOf(
    "id" to mod.id.toString(),
    "name" to mod.name,
    "version" to project.version.toString(),
    "minecraft" to common.mod.prop("mc_dep_forgelike").toString(),
    "pack_format" to common.mod.prop("pack_format").toString(),
    "loader_range" to common.mod.prop("fml_loader_range").toString(),
    "neoforge_range" to common.mod.prop("neoforge_range").toString()
)

tasks.processResources {
    inputs.property("modsTomlName", modsTomlName)
    inputs.properties(modsToml)

    exclude("META-INF/neoforge.mods.toml")
    from(rootProject.file("neoforge/src/main/resources/META-INF/neoforge.mods.toml")) {
        into("META-INF")
        rename { modsTomlName }
        expand(modsToml)
    }

    filesMatching("pack.mcmeta") {
        expand(modsToml)
    }
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
