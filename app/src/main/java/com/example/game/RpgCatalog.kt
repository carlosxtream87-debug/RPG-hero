package com.example.game

import com.example.data.model.HeroItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.ZoneId
import kotlin.math.max
import kotlin.random.Random

enum class ChestTier(
  val label: String,
  val minRarity: ItemRarity,
  val bonusCoins: Int,
  val bonusXp: Int,
) {
  WOOD("Baú de Madeira", ItemRarity.UNCOMMON, 25, 25),
  IRON("Baú de Ferro", ItemRarity.RARE, 55, 50),
  GOLD("Baú Dourado", ItemRarity.EPIC, 110, 95),
  LEGENDARY("Baú Lendário", ItemRarity.LEGENDARY, 220, 180),
}

enum class MonsterArchetype(
  val displayName: String,
  val maxHp: Int,
  val attack: Int,
  val speed: Float,
  val radius: Float,
  val agroRadius: Float,
  val xpReward: Int,
  val coinMin: Int,
  val coinMax: Int,
  val isBoss: Boolean = false,
  val primaryHex: Long,
  val accentHex: Long,
) {
  SLIME(
    displayName = "Slime Esmeralda",
    maxHp = 38,
    attack = 7,
    speed = 72f,
    radius = 16f,
    agroRadius = 210f,
    xpReward = 18,
    coinMin = 5,
    coinMax = 11,
    primaryHex = 0xFF22C55E,
    accentHex = 0xFFA7F3D0,
  ),
  GOBLIN(
    displayName = "Goblin Saqueador",
    maxHp = 65,
    attack = 12,
    speed = 112f,
    radius = 17f,
    agroRadius = 260f,
    xpReward = 30,
    coinMin = 10,
    coinMax = 20,
    primaryHex = 0xFF65A30D,
    accentHex = 0xFFD97706,
  ),
  BAT(
    displayName = "Morcego Sombrio",
    maxHp = 58,
    attack = 14,
    speed = 148f,
    radius = 15f,
    agroRadius = 280f,
    xpReward = 34,
    coinMin = 12,
    coinMax = 22,
    primaryHex = 0xFF7E22CE,
    accentHex = 0xFFF43F5E,
  ),
  SKELETON(
    displayName = "Esqueleto Guardião",
    maxHp = 115,
    attack = 18,
    speed = 104f,
    radius = 18f,
    agroRadius = 270f,
    xpReward = 55,
    coinMin = 18,
    coinMax = 34,
    primaryHex = 0xFFE2E8F0,
    accentHex = 0xFF38BDF8,
  ),
  KNIGHT(
    displayName = "Cavaleiro de Ferro",
    maxHp = 185,
    attack = 26,
    speed = 98f,
    radius = 20f,
    agroRadius = 290f,
    xpReward = 90,
    coinMin = 30,
    coinMax = 55,
    primaryHex = 0xFF475569,
    accentHex = 0xFFEF4444,
  ),
  BOSS_GOBLIN_KING(
    displayName = "Rei Goblin",
    maxHp = 260,
    attack = 22,
    speed = 108f,
    radius = 27f,
    agroRadius = 330f,
    xpReward = 150,
    coinMin = 70,
    coinMax = 110,
    isBoss = true,
    primaryHex = 0xFF84CC16,
    accentHex = 0xFFFBBF24,
  ),
  BOSS_SCARLET_LORD(
    displayName = "Lorde Escarlate",
    maxHp = 480,
    attack = 34,
    speed = 118f,
    radius = 30f,
    agroRadius = 360f,
    xpReward = 320,
    coinMin = 140,
    coinMax = 220,
    isBoss = true,
    primaryHex = 0xFFDC2626,
    accentHex = 0xFFF59E0B,
  ),
}

data class ItemTemplate(
  val templateId: String,
  val name: String,
  val slot: ItemSlot,
  val rarity: ItemRarity,
  val baseAttack: Int,
  val baseDefense: Int,
  val baseHp: Int,
  val baseCrit: Int,
  val baseCoinPct: Int,
  val sellPrice: Int,
  val colorHex: String,
)

enum class TileType(val solid: Boolean) {
  GRASS(false),
  FLOWERS(false),
  PATH(false),
  WATER(true),
  BRIDGE(false),
  TREE(true),
  STONE_WALL(true),
  RUIN_FLOOR(false),
  CRACKED_PILLAR(true),
  TORCH(true),
  BASALT(false),
  LAVA(true),
  PORTAL_NEXT(false),
  PORTAL_PREV(false),
}

data class ChestSpawn(
  val id: String,
  val tileX: Int,
  val tileY: Int,
  val tier: ChestTier,
)

data class MonsterSpawn(
  val id: String,
  val archetype: MonsterArchetype,
  val tileX: Int,
  val tileY: Int,
  val respawnSeconds: Float,
)

data class ZoneMapData(
  val zoneId: ZoneId,
  val cols: Int,
  val rows: Int,
  val tiles: Array<Array<TileType>>,
  val spawnTileX: Int,
  val spawnTileY: Int,
  val returnSpawnTileX: Int,
  val returnSpawnTileY: Int,
  val merchantTile: Pair<Int, Int>?,
  val chests: List<ChestSpawn>,
  val monsters: List<MonsterSpawn>,
)

object RpgCatalog {
  const val TILE_SIZE = 52f
  const val MAP_COLS = 24
  const val MAP_ROWS = 18

  private val templates = listOf(
    // Weapons (Espadas)
    ItemTemplate("wooden_blade", "Espada do Aprendiz", ItemSlot.WEAPON, ItemRarity.COMMON, 6, 0, 0, 2, 0, 10, "#D97706"),
    ItemTemplate("bronze_dagger", "Adaga de Bronze", ItemSlot.WEAPON, ItemRarity.COMMON, 9, 0, 0, 5, 0, 16, "#CD7F32"),
    ItemTemplate("iron_sword", "Espada de Ferro", ItemSlot.WEAPON, ItemRarity.UNCOMMON, 13, 1, 0, 4, 0, 28, "#94A3B8"),
    ItemTemplate("forest_saber", "Sabre da Clareira", ItemSlot.WEAPON, ItemRarity.UNCOMMON, 17, 0, 10, 7, 5, 40, "#22C55E"),
    ItemTemplate("cobalt_blade", "Lâmina de Cobalto", ItemSlot.WEAPON, ItemRarity.RARE, 23, 3, 15, 9, 0, 68, "#38BDF8"),
    ItemTemplate("wind_scimitar", "Alfanje do Vento", ItemSlot.WEAPON, ItemRarity.RARE, 27, 0, 10, 14, 10, 82, "#06B6D4"),
    ItemTemplate("solar_sword", "Espada Solar", ItemSlot.WEAPON, ItemRarity.EPIC, 35, 4, 25, 14, 10, 140, "#A855F7"),
    ItemTemplate("ruby_greatsword", "Lâmina Rubi", ItemSlot.WEAPON, ItemRarity.EPIC, 41, 2, 35, 16, 5, 165, "#F43F5E"),
    ItemTemplate("eldoria_excalibur", "Excalibur de Eldoria", ItemSlot.WEAPON, ItemRarity.LEGENDARY, 56, 8, 60, 22, 20, 320, "#FBBF24"),
    ItemTemplate("dragon_fang", "Presa do Dragão", ItemSlot.WEAPON, ItemRarity.LEGENDARY, 64, 5, 50, 26, 15, 360, "#F59E0B"),

    // Armor / Shields (Escudos & Armaduras)
    ItemTemplate("oak_shield", "Escudo de Carvalho", ItemSlot.ARMOR, ItemRarity.COMMON, 0, 4, 15, 0, 0, 14, "#B45309"),
    ItemTemplate("iron_mail", "Escudo de Ferro", ItemSlot.ARMOR, ItemRarity.UNCOMMON, 1, 8, 30, 0, 0, 32, "#64748B"),
    ItemTemplate("emerald_aegis", "Égide Esmeralda", ItemSlot.ARMOR, ItemRarity.RARE, 2, 14, 55, 2, 5, 72, "#10B981"),
    ItemTemplate("royal_bastion", "Bastião Real", ItemSlot.ARMOR, ItemRarity.EPIC, 4, 22, 90, 4, 10, 155, "#A855F7"),
    ItemTemplate("celestial_aegis", "Escudo Celestial", ItemSlot.ARMOR, ItemRarity.LEGENDARY, 8, 32, 150, 6, 15, 340, "#FBBF24"),

    // Charms (Amuletos)
    ItemTemplate("lucky_clover", "Trevo Dourado", ItemSlot.CHARM, ItemRarity.UNCOMMON, 2, 1, 10, 4, 18, 34, "#22C55E"),
    ItemTemplate("ruby_ring", "Anel de Rubi", ItemSlot.CHARM, ItemRarity.RARE, 8, 2, 20, 10, 10, 75, "#38BDF8"),
    ItemTemplate("treasure_pendant", "Pingente do Tesouro", ItemSlot.CHARM, ItemRarity.RARE, 5, 4, 25, 6, 35, 90, "#0EA5E9"),
    ItemTemplate("arcane_medallion", "Medalhão Arcano", ItemSlot.CHARM, ItemRarity.EPIC, 12, 8, 45, 14, 25, 160, "#A855F7"),
    ItemTemplate("eldoria_heart", "Coração de Eldoria", ItemSlot.CHARM, ItemRarity.LEGENDARY, 20, 15, 90, 18, 45, 350, "#FBBF24"),
  )

  fun rollChestLoot(
    userId: String,
    chestTier: ChestTier,
    heroLevel: Int,
    currentBestWeaponAttack: Int,
  ): HeroItem {
    val targetRarity = when (chestTier) {
      ChestTier.WOOD -> if (Random.nextFloat() < 0.35f) ItemRarity.RARE else ItemRarity.UNCOMMON
      ChestTier.IRON -> if (Random.nextFloat() < 0.35f) ItemRarity.EPIC else ItemRarity.RARE
      ChestTier.GOLD -> if (Random.nextFloat() < 0.30f) ItemRarity.LEGENDARY else ItemRarity.EPIC
      ChestTier.LEGENDARY -> ItemRarity.LEGENDARY
    }

    // Strongly favor swords (60%) or armor/charms (40%) with guaranteed meaningful stats
    val desiredSlot = when {
      Random.nextFloat() < 0.58f -> ItemSlot.WEAPON
      Random.nextFloat() < 0.55f -> ItemSlot.ARMOR
      else -> ItemSlot.CHARM
    }

    val candidates = templates.filter {
      it.slot == desiredSlot && it.rarity.tierOrder >= targetRarity.tierOrder
    }.ifEmpty {
      templates.filter { it.rarity.tierOrder >= targetRarity.tierOrder }
    }.ifEmpty {
      templates.filter { it.rarity == ItemRarity.LEGENDARY }
    }

    val picked = candidates.random()
    val levelScale = max(0, heroLevel - 1)
    val rollJitter = Random.nextInt(0, 4)

    // Ensure swords from Iron/Gold/Legendary chests feel rewarding & often upgrade current weapon
    val atkBoost = if (picked.slot == ItemSlot.WEAPON) {
      val base = picked.baseAttack + levelScale * 2 + rollJitter
      if (chestTier.minRarity.tierOrder >= ItemRarity.RARE.tierOrder) {
        max(base, currentBestWeaponAttack + Random.nextInt(2, 6))
      } else {
        base
      }
    } else {
      picked.baseAttack + (levelScale / 2)
    }

    val defBoost = picked.baseDefense + if (picked.slot == ItemSlot.ARMOR) levelScale + Random.nextInt(0, 3) else 0
    val hpBoost = picked.baseHp + if (picked.slot != ItemSlot.WEAPON) levelScale * 5 else 0
    val critBoost = picked.baseCrit + if (Random.nextFloat() < 0.35f) 2 else 0
    val coinPct = picked.baseCoinPct
    val sell = picked.sellPrice + levelScale * 8

    val suffix = when {
      rollJitter >= 3 && picked.rarity.tierOrder >= ItemRarity.RARE.tierOrder -> " +"
      else -> ""
    }

    return HeroItem(
      userId = userId,
      templateId = picked.templateId,
      name = "${picked.name}$suffix".take(60),
      slot = picked.slot.key,
      rarity = picked.rarity.key,
      attackBonus = atkBoost.toLong(),
      defenseBonus = defBoost.toLong(),
      hpBonus = hpBoost.toLong(),
      critBonus = critBoost.toLong(),
      coinBonusPct = coinPct.toLong(),
      sellPrice = sell.toLong(),
      colorHex = picked.colorHex,
    )
  }

  fun buildZone(zoneId: ZoneId): ZoneMapData = when (zoneId) {
    ZoneId.MEADOW -> buildMeadowZone()
    ZoneId.RUINS -> buildRuinsZone()
    ZoneId.KEEP -> buildKeepZone()
  }

  private fun createEmptyGrid(fill: TileType, wall: TileType): Array<Array<TileType>> {
    return Array(MAP_ROWS) { r ->
      Array(MAP_COLS) { c ->
        if (r == 0 || r == MAP_ROWS - 1 || c == 0 || c == MAP_COLS - 1) wall else fill
      }
    }
  }

  private fun buildMeadowZone(): ZoneMapData {
    val g = createEmptyGrid(TileType.GRASS, TileType.TREE)

    // River cutting through right third with a wooden bridge
    for (r in 1 until MAP_ROWS - 1) {
      g[r][16] = TileType.WATER
      g[r][17] = TileType.WATER
    }
    for (r in 8..9) {
      g[r][16] = TileType.BRIDGE
      g[r][17] = TileType.BRIDGE
    }

    // Winding dirt path from spawn (3, 9) across bridge to portal (22, 9)
    for (c in 3..15) g[9][c] = TileType.PATH
    for (c in 18..22) g[9][c] = TileType.PATH
    for (r in 4..9) g[r][8] = TileType.PATH

    // Decorative tree groves & flower patches
    val trees = listOf(
      3 to 3, 4 to 3, 3 to 4,
      11 to 3, 12 to 3, 12 to 4,
      6 to 13, 7 to 13, 6 to 14,
      13 to 13, 14 to 13,
      20 to 3, 21 to 4,
    )
    trees.forEach { (c, r) -> g[r][c] = TileType.TREE }

    val flowers = listOf(
      4 to 7, 5 to 11, 10 to 6, 13 to 7, 19 to 12, 20 to 13, 10 to 14, 14 to 4
    )
    flowers.forEach { (c, r) -> g[r][c] = TileType.FLOWERS }

    // Portal to Ruins on east edge
    g[9][22] = TileType.PORTAL_NEXT

    return ZoneMapData(
      zoneId = ZoneId.MEADOW,
      cols = MAP_COLS,
      rows = MAP_ROWS,
      tiles = g,
      spawnTileX = 3,
      spawnTileY = 9,
      returnSpawnTileX = 20,
      returnSpawnTileY = 9,
      merchantTile = 6 to 4,
      chests = listOf(
        ChestSpawn("meadow_chest_1", 4, 14, ChestTier.WOOD),
        ChestSpawn("meadow_chest_2", 14, 3, ChestTier.WOOD),
        ChestSpawn("meadow_chest_3", 21, 14, ChestTier.IRON),
        ChestSpawn("meadow_chest_4", 21, 2, ChestTier.GOLD),
      ),
      monsters = listOf(
        MonsterSpawn("m_slime_1", MonsterArchetype.SLIME, 7, 6, 9f),
        MonsterSpawn("m_slime_2", MonsterArchetype.SLIME, 11, 11, 9f),
        MonsterSpawn("m_slime_3", MonsterArchetype.SLIME, 13, 6, 10f),
        MonsterSpawn("m_goblin_1", MonsterArchetype.GOBLIN, 10, 14, 12f),
        MonsterSpawn("m_goblin_2", MonsterArchetype.GOBLIN, 19, 6, 12f),
        MonsterSpawn("m_goblin_3", MonsterArchetype.GOBLIN, 19, 12, 12f),
        MonsterSpawn("m_boss_goblin", MonsterArchetype.BOSS_GOBLIN_KING, 20, 4, 28f),
      ),
    )
  }

  private fun buildRuinsZone(): ZoneMapData {
    val g = createEmptyGrid(TileType.RUIN_FLOOR, TileType.STONE_WALL)

    // Ancient stone pillars & torch sconces
    val pillars = listOf(
      5 to 4, 5 to 13, 9 to 6, 9 to 11, 14 to 4, 14 to 13, 18 to 7, 18 to 10
    )
    pillars.forEach { (c, r) -> g[r][c] = TileType.CRACKED_PILLAR }

    val torches = listOf(3 to 2, 12 to 2, 20 to 2, 3 to 15, 12 to 15, 20 to 15)
    torches.forEach { (c, r) -> g[r][c] = TileType.TORCH }

    // Moat of dark water in middle with bridges
    for (r in 3..14) {
      if (r !in 8..9) {
        g[r][12] = TileType.WATER
      } else {
        g[r][12] = TileType.BRIDGE
      }
    }

    g[9][1] = TileType.PORTAL_PREV
    g[9][22] = TileType.PORTAL_NEXT

    return ZoneMapData(
      zoneId = ZoneId.RUINS,
      cols = MAP_COLS,
      rows = MAP_ROWS,
      tiles = g,
      spawnTileX = 2,
      spawnTileY = 9,
      returnSpawnTileX = 20,
      returnSpawnTileY = 9,
      merchantTile = null,
      chests = listOf(
        ChestSpawn("ruins_chest_1", 4, 3, ChestTier.IRON),
        ChestSpawn("ruins_chest_2", 10, 14, ChestTier.IRON),
        ChestSpawn("ruins_chest_3", 16, 3, ChestTier.GOLD),
        ChestSpawn("ruins_chest_4", 21, 14, ChestTier.GOLD),
      ),
      monsters = listOf(
        MonsterSpawn("r_bat_1", MonsterArchetype.BAT, 7, 5, 10f),
        MonsterSpawn("r_bat_2", MonsterArchetype.BAT, 8, 13, 10f),
        MonsterSpawn("r_skel_1", MonsterArchetype.SKELETON, 10, 8, 12f),
        MonsterSpawn("r_skel_2", MonsterArchetype.SKELETON, 15, 6, 12f),
        MonsterSpawn("r_skel_3", MonsterArchetype.SKELETON, 16, 12, 12f),
        MonsterSpawn("r_knight_1", MonsterArchetype.KNIGHT, 20, 6, 16f),
        MonsterSpawn("r_knight_2", MonsterArchetype.KNIGHT, 20, 12, 16f),
      ),
    )
  }

  private fun buildKeepZone(): ZoneMapData {
    val g = createEmptyGrid(TileType.BASALT, TileType.STONE_WALL)

    // Lava channels with bridges leading to Boss Arena
    for (r in 2..15) {
      if (r !in 8..9) {
        g[r][7] = TileType.LAVA
        g[r][15] = TileType.LAVA
      } else {
        g[r][7] = TileType.BRIDGE
        g[r][15] = TileType.BRIDGE
      }
    }

    val pillars = listOf(4 to 4, 4 to 13, 11 to 4, 11 to 13, 18 to 4, 18 to 13)
    pillars.forEach { (c, r) -> g[r][c] = TileType.CRACKED_PILLAR }

    val torches = listOf(2 to 2, 10 to 2, 21 to 2, 2 to 15, 10 to 15, 21 to 15)
    torches.forEach { (c, r) -> g[r][c] = TileType.TORCH }

    g[9][1] = TileType.PORTAL_PREV

    return ZoneMapData(
      zoneId = ZoneId.KEEP,
      cols = MAP_COLS,
      rows = MAP_ROWS,
      tiles = g,
      spawnTileX = 2,
      spawnTileY = 9,
      returnSpawnTileX = 2,
      returnSpawnTileY = 9,
      merchantTile = null,
      chests = listOf(
        ChestSpawn("keep_chest_1", 5, 2, ChestTier.GOLD),
        ChestSpawn("keep_chest_2", 13, 15, ChestTier.GOLD),
        ChestSpawn("keep_chest_3", 21, 3, ChestTier.LEGENDARY),
        ChestSpawn("keep_chest_4", 21, 14, ChestTier.LEGENDARY),
      ),
      monsters = listOf(
        MonsterSpawn("k_bat_1", MonsterArchetype.BAT, 5, 7, 10f),
        MonsterSpawn("k_skel_1", MonsterArchetype.SKELETON, 10, 6, 12f),
        MonsterSpawn("k_knight_1", MonsterArchetype.KNIGHT, 11, 11, 14f),
        MonsterSpawn("k_knight_2", MonsterArchetype.KNIGHT, 17, 6, 15f),
        MonsterSpawn("k_knight_3", MonsterArchetype.KNIGHT, 17, 12, 15f),
        MonsterSpawn("k_boss_lord", MonsterArchetype.BOSS_SCARLET_LORD, 20, 9, 30f),
      ),
    )
  }
}
