package com.example.game

import com.example.data.model.ComputedHeroStats
import com.example.data.model.HeroSave
import com.example.data.model.ZoneId
import kotlin.math.hypot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyPathfindingTest {
  private val defaultStats = ComputedHeroStats.calculate(HeroSave(), emptyList())

  @Test
  fun pathfinderRoutesAcrossBridgeAroundRiverInMeadow() {
    val meadow = RpgCatalog.buildZone(ZoneId.MEADOW)
    // Goblin side (19, 6) -> across river (cols 16..17 solid WATER except rows 8..9 BRIDGE) -> (14, 6)
    val path = GridPathfinder.findPath(
      zone = meadow,
      startCol = 19,
      startRow = 6,
      targetCol = 14,
      targetRow = 6,
    )

    assertTrue("Expected non-empty path around river", path.isNotEmpty())
    assertTrue("Path should end at target (14, 6)", path.last() == TilePoint(14, 6))
    assertTrue(
      "Path must cross the wooden bridge (col 16..17, row 8..9)",
      path.any { it.col in 16..17 && it.row in 8..9 },
    )
    assertFalse(
      "Path must never step onto solid tiles",
      path.any { meadow.tiles[it.row][it.col].solid },
    )
  }

  @Test
  fun pathfinderDoesNotClipDiagonalSolidCorners() {
    val zone = RpgCatalog.buildZone(ZoneId.MEADOW)
    // Tree at (11, 3), (12, 3), (12, 4). Stepping diagonally from (11, 4) to (12, 3) or (13, 3) must stay legal.
    val path = GridPathfinder.findPath(zone, 11, 4, 13, 3)
    assertTrue(path.isNotEmpty())
    var prev = TilePoint(11, 4)
    for (step in path) {
      assertFalse(zone.tiles[step.row][step.col].solid)
      val dc = step.col - prev.col
      val dr = step.row - prev.row
      if (dc != 0 && dr != 0) {
        assertFalse(
          "Diagonal step clipped corner from $prev to $step",
          zone.tiles[prev.row][prev.col + dc].solid || zone.tiles[prev.row + dr][prev.col].solid,
        )
      }
      prev = step
    }
  }

  @Test
  fun monsterOutsideAggroRangePatrolsWithoutAggro() {
    val engine = RpgGameEngine()
    engine.loadZone(ZoneId.MEADOW, openedChestIds = emptySet())
    // Hero spawns at (3, 9); goblin m_goblin_2 is at (19, 6), far outside agroRadius (260f)
    val farGoblin = engine.enemies.first { it.id == "m_goblin_2" }

    repeat(60) {
      engine.step(dtRaw = 0.03f, joyX = 0f, joyY = 0f, stats = defaultStats, currentHp = 100)
    }

    assertFalse("Far monster should remain out of aggro", farGoblin.isAggro)
    assertTrue("Patrolling monster should stay near home", hypot(farGoblin.x - farGoblin.homeX, farGoblin.y - farGoblin.homeY) < 130f)
  }

  @Test
  fun groundedMonsterPathfindsAroundRiverBridgeToReachHeroForCombat() {
    val engine = RpgGameEngine()
    engine.loadZone(ZoneId.MEADOW, openedChestIds = emptySet())
    // m_goblin_2 spawns at (19, 6); place hero at (15, 6) across the river within 260f (4 tiles = 208f)
    val ts = RpgCatalog.TILE_SIZE
    engine.heroX = 15.5f * ts
    engine.heroY = 6.5f * ts

    val goblin = engine.enemies.first { it.id == "m_goblin_2" }
    val initialDist = hypot(engine.heroX - goblin.x, engine.heroY - goblin.y)
    assertTrue("Initial distance ($initialDist) should be within agroRadius", initialDist <= goblin.archetype.agroRadius)

    var sawAggroAndPath = false
    var reachedCombatRange = false
    // Simulate ~6 seconds (goblin speed 112 px/s; detour via bridge at row 8 is ~320px -> ~3s)
    for (i in 0 until 240) {
      engine.step(dtRaw = 0.025f, joyX = 0f, joyY = 0f, stats = defaultStats, currentHp = 100)
      if (goblin.isAggro && goblin.path.isNotEmpty()) {
        sawAggroAndPath = true
      }
      val dist = hypot(engine.heroX - goblin.x, engine.heroY - goblin.y)
      if (dist <= engine.heroRadius + goblin.archetype.radius + 10f) {
        reachedCombatRange = true
        break
      }
    }

    assertTrue("Goblin should detect hero and compute a path around the river", sawAggroAndPath)
    assertTrue("Goblin should cross the bridge and reach melee combat range", reachedCombatRange)
  }

  @Test
  fun flyingBatChasesDirectlyAcrossMoatWithinAggroRange() {
    val engine = RpgGameEngine()
    engine.loadZone(ZoneId.RUINS, openedChestIds = emptySet())
    val ts = RpgCatalog.TILE_SIZE
    // r_bat_1 spawns at (7, 5); moat is at col 12 rows 3..7. Place hero at (11, 5) or across moat (12.5? no, 13.5? wait dist = 6*52=312 > 280)
    // Put hero at (11, 5) -> wait let's put hero at (13, 5) vs bat moved to (10, 5): distance 3 tiles = 156f <= 280f across water moat at col 12!
    val bat = engine.enemies.first { it.id == "r_bat_1" }
    bat.x = 10.5f * ts
    bat.y = 5.5f * ts
    engine.heroX = 13.5f * ts
    engine.heroY = 5.5f * ts

    repeat(60) {
      engine.step(dtRaw = 0.025f, joyX = 0f, joyY = 0f, stats = defaultStats, currentHp = 100)
    }

    assertTrue("Bat should aggro hero across the moat", bat.isAggro)
    assertTrue("Bat should fly across col 12 water toward hero", bat.x > 12f * ts)
  }
}
