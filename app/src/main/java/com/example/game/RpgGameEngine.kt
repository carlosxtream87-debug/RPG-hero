package com.example.game

import com.example.data.model.ComputedHeroStats
import com.example.data.model.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class EnemyEntity(
  val id: String,
  val archetype: MonsterArchetype,
  var x: Float,
  var y: Float,
  val homeX: Float,
  val homeY: Float,
  var hp: Int,
  val maxHp: Int,
  var alive: Boolean = true,
  var respawnTimer: Float = 0f,
  val respawnDuration: Float,
  var attackCooldown: Float = 0f,
  var hitFlashTimer: Float = 0f,
  var knockVx: Float = 0f,
  var knockVy: Float = 0f,
  var patrolAngle: Float = Random.nextFloat() * 6.28f,
  var patrolTimer: Float = Random.nextFloat() * 2f,
  var isAggro: Boolean = false,
  var path: List<TilePoint> = emptyList(),
  var pathIndex: Int = 0,
  var repathTimer: Float = 0f,
)

enum class DropKind { COIN, HEART }

data class WorldDrop(
  val id: String,
  val kind: DropKind,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  val amount: Int,
  var age: Float = 0f,
)

data class ChestEntity(
  val id: String,
  val x: Float,
  val y: Float,
  val tier: ChestTier,
  var opened: Boolean = false,
  val isBossBonus: Boolean = false,
)

data class FloatingText(
  val id: String,
  val text: String,
  var x: Float,
  var y: Float,
  val colorHex: Long,
  var life: Float = 0.95f,
  val maxLife: Float = 0.95f,
  val isBig: Boolean = false,
)

data class ParticleSpark(
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  val colorHex: Long,
  var life: Float,
  val maxLife: Float,
  val radius: Float,
)

data class GameEvents(
  val coinsCollected: Int = 0,
  val xpGained: Int = 0,
  val monstersKilled: Int = 0,
  val hpDelta: Int = 0,
  val chestToOpen: ChestEntity? = null,
  val portalTriggered: TileType? = null,
  val merchantTriggered: Boolean = false,
  val hitLanded: Boolean = false,
)

class RpgGameEngine {
  var zoneData: ZoneMapData = RpgCatalog.buildZone(ZoneId.MEADOW)
    private set

  var heroX: Float = 3.5f * RpgCatalog.TILE_SIZE
  var heroY: Float = 9.5f * RpgCatalog.TILE_SIZE
  val heroRadius: Float = 16f
  var facingAngle: Float = 0f // radians, 0 = right

  // Combat & Action states
  var swingTimer: Float = 0f
  val swingDuration: Float = 0.24f
  var swingCooldown: Float = 0f
  var swingStartAngle: Float = 0f
  var swingEndAngle: Float = 0f
  private val hitEnemiesThisSwing = mutableSetOf<String>()

  var dashTimer: Float = 0f
  var dashCooldown: Float = 0f
  private var dashDirX: Float = 1f
  private var dashDirY: Float = 0f

  var heroHurtTimer: Float = 0f
  var heroKnockVx: Float = 0f
  var heroKnockVy: Float = 0f
  var screenShake: Float = 0f
  var walkCycle: Float = 0f
  var worldTime: Float = 0f

  val enemies = mutableListOf<EnemyEntity>()
  val drops = mutableListOf<WorldDrop>()
  val chests = mutableListOf<ChestEntity>()
  val floatingTexts = mutableListOf<FloatingText>()
  val particles = mutableListOf<ParticleSpark>()

  var nearChest: ChestEntity? = null
    private set
  var nearPortal: TileType? = null
    private set
  var nearMerchant: Boolean = false
    private set

  private var portalCooldown: Float = 0f
  private var dropCounter = 0L

  fun loadZone(zoneId: ZoneId, openedChestIds: Set<String>, fromNextZone: Boolean = false) {
    zoneData = RpgCatalog.buildZone(zoneId)
    val ts = RpgCatalog.TILE_SIZE
    val sx = if (fromNextZone) zoneData.returnSpawnTileX else zoneData.spawnTileX
    val sy = if (fromNextZone) zoneData.returnSpawnTileY else zoneData.spawnTileY
    heroX = (sx + 0.5f) * ts
    heroY = (sy + 0.5f) * ts
    portalCooldown = 1.0f

    enemies.clear()
    zoneData.monsters.forEach { sp ->
      enemies += EnemyEntity(
        id = sp.id,
        archetype = sp.archetype,
        x = (sp.tileX + 0.5f) * ts,
        y = (sp.tileY + 0.5f) * ts,
        homeX = (sp.tileX + 0.5f) * ts,
        homeY = (sp.tileY + 0.5f) * ts,
        hp = sp.archetype.maxHp,
        maxHp = sp.archetype.maxHp,
        respawnDuration = sp.respawnSeconds,
      )
    }

    chests.clear()
    zoneData.chests.forEach { ch ->
      chests += ChestEntity(
        id = ch.id,
        x = (ch.tileX + 0.5f) * ts,
        y = (ch.tileY + 0.5f) * ts,
        tier = ch.tier,
        opened = ch.id in openedChestIds,
      )
    }

    drops.clear()
    floatingTexts.clear()
    particles.clear()
    updateProximities()
  }

  fun triggerSwordOrInteract(): ChestEntity? {
    val chest = nearChest
    if (chest != null && !chest.opened) {
      return chest
    }
    if (swingCooldown <= 0f && swingTimer <= 0f) {
      swingTimer = swingDuration
      swingCooldown = 0.30f
      swingStartAngle = facingAngle - 1.25f
      swingEndAngle = facingAngle + 1.25f
      hitEnemiesThisSwing.clear()
    }
    return null
  }

  fun triggerDash(): Boolean {
    if (dashCooldown > 0f || dashTimer > 0f) return false
    dashTimer = 0.16f
    dashCooldown = 1.15f
    dashDirX = cos(facingAngle)
    dashDirY = sin(facingAngle)
    spawnBurst(heroX, heroY, 0xFF38BDF8, 8, 90f)
    return true
  }

  fun markChestOpened(chestId: String) {
    chests.firstOrNull { it.id == chestId }?.let {
      it.opened = true
      spawnBurst(it.x, it.y, 0xFFFBBF24, 18, 150f)
      addFloatingText("BAÚ ABERTO!", it.x, it.y - 24f, 0xFFFBBF24, isBig = true)
    }
  }

  fun addFloatingText(
    text: String,
    x: Float,
    y: Float,
    colorHex: Long,
    isBig: Boolean = false,
  ) {
    floatingTexts += FloatingText(
      id = "ft_${++dropCounter}",
      text = text,
      x = x,
      y = y,
      colorHex = colorHex,
      isBig = isBig,
    )
  }

  fun step(
    dtRaw: Float,
    joyX: Float,
    joyY: Float,
    stats: ComputedHeroStats,
    currentHp: Int,
  ): GameEvents {
    val dt = dtRaw.coerceIn(0.001f, 0.05f)
    worldTime += dt

    var coinsCollected = 0
    var xpGained = 0
    var monstersKilled = 0
    var hpDelta = 0
    var hitLanded = false

    if (portalCooldown > 0f) portalCooldown -= dt
    if (swingCooldown > 0f) swingCooldown -= dt
    if (dashCooldown > 0f) dashCooldown -= dt
    if (heroHurtTimer > 0f) heroHurtTimer -= dt
    if (screenShake > 0f) screenShake = max(0f, screenShake - dt * 26f)

    // 1. Hero Movement
    val heroSpeed = 195f
    var moveVx = 0f
    var moveVy = 0f

    if (dashTimer > 0f) {
      dashTimer -= dt
      moveVx = dashDirX * 480f
      moveVy = dashDirY * 480f
    } else {
      val mag = hypot(joyX, joyY)
      if (mag > 0.08f) {
        val normX = joyX / max(1f, mag)
        val normY = joyY / max(1f, mag)
        val clampedMag = min(1f, mag)
        moveVx = normX * heroSpeed * clampedMag
        moveVy = normY * heroSpeed * clampedMag
        facingAngle = atan2(normY, normX)
        walkCycle += dt * 11f * clampedMag
      }
    }

    // Add knockback decay
    moveVx += heroKnockVx
    moveVy += heroKnockVy
    heroKnockVx *= 0.82f
    heroKnockVy *= 0.82f

    moveHeroWithCollision(moveVx * dt, moveVy * dt)

    // 2. Sword Swing Hitbox
    if (swingTimer > 0f) {
      swingTimer -= dt
      val swordReach = when (stats.equippedWeapon?.itemRarity()?.tierOrder ?: 1) {
        5 -> 86f
        4 -> 80f
        3 -> 74f
        else -> 68f
      }
      enemies.forEach { enemy ->
        if (!enemy.alive || enemy.id in hitEnemiesThisSwing) return@forEach
        val dx = enemy.x - heroX
        val dy = enemy.y - heroY
        val dist = hypot(dx, dy)
        if (dist <= swordReach + enemy.archetype.radius) {
          val angleToEnemy = atan2(dy, dx)
          val angleDiff = normalizeAngle(angleToEnemy - facingAngle)
          if (kotlin.math.abs(angleDiff) <= 1.38f || dist < 34f) {
            hitEnemiesThisSwing += enemy.id
            hitLanded = true
            val isCrit = Random.nextInt(100) < stats.critChancePct
            val variance = Random.nextInt(-2, 4)
            val rawDmg = max(4, stats.totalAttack + variance)
            val dmg = if (isCrit) (rawDmg * 1.75f).toInt() else rawDmg

            enemy.hp -= dmg
            enemy.hitFlashTimer = 0.14f
            enemy.isAggro = true
            enemy.repathTimer = 0f
            val pushAngle = if (dist > 0.1f) angleToEnemy else facingAngle
            val pushForce = if (enemy.archetype.isBoss) 140f else 240f
            enemy.knockVx = cos(pushAngle) * pushForce
            enemy.knockVy = sin(pushAngle) * pushForce

            screenShake = if (isCrit) 6f else 3.5f
            val sparkColor = if (isCrit) 0xFFFBBF24 else 0xFFFFFFFF
            spawnBurst(enemy.x, enemy.y, sparkColor, if (isCrit) 12 else 7, 130f)
            addFloatingText(
              text = if (isCrit) "CRÍTICO -$dmg!" else "-$dmg",
              x = enemy.x,
              y = enemy.y - enemy.archetype.radius - 8f,
              colorHex = if (isCrit) 0xFFFBBF24 else 0xFFFFFFFF,
              isBig = isCrit,
            )

            if (enemy.hp <= 0) {
              enemy.alive = false
              enemy.isAggro = false
              enemy.path = emptyList()
              enemy.pathIndex = 0
              enemy.respawnTimer = enemy.respawnDuration
              monstersKilled++
              xpGained += enemy.archetype.xpReward
              spawnBurst(enemy.x, enemy.y, enemy.archetype.primaryHex, 16, 160f)
              addFloatingText(
                text = "+${enemy.archetype.xpReward} XP",
                x = enemy.x,
                y = enemy.y - 24f,
                colorHex = 0xFF38BDF8,
              )
              spawnLootDropsForMonster(enemy, stats.coinBonusPct)
              if (enemy.archetype.isBoss) {
                val bonusId = "boss_chest_${zoneData.zoneId.key}_${++dropCounter}"
                chests += ChestEntity(
                  id = bonusId,
                  x = enemy.x,
                  y = enemy.y,
                  tier = if (zoneData.zoneId == ZoneId.KEEP) ChestTier.LEGENDARY else ChestTier.GOLD,
                  opened = false,
                  isBossBonus = true,
                )
                addFloatingText("BAÚ DO CHEFE!", enemy.x, enemy.y - 44f, 0xFFF59E0B, isBig = true)
              }
            }
          }
        }
      }
    }

    // 3. Enemy AI & Hero Damage
    enemies.forEach { enemy ->
      if (!enemy.alive) {
        enemy.respawnTimer -= dt
        if (enemy.respawnTimer <= 0f) {
          enemy.alive = true
          enemy.hp = enemy.maxHp
          enemy.x = enemy.homeX
          enemy.y = enemy.homeY
          enemy.knockVx = 0f
          enemy.knockVy = 0f
          enemy.isAggro = false
          enemy.path = emptyList()
          enemy.pathIndex = 0
          enemy.repathTimer = 0f
        }
        return@forEach
      }

      if (enemy.hitFlashTimer > 0f) enemy.hitFlashTimer -= dt
      if (enemy.attackCooldown > 0f) enemy.attackCooldown -= dt
      if (enemy.repathTimer > 0f) enemy.repathTimer -= dt

      val dx = heroX - enemy.x
      val dy = heroY - enemy.y
      val distToHero = hypot(dx, dy)

      var vx = enemy.knockVx
      var vy = enemy.knockVy
      enemy.knockVx *= 0.82f
      enemy.knockVy *= 0.82f

      val engageRadius = enemy.archetype.agroRadius
      val disengageRadius = enemy.archetype.agroRadius * 1.4f
      val wasAggro = enemy.isAggro
      val inRange = if (wasAggro) distToHero <= disengageRadius else distToHero <= engageRadius

      val ignoresTerrain = enemy.archetype == MonsterArchetype.BAT
      val clearance = min(enemy.archetype.radius, 22f)

      if (inRange) {
        if (ignoresTerrain || GridPathfinder.hasLineOfSight(zoneData, enemy.x, enemy.y, heroX, heroY, clearance)) {
          enemy.isAggro = true
          enemy.path = emptyList()
          enemy.pathIndex = 0
          if (distToHero > 6f) {
            vx += (dx / distToHero) * enemy.archetype.speed
            vy += (dy / distToHero) * enemy.archetype.speed
          }
        } else {
          val startTile = GridPathfinder.worldToTile(enemy.x, enemy.y, zoneData)
          val targetTile = GridPathfinder.worldToTile(heroX, heroY, zoneData)
          if (!wasAggro || enemy.repathTimer <= 0f || enemy.pathIndex >= enemy.path.size) {
            enemy.path = GridPathfinder.findPath(
              zone = zoneData,
              startCol = startTile.col,
              startRow = startTile.row,
              targetCol = targetTile.col,
              targetRow = targetTile.row,
              ignoreSolid = false,
            )
            enemy.pathIndex = 0
            enemy.repathTimer = 0.25f
          }

          if (enemy.path.isNotEmpty() || startTile == targetTile) {
            enemy.isAggro = true
            val (steerX, steerY) = nextWaypointTarget(enemy, heroX, heroY, clearance)
            val sdx = steerX - enemy.x
            val sdy = steerY - enemy.y
            val sDist = hypot(sdx, sdy)
            if (sDist > 2f) {
              vx += (sdx / sDist) * enemy.archetype.speed
              vy += (sdy / sDist) * enemy.archetype.speed
            }
          } else {
            enemy.isAggro = false
          }
        }
      } else {
        enemy.isAggro = false
        if (wasAggro) {
          enemy.path = emptyList()
          enemy.pathIndex = 0
        }
      }

      if (!enemy.isAggro) {
        val toHomeX = enemy.homeX - enemy.x
        val toHomeY = enemy.homeY - enemy.y
        val toHomeDist = hypot(toHomeX, toHomeY)

        if (toHomeDist > 110f && !ignoresTerrain &&
          !GridPathfinder.hasLineOfSight(zoneData, enemy.x, enemy.y, enemy.homeX, enemy.homeY, clearance)
        ) {
          if (enemy.repathTimer <= 0f || enemy.pathIndex >= enemy.path.size) {
            val startTile = GridPathfinder.worldToTile(enemy.x, enemy.y, zoneData)
            val homeTile = GridPathfinder.worldToTile(enemy.homeX, enemy.homeY, zoneData)
            enemy.path = GridPathfinder.findPath(
              zone = zoneData,
              startCol = startTile.col,
              startRow = startTile.row,
              targetCol = homeTile.col,
              targetRow = homeTile.row,
              ignoreSolid = false,
            )
            enemy.pathIndex = 0
            enemy.repathTimer = 0.45f
          }
          val (steerX, steerY) = nextWaypointTarget(enemy, enemy.homeX, enemy.homeY, clearance)
          val sdx = steerX - enemy.x
          val sdy = steerY - enemy.y
          val sDist = hypot(sdx, sdy)
          if (sDist > 2f) {
            vx += (sdx / sDist) * (enemy.archetype.speed * 0.6f)
            vy += (sdy / sDist) * (enemy.archetype.speed * 0.6f)
          }
        } else {
          // Gentle patrol around home
          enemy.patrolTimer -= dt
          if (enemy.patrolTimer <= 0f) {
            enemy.patrolTimer = 1.6f + Random.nextFloat() * 1.5f
            enemy.patrolAngle = if (toHomeDist > 85f) {
              atan2(toHomeY, toHomeX)
            } else {
              Random.nextFloat() * (2f * PI.toFloat())
            }
          }
          vx += cos(enemy.patrolAngle) * (enemy.archetype.speed * 0.42f)
          vy += sin(enemy.patrolAngle) * (enemy.archetype.speed * 0.42f)
        }
      }

      moveEnemyWithCollision(enemy, vx * dt, vy * dt)

      // Contact attack against Hero
      val hitDist = heroRadius + enemy.archetype.radius + 4f
      if (distToHero <= hitDist && enemy.attackCooldown <= 0f && heroHurtTimer <= 0f && dashTimer <= 0f && currentHp > 0) {
        enemy.attackCooldown = 0.95f
        heroHurtTimer = 0.65f
        val mitigated = max(2, enemy.archetype.attack - (stats.totalDefense / 2))
        hpDelta -= mitigated
        screenShake = 7f
        val pushAngle = atan2(dy, dx)
        heroKnockVx = cos(pushAngle) * 230f
        heroKnockVy = sin(pushAngle) * 230f
        spawnBurst(heroX, heroY, 0xFFEF4444, 10, 110f)
        addFloatingText("-$mitigated", heroX, heroY - 26f, 0xFFEF4444, isBig = true)
      }
    }

    // 4. Drops (Coins & Hearts) with Magnetic Pickup
    val magnetRadius = 78f + (stats.coinBonusPct * 0.4f)
    val dropIter = drops.iterator()
    while (dropIter.hasNext()) {
      val drop = dropIter.next()
      drop.age += dt
      drop.x += drop.vx * dt
      drop.y += drop.vy * dt
      drop.vx *= 0.88f
      drop.vy *= 0.88f

      val dx = heroX - drop.x
      val dy = heroY - drop.y
      val dist = hypot(dx, dy)
      if (dist < magnetRadius && dist > 1f) {
        val pull = 280f + (magnetRadius - dist) * 2.5f
        drop.x += (dx / dist) * pull * dt
        drop.y += (dy / dist) * pull * dt
      }
      if (dist <= heroRadius + 14f) {
        when (drop.kind) {
          DropKind.COIN -> {
            coinsCollected += drop.amount
            spawnBurst(drop.x, drop.y, 0xFFFBBF24, 5, 70f)
            addFloatingText("+${drop.amount} 🪙", heroX, heroY - 22f, 0xFFFBBF24)
          }
          DropKind.HEART -> {
            hpDelta += drop.amount
            spawnBurst(drop.x, drop.y, 0xFFEF4444, 6, 70f)
            addFloatingText("+${drop.amount} HP", heroX, heroY - 22f, 0xFF22C55E)
          }
        }
        dropIter.remove()
      }
    }

    // 5. Floating Texts & Particles
    val ftIter = floatingTexts.iterator()
    while (ftIter.hasNext()) {
      val ft = ftIter.next()
      ft.life -= dt
      ft.y -= 28f * dt
      if (ft.life <= 0f) ftIter.remove()
    }

    val pIter = particles.iterator()
    while (pIter.hasNext()) {
      val p = pIter.next()
      p.life -= dt
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.vx *= 0.91f
      p.vy *= 0.91f
      if (p.life <= 0f) pIter.remove()
    }

    // 6. Proximities (Chests, Portals, Merchant)
    updateProximities()
    val portalTriggered = if (portalCooldown <= 0f) nearPortal else null

    return GameEvents(
      coinsCollected = coinsCollected,
      xpGained = xpGained,
      monstersKilled = monstersKilled,
      hpDelta = hpDelta,
      portalTriggered = portalTriggered,
      hitLanded = hitLanded,
    )
  }

  private fun spawnLootDropsForMonster(enemy: EnemyEntity, coinBonusPct: Int) {
    val baseCoins = Random.nextInt(enemy.archetype.coinMin, enemy.archetype.coinMax + 1)
    val bonus = (baseCoins * coinBonusPct) / 100
    val totalCoins = baseCoins + bonus
    val coinPieces = if (enemy.archetype.isBoss) 7 else if (totalCoins > 25) 4 else 3
    val perPiece = max(1, totalCoins / coinPieces)
    var remaining = totalCoins
    repeat(coinPieces) { idx ->
      val amt = if (idx == coinPieces - 1) max(1, remaining) else perPiece
      remaining -= amt
      val ang = Random.nextFloat() * (2f * PI.toFloat())
      val spd = 80f + Random.nextFloat() * 90f
      drops += WorldDrop(
        id = "coin_${++dropCounter}",
        kind = DropKind.COIN,
        x = enemy.x,
        y = enemy.y,
        vx = cos(ang) * spd,
        vy = sin(ang) * spd,
        amount = amt,
      )
    }

    // Chance to drop a Heart pickup
    if (enemy.archetype.isBoss || Random.nextFloat() < 0.28f) {
      val ang = Random.nextFloat() * (2f * PI.toFloat())
      drops += WorldDrop(
        id = "heart_${++dropCounter}",
        kind = DropKind.HEART,
        x = enemy.x,
        y = enemy.y,
        vx = cos(ang) * 75f,
        vy = sin(ang) * 75f,
        amount = if (enemy.archetype.isBoss) 45 else 18,
      )
    }
  }

  fun spawnBurst(x: Float, y: Float, colorHex: Long, count: Int, speed: Float) {
    repeat(count) {
      val ang = Random.nextFloat() * (2f * PI.toFloat())
      val spd = speed * (0.4f + Random.nextFloat() * 0.7f)
      val life = 0.25f + Random.nextFloat() * 0.25f
      particles += ParticleSpark(
        x = x,
        y = y,
        vx = cos(ang) * spd,
        vy = sin(ang) * spd,
        colorHex = colorHex,
        life = life,
        maxLife = life,
        radius = 2.5f + Random.nextFloat() * 3f,
      )
    }
  }

  private fun updateProximities() {
    nearChest = chests
      .filter { !it.opened && hypot(it.x - heroX, it.y - heroY) <= 68f }
      .minByOrNull { hypot(it.x - heroX, it.y - heroY) }

    val ts = RpgCatalog.TILE_SIZE
    val tileC = (heroX / ts).toInt().coerceIn(0, zoneData.cols - 1)
    val tileR = (heroY / ts).toInt().coerceIn(0, zoneData.rows - 1)
    val tile = zoneData.tiles[tileR][tileC]
    nearPortal = if (tile == TileType.PORTAL_NEXT || tile == TileType.PORTAL_PREV) tile else null

    nearMerchant = zoneData.merchantTile?.let { (mc, mr) ->
      val mx = (mc + 0.5f) * ts
      val my = (mr + 0.5f) * ts
      hypot(mx - heroX, my - heroY) <= 72f
    } ?: false
  }

  private fun nextWaypointTarget(
    enemy: EnemyEntity,
    finalX: Float,
    finalY: Float,
    clearanceRadius: Float,
  ): Pair<Float, Float> {
    while (enemy.pathIndex < enemy.path.size) {
      val wp = enemy.path[enemy.pathIndex]
      val (wx, wy) = GridPathfinder.tileCenter(wp.col, wp.row)
      if (hypot(wx - enemy.x, wy - enemy.y) <= 14f) {
        enemy.pathIndex++
        continue
      }
      // String-pulling lookahead: skip to the next waypoint if we have clear line of sight to it
      if (enemy.pathIndex + 1 < enemy.path.size) {
        val nextWp = enemy.path[enemy.pathIndex + 1]
        val (nwx, nwy) = GridPathfinder.tileCenter(nextWp.col, nextWp.row)
        if (GridPathfinder.hasLineOfSight(zoneData, enemy.x, enemy.y, nwx, nwy, clearanceRadius)) {
          enemy.pathIndex++
          continue
        }
      }
      return wx to wy
    }
    return finalX to finalY
  }

  private fun moveHeroWithCollision(dx: Float, dy: Float) {
    val nextX = heroX + dx
    if (!collidesWithWorld(nextX, heroY, heroRadius)) {
      heroX = nextX
    }
    val nextY = heroY + dy
    if (!collidesWithWorld(heroX, nextY, heroRadius)) {
      heroY = nextY
    }
  }

  private fun moveEnemyWithCollision(enemy: EnemyEntity, dx: Float, dy: Float) {
    val r = min(enemy.archetype.radius, 22f)
    val ignoresTerrain = enemy.archetype == MonsterArchetype.BAT
    val nextX = enemy.x + dx
    if (ignoresTerrain) {
      val ts = RpgCatalog.TILE_SIZE
      enemy.x = nextX.coerceIn(ts * 1.4f, (zoneData.cols - 1.4f) * ts)
    } else if (!collidesWithWorld(nextX, enemy.y, r)) {
      enemy.x = nextX
    }
    val nextY = enemy.y + dy
    if (ignoresTerrain) {
      val ts = RpgCatalog.TILE_SIZE
      enemy.y = nextY.coerceIn(ts * 1.4f, (zoneData.rows - 1.4f) * ts)
    } else if (!collidesWithWorld(enemy.x, nextY, r)) {
      enemy.y = nextY
    }
  }

  private fun collidesWithWorld(cx: Float, cy: Float, radius: Float): Boolean {
    return GridPathfinder.collidesWithWorld(zoneData, cx, cy, radius)
  }

  private fun normalizeAngle(a: Float): Float {
    var angle = a
    while (angle > PI) angle -= (2f * PI.toFloat())
    while (angle < -PI) angle += (2f * PI.toFloat())
    return angle
  }
}
