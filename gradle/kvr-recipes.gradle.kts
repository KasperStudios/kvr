// Generates the Block 2 data files (recipes, loot table) in the JSON shape the target Minecraft
// version expects. Applied by the fabric and neoforge build scripts because the format moved:
//
//   <=1.20.4          data/kvr/recipes/...        ingredient {"item":"x"}   result {"item":"x"}
//   1.21.1            data/kvr/recipe/...         ingredient {"item":"x"}   result {"id":"x"}
//   1.21.11           data/kvr/recipe/...         ingredient "x"            result {"id":"x"}
//
// Everything is generated from the tier ladder, so adding a component type or tier does not mean
// hand-writing dozens of JSON files.

fun kvrExtra(key: String): String = (project.extra.properties[key]
    ?: error("KVR data format '$key' is not set by the branch build script")).toString()

val kvrRecipeFolder = kvrExtra("kvr.recipe_folder")
val kvrLootFolder = kvrExtra("kvr.loot_folder")
val kvrIngredientStyle = kvrExtra("kvr.ingredient_style")
val kvrResultKey = kvrExtra("kvr.result_key")

fun kvrIngredient(id: String): String =
    if (kvrIngredientStyle == "string") "\"$id\"" else "{\"item\": \"$id\"}"

fun kvrResult(id: String, count: Int): String =
    if (kvrResultKey == "id") "{\"id\": \"$id\", \"count\": $count}" else "{\"item\": \"$id\", \"count\": $count}"

fun kvrJson(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

fun kvrShaped(name: String, pattern: List<String>, key: Map<String, String>, result: String, count: Int): String {
    val keyJson = key.entries.joinToString(",\n") { (symbol, id) -> "    \"$symbol\": ${kvrIngredient(id)}" }
    return kvrJson(
        "{",
        "  \"type\": \"minecraft:crafting_shaped\",",
        "  \"category\": \"misc\",",
        "  \"key\": {",
        keyJson + "",
        "  },",
        "  \"pattern\": [",
        pattern.joinToString(",\n") { "    \"$it\"" },
        "  ],",
        "  \"result\": ${kvrResult(result, count)}",
        "}"
    )
}

val kvrGeneratedDir = layout.buildDirectory.dir("generated/kvr-data")

val generateKvrData = tasks.register("generateKvrData") {
    group = "kvr"
    description = "Generates KVR recipes and loot tables for this Minecraft version"
    outputs.dir(kvrGeneratedDir)

    val recipeFolder = kvrRecipeFolder
    val lootFolder = kvrLootFolder
    val ing = ::kvrIngredient
    val out = kvrGeneratedDir

    doLast {
        val recipes = out.get().dir("data/kvr/$recipeFolder").asFile
        val loot = out.get().dir("data/kvr/$lootFolder/blocks").asFile
        recipes.deleteRecursively()
        loot.deleteRecursively()
        recipes.mkdirs()
        loot.mkdirs()

        // Tier 1 modules: a ring of 8 UpOre ingots with the hole in a type-specific corner/centre,
        // so the five shapes stay distinguishable on a 3x3 grid.
        val holes = mapOf(
            "cpu" to listOf("III", "I I", "III"),
            "gpu" to listOf(" II", "III", "III"),
            "ram" to listOf("II ", "III", "III"),
            "storage" to listOf("III", "III", " II"),
            "battery" to listOf("III", "III", "II ")
        )
        for ((type, pattern) in holes) {
            recipes.resolve("${type}_tier1.json").writeText(
                kvrShaped("${type}_tier1", pattern, mapOf("I" to "kvr:up_ingot"), "kvr:${type}_tier1", 1)
            )
        }

        // Chassis tier 1: the shell the player starts from.
        recipes.resolve("chassis_tier1.json").writeText(
            kvrShaped(
                "chassis_tier1",
                listOf("III", "IRI", "III"),
                mapOf("I" to "kvr:up_ingot", "R" to "minecraft:redstone"),
                "kvr:chassis_tier1",
                1
            )
        )

        // Tier upgrader template: the reusable smithing-style pattern that gates every upgrade.
        recipes.resolve("tier_upgrader_template.json").writeText(
            kvrShaped(
                "tier_upgrader_template",
                listOf(" I ", "IPI", " I "),
                mapOf("I" to "kvr:up_ingot", "P" to "minecraft:paper"),
                "kvr:tier_upgrader_template",
                1
            )
        )

        // UpOre processing.
        // UpOre block drops raw chunks.
        loot.resolve("up_ore.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:block\",",
                "  \"pools\": [",
                "    {",
                "      \"bonus_rolls\": 0.0,",
                "      \"conditions\": [ { \"condition\": \"minecraft:survives_explosion\" } ],",
                "      \"entries\": [ { \"type\": \"minecraft:item\", \"name\": \"kvr:up_raw\" } ],",
                "      \"rolls\": 1.0",
                "    }",
                "  ],",
                "  \"random_sequence\": \"kvr:blocks/up_ore\"",
                "}"
            )
        )

        recipes.resolve("up_ingot_from_smelting_raw.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:smelting\",",
                "  \"category\": \"misc\",",
                "  \"cookingtime\": 200,",
                "  \"experience\": 0.7,",
                "  \"ingredient\": ${ing("kvr:up_raw")},",
                "  \"result\": ${kvrResult("kvr:up_ingot", 1)}",
                "}"
            )
        )

    }
}

tasks.withType<ProcessResources>().configureEach {
    dependsOn(generateKvrData)
    from(kvrGeneratedDir)
}
