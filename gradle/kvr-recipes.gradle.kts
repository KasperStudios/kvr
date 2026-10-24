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

fun kvrSmithing(template: String, base: String, addition: String, result: String): String {
    return kvrJson(
        "{",
        "  \"type\": \"minecraft:smithing_transform\",",
        "  \"template\": ${kvrIngredient(template)},",
        "  \"base\": ${kvrIngredient(base)},",
        "  \"addition\": ${kvrIngredient(addition)},",
        "  \"result\": ${kvrResult(result, 1)}",
        "}"
    )
}

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
        val configuredFeatures = out.get().dir("data/kvr/worldgen/configured_feature").asFile
        val placedFeatures = out.get().dir("data/kvr/worldgen/placed_feature").asFile
        val neoforgeModifiers = out.get().dir("data/kvr/neoforge/biome_modifier").asFile

        recipes.deleteRecursively()
        loot.deleteRecursively()
        configuredFeatures.deleteRecursively()
        placedFeatures.deleteRecursively()
        neoforgeModifiers.deleteRecursively()

        recipes.mkdirs()
        loot.mkdirs()
        configuredFeatures.mkdirs()
        placedFeatures.mkdirs()
        neoforgeModifiers.mkdirs()

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

        // Smithing upgrades for modules and chassis across all 5 tiers (1->2, 2->3, 3->4, 4->5):
        // Tier 1 -> Tier 2: kvr:up_ingot
        // Tier 2 -> Tier 3: kvr:up_ingot
        // Tier 3 -> Tier 4: kvr:compressed_up_ore
        // Tier 4 -> Tier 5: kvr:compressed_up_ore
        val itemTypes = listOf("cpu", "gpu", "ram", "storage", "battery", "chassis")
        val tiers = listOf(
            Triple(1, 2, "kvr:up_ingot"),
            Triple(2, 3, "kvr:up_ingot"),
            Triple(3, 4, "kvr:compressed_up_ore"),
            Triple(4, 5, "kvr:compressed_up_ore")
        )
        for (item in itemTypes) {
            for ((from, to, addition) in tiers) {
                recipes.resolve("${item}_tier${to}_smithing.json").writeText(
                    kvrSmithing(
                        "kvr:tier_upgrader_template",
                        "kvr:${item}_tier$from",
                        addition,
                        "kvr:${item}_tier$to"
                    )
                )
            }
        }

        // UpOre processing.
        // Template crafting recipe
        recipes.resolve("tier_upgrader_template.json").writeText(
            kvrShaped(
                "tier_upgrader_template",
                listOf(" I ", "IPI", " I "),
                mapOf("I" to "kvr:up_ingot", "P" to "minecraft:paper"),
                "kvr:tier_upgrader_template",
                1
            )
        )

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

        loot.resolve("compressed_up_ore.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:block\",",
                "  \"pools\": [",
                "    {",
                "      \"bonus_rolls\": 0.0,",
                "      \"conditions\": [ { \"condition\": \"minecraft:survives_explosion\" } ],",
                "      \"entries\": [ { \"type\": \"minecraft:item\", \"name\": \"kvr:compressed_up_ore\" } ],",
                "      \"rolls\": 1.0",
                "    }",
                "  ],",
                "  \"random_sequence\": \"kvr:blocks/compressed_up_ore\"",
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

        // Chassis & Glasses dyeing special recipe
        recipes.resolve("chassis_dye.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"kvr:crafting_special_chassis_dye\",",
                "  \"category\": \"misc\"",
                "}"
            )
        )

        // Compressed UpOre block recipe: 9 ingots -> 1 compressed block in crafting grid, and 1 block -> 9 ingots
        // (Wait, SPEC says: 64 ore = 1 block. Let's provide crafting recipe or shapeless unpacking/packing)
        recipes.resolve("compressed_up_ore.json").writeText(
            kvrShaped(
                "compressed_up_ore",
                listOf("III", "III", "III"),
                mapOf("I" to "kvr:up_ingot"),
                "kvr:compressed_up_ore",
                1
            )
        )

        recipes.resolve("up_ingot_from_compressed.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:crafting_shapeless\",",
                "  \"category\": \"misc\",",
                "  \"ingredients\": [ ${ing("kvr:compressed_up_ore")} ],",
                "  \"result\": ${kvrResult("kvr:up_ingot", 9)}",
                "}"
            )
        )

        // Worldgen: Configured Features (Overworld + Nether)
        configuredFeatures.resolve("ore_up_overworld.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:ore\",",
                "  \"config\": {",
                "    \"discard_chance_on_air_exposure\": 0.0,",
                "    \"size\": 6,",
                "    \"targets\": [",
                "      {",
                "        \"state\": {",
                "          \"Name\": \"kvr:up_ore\"",
                "        },",
                "        \"target\": {",
                "          \"predicate_type\": \"minecraft:tag_match\",",
                "          \"tag\": \"minecraft:stone_ore_replaceables\"",
                "        }",
                "      },",
                "      {",
                "        \"state\": {",
                "          \"Name\": \"kvr:up_ore\"",
                "        },",
                "        \"target\": {",
                "          \"predicate_type\": \"minecraft:tag_match\",",
                "          \"tag\": \"minecraft:deepslate_ore_replaceables\"",
                "        }",
                "      }",
                "    ]",
                "  }",
                "}"
            )
        )

        configuredFeatures.resolve("ore_up_nether.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"minecraft:ore\",",
                "  \"config\": {",
                "    \"discard_chance_on_air_exposure\": 0.0,",
                "    \"size\": 8,",
                "    \"targets\": [",
                "      {",
                "        \"state\": {",
                "          \"Name\": \"kvr:up_ore\"",
                "        },",
                "        \"target\": {",
                "          \"block\": \"minecraft:netherrack\",",
                "          \"predicate_type\": \"minecraft:block_match\"",
                "        }",
                "      }",
                "    ]",
                "  }",
                "}"
            )
        )

        // Worldgen: Placed Features
        placedFeatures.resolve("ore_up_overworld.json").writeText(
            kvrJson(
                "{",
                "  \"feature\": \"kvr:ore_up_overworld\",",
                "  \"placement\": [",
                "    {",
                "      \"type\": \"minecraft:count\",",
                "      \"count\": 8",
                "    },",
                "    {",
                "      \"type\": \"minecraft:in_square\"",
                "    },",
                "    {",
                "      \"type\": \"minecraft:height_range\",",
                "      \"height\": {",
                "        \"type\": \"minecraft:uniform\",",
                "        \"max_inclusive\": {",
                "          \"absolute\": 64",
                "        },",
                "        \"min_inclusive\": {",
                "          \"above_bottom\": 0",
                "        }",
                "      }",
                "    },",
                "    {",
                "      \"type\": \"minecraft:biome\"",
                "    }",
                "  ]",
                "}"
            )
        )

        placedFeatures.resolve("ore_up_nether.json").writeText(
            kvrJson(
                "{",
                "  \"feature\": \"kvr:ore_up_nether\",",
                "  \"placement\": [",
                "    {",
                "      \"type\": \"minecraft:count\",",
                "      \"count\": 10",
                "    },",
                "    {",
                "      \"type\": \"minecraft:in_square\"",
                "    },",
                "    {",
                "      \"type\": \"minecraft:height_range\",",
                "      \"height\": {",
                "        \"type\": \"minecraft:uniform\",",
                "        \"max_inclusive\": {",
                "          \"below_top\": 10",
                "        },",
                "        \"min_inclusive\": {",
                "          \"above_bottom\": 10",
                "        }",
                "      }",
                "    },",
                "    {",
                "      \"type\": \"minecraft:biome\"",
                "    }",
                "  ]",
                "}"
            )
        )

        // NeoForge Biome Modifiers
        neoforgeModifiers.resolve("add_ore_up_overworld.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"neoforge:add_features\",",
                "  \"biomes\": \"#minecraft:is_overworld\",",
                "  \"features\": \"kvr:ore_up_overworld\",",
                "  \"step\": \"underground_ores\"",
                "}"
            )
        )

        neoforgeModifiers.resolve("add_ore_up_nether.json").writeText(
            kvrJson(
                "{",
                "  \"type\": \"neoforge:add_features\",",
                "  \"biomes\": \"#minecraft:is_nether\",",
                "  \"features\": \"kvr:ore_up_nether\",",
                "  \"step\": \"underground_ores\"",
                "}"
            )
        )

    }
}

tasks.withType<ProcessResources>().configureEach {
    dependsOn(generateKvrData)
    from(kvrGeneratedDir)
}
