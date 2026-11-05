@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
}

val minecraft: String = stonecutter.current.version

version = "${mod.version}+$minecraft"
base {
    archivesName.set("${mod.id}-common")
}

// 1.21.11 renamed net.minecraft.resources.ResourceLocation to ...Identifier (same class, same API).
// The Stonecutter DSL cannot live in buildSrc (plugin types), so this block is repeated per module.
stonecutter {
    replacements.string(stonecutter.eval(stonecutter.current.version, ">=1.21.11")) {
        replace("ResourceLocation", "Identifier")
    }
}

architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else it.project.prop("loom.platform")
})

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    // Mojang mappings for every loader: NeoForge needs no yarn mapping patch,
    // which is what keeps the 1.21.11 column buildable.
    mappings(loom.officialMojangMappings())

    // @ExpectPlatform annotations for the platform facades. Deliberately NOT the full
    // Architectury API: the KVR core only needs the annotation contract, and a
    // version-independent artifact keeps the Stonecutter matrix free of per-version AR versions.
    compileOnly("dev.architectury:architectury-injectables:${mod.dep("architectury_injectables")}")

    // SpongePowered Mixin for common client mixins (GuiMixin, MouseHandlerMixin, etc.)
    compileOnly("org.spongepowered:mixin:0.8.7")
    annotationProcessor("org.spongepowered:mixin:0.8.7:processor")

    testImplementation("org.junit.jupiter:junit-jupiter:${mod.dep("junit")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    decompilers {
        get("vineflower").apply { // Adds names to lambdas - useful for mixins
            options.put("mark-corresponding-synthetics", "1")
        }
    }
}

applyKvrJavaVersion(stonecutter.eval(minecraft, ">=1.20.5"))

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

tasks.build {
    group = "versioned"
    description = "Builds the common module for the active Stonecutter version"
}
