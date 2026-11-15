package com.example.ui.canvas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.example.data.model.ComputedHeroStats
import com.example.game.ChestEntity
import com.example.game.ChestTier
import com.example.game.DropKind
import com.example.game.EnemyEntity
import com.example.game.MonsterArchetype
import com.example.game.RpgCatalog
import com.example.game.RpgGameEngine
import com.example.game.TileType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun RpgWorldCanvas(
  engine: RpgGameEngine,
  stats: ComputedHeroStats,
  frameTick: Long,
  modifier: Modifier = Modifier,
) {
  val textPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }
  val shadowPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      color = android.graphics.Color.BLACK
    }
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    // Read frameTick so Compose invalidates this Canvas at 60 FPS
    @Suppress("UNUSED_VARIABLE")
    val tick = frameTick

    val ts = RpgCatalog.TILE_SIZE
    val zone = engine.zoneData
    val worldW = zone.cols * ts
    val worldH = zone.rows * ts

    // Zoom scale adaptive to screen size so tiles look crisp on both phones & tablets
    val zoom = (minOf(size.width, size.height) / 480f).coerceIn(1.05f, 1.85f)
    val viewW = size.width / zoom
    val viewH = size.height / zoom

    val shakeX = if (engine.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * engine.screenShake else 0f
    val shakeY = if (engine.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * engine.screenShake else 0f

    val camX = if (worldW <= viewW) {
      -(viewW - worldW) / 2f
    } else {
      (engine.heroX - viewW / 2f).coerceIn(0f, worldW - viewW)
    } + shakeX

    val camY = if (worldH <= viewH) {
      -(viewH - worldH) / 2f
    } else {
      (engine.heroY - viewH / 2f).coerceIn(0f, worldH - viewH)
    } + shakeY

    // Background void outside map
    drawRect(color = Color(0xFF070E0A))

    withTransform({
      scale(zoom, zoom, pivot = Offset.Zero)
      translate(-camX, -camY)
    }) {
      val minCol = max(0, (camX / ts).toInt() - 1)
      val maxCol = minOf(zone.cols - 1, ((camX + viewW) / ts).toInt() + 1)
      val minRow = max(0, (camY / ts).toInt() - 1)
      val maxRow = minOf(zone.rows - 1, ((camY + viewH) / ts).toInt() + 1)

      // 1. Tiles
      for (r in minRow..maxRow) {
        for (c in minCol..maxCol) {
          drawTile(
            tile = zone.tiles[r][c],
            col = c,
            row = r,
            ts = ts,
            time = engine.worldTime,
          )
        }
      }

      // 2. Merchant NPC in Clareira Verde
      zone.merchantTile?.let { (mc, mr) ->
        drawMerchantNpc(
          x = (mc + 0.5f) * ts,
          y = (mr + 0.5f) * ts,
          nearMerchant = engine.nearMerchant,
          time = engine.worldTime,
          textPaint = textPaint,
          shadowPaint = shadowPaint,
        )
      }

      // 3. Chests
      engine.chests.forEach { chest ->
        drawChest(
          chest = chest,
          isNear = engine.nearChest?.id == chest.id,
          time = engine.worldTime,
          textPaint = textPaint,
          shadowPaint = shadowPaint,
        )
      }

      // 4. Drops (Coins & Hearts)
      engine.drops.forEach { drop ->
        when (drop.kind) {
          DropKind.COIN -> drawCoin(drop.x, drop.y, engine.worldTime + drop.age * 4f)
          DropKind.HEART -> drawHeartDrop(drop.x, drop.y, engine.worldTime + drop.age * 3f)
        }
      }

      // 5. Monsters
      engine.enemies.filter { it.alive }.forEach { enemy ->
        drawEnemy(enemy, engine.worldTime, textPaint, shadowPaint)
      }

      // 6. Hero + Equipped Sword + Slash Arc
      drawHero(engine, stats)

      // 7. Particles
      engine.particles.forEach { p ->
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        drawCircle(
          color = Color(p.colorHex).copy(alpha = alpha),
          radius = p.radius * alpha,
          center = Offset(p.x, p.y),
        )
      }

      // 8. Floating Combat & Loot Text
      engine.floatingTexts.forEach { ft ->
        val alpha = ((ft.life / ft.maxLife) * 1.4f).coerceIn(0f, 1f)
        val fontSize = if (ft.isBig) 16f else 13f
        shadowPaint.textSize = fontSize
        shadowPaint.alpha = (alpha * 220).toInt()
        textPaint.textSize = fontSize
        textPaint.color = ft.colorHex.toInt()
        textPaint.alpha = (alpha * 255).toInt()
        drawContext.canvas.nativeCanvas.drawText(ft.text, ft.x + 1.2f, ft.y + 1.2f, shadowPaint)
        drawContext.canvas.nativeCanvas.drawText(ft.text, ft.x, ft.y, textPaint)
      }
    }
  }
}

private fun DrawScope.drawTile(
  tile: TileType,
  col: Int,
  row: Int,
  ts: Float,
  time: Float,
) {
  val x = col * ts
  val y = row * ts
  val alt = (col + row) % 2 == 0

  when (tile) {
    TileType.GRASS -> {
      drawRect(
        color = if (alt) Color(0xFF2E6F40) else Color(0xFF286339),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      if ((col * 7 + row * 13) % 5 == 0) {
        drawLine(
          color = Color(0xFF4ADE80).copy(alpha = 0.35f),
          start = Offset(x + ts * 0.3f, y + ts * 0.7f),
          end = Offset(x + ts * 0.35f, y + ts * 0.5f),
          strokeWidth = 2f,
        )
        drawLine(
          color = Color(0xFF4ADE80).copy(alpha = 0.35f),
          start = Offset(x + ts * 0.42f, y + ts * 0.72f),
          end = Offset(x + ts * 0.46f, y + ts * 0.54f),
          strokeWidth = 2f,
        )
      }
    }
    TileType.FLOWERS -> {
      drawRect(
        color = if (alt) Color(0xFF2E6F40) else Color(0xFF286339),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      val sway = sin(time * 2.5f + col) * 1.5f
      drawCircle(Color(0xFFFDE047), radius = 4f, center = Offset(x + ts * 0.32f + sway, y + ts * 0.35f))
      drawCircle(Color(0xFFF472B6), radius = 3.5f, center = Offset(x + ts * 0.68f - sway, y + ts * 0.65f))
      drawCircle(Color(0xFF38BDF8), radius = 3f, center = Offset(x + ts * 0.62f + sway, y + ts * 0.28f))
    }
    TileType.PATH -> {
      drawRect(
        color = if (alt) Color(0xFF9C7A54) else Color(0xFF8F6E4A),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      drawCircle(Color(0xFF785A3A), radius = 2.5f, center = Offset(x + ts * 0.28f, y + ts * 0.34f))
      drawCircle(Color(0xFFB39169), radius = 2f, center = Offset(x + ts * 0.72f, y + ts * 0.68f))
    }
    TileType.WATER -> {
      drawRect(
        color = Color(0xFF0E7490),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      val wave = sin(time * 3f + row + col * 0.5f) * 4f
      drawLine(
        color = Color(0xFF67E8F9).copy(alpha = 0.45f),
        start = Offset(x + 8f + wave, y + ts * 0.35f),
        end = Offset(x + ts - 12f + wave, y + ts * 0.35f),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = Color(0xFF67E8F9).copy(alpha = 0.3f),
        start = Offset(x + 14f - wave, y + ts * 0.7f),
        end = Offset(x + ts - 8f - wave, y + ts * 0.7f),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
      )
    }
    TileType.BRIDGE -> {
      drawRect(Color(0xFF0E7490), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      drawRect(Color(0xFF78350F), Offset(x, y + 3f), Size(ts + 0.5f, ts - 6f))
      for (i in 0..3) {
        val px = x + i * (ts / 4f)
        drawLine(Color(0xFF451A03), Offset(px, y + 3f), Offset(px, y + ts - 3f), strokeWidth = 2f)
      }
      drawRect(Color(0xFFB45309), Offset(x, y + 2f), Size(ts + 0.5f, 4f))
      drawRect(Color(0xFFB45309), Offset(x, y + ts - 6f), Size(ts + 0.5f, 4f))
    }
    TileType.TREE -> {
      drawRect(Color(0xFF235330), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      // Shadow + trunk + layered foliage
      drawCircle(Color(0x55000000), radius = ts * 0.42f, center = Offset(x + ts * 0.5f, y + ts * 0.58f))
      drawRect(Color(0xFF5C3A21), Offset(x + ts * 0.41f, y + ts * 0.55f), Size(ts * 0.18f, ts * 0.35f))
      drawCircle(Color(0xFF14532D), radius = ts * 0.42f, center = Offset(x + ts * 0.5f, y + ts * 0.43f))
      drawCircle(Color(0xFF166534), radius = ts * 0.34f, center = Offset(x + ts * 0.48f, y + ts * 0.38f))
      drawCircle(Color(0xFF22C55E).copy(alpha = 0.4f), radius = ts * 0.2f, center = Offset(x + ts * 0.43f, y + ts * 0.32f))
    }
    TileType.RUIN_FLOOR -> {
      drawRect(
        color = if (alt) Color(0xFF2B3544) else Color(0xFF252E3B),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      drawRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(x + 2f, y + 2f),
        size = Size(ts - 4f, ts - 4f),
        style = Stroke(width = 1.5f),
      )
    }
    TileType.STONE_WALL, TileType.CRACKED_PILLAR -> {
      drawRect(Color(0xFF1E293B), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      drawRoundRect(
        color = Color(0xFF475569),
        topLeft = Offset(x + 3f, y + 3f),
        size = Size(ts - 6f, ts - 6f),
        cornerRadius = CornerRadius(6f, 6f),
      )
      drawRoundRect(
        color = Color(0xFF64748B),
        topLeft = Offset(x + 6f, y + 6f),
        size = Size(ts - 12f, ts - 14f),
        cornerRadius = CornerRadius(4f, 4f),
      )
    }
    TileType.TORCH -> {
      drawRect(Color(0xFF252E3B), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      val flicker = sin(time * 12f + col * 3f) * 2.5f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x66F59E0B), Color.Transparent),
          center = Offset(x + ts * 0.5f, y + ts * 0.45f),
          radius = ts * 0.9f,
        ),
        radius = ts * 0.9f,
        center = Offset(x + ts * 0.5f, y + ts * 0.45f),
      )
      drawCircle(Color(0xFF475569), radius = 10f, center = Offset(x + ts * 0.5f, y + ts * 0.55f))
      drawCircle(Color(0xFFF59E0B), radius = 7f + flicker * 0.35f, center = Offset(x + ts * 0.5f, y + ts * 0.46f))
      drawCircle(Color(0xFFFEF08A), radius = 3.5f, center = Offset(x + ts * 0.5f, y + ts * 0.44f))
    }
    TileType.BASALT -> {
      drawRect(
        color = if (alt) Color(0xFF291D24) else Color(0xFF21171D),
        topLeft = Offset(x, y),
        size = Size(ts + 0.5f, ts + 0.5f),
      )
      if ((col + row * 3) % 4 == 0) {
        drawLine(
          color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
          start = Offset(x + 8f, y + 10f),
          end = Offset(x + ts - 10f, y + ts - 12f),
          strokeWidth = 2f,
        )
      }
    }
    TileType.LAVA -> {
      drawRect(Color(0xFFDC2626), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      val pulse = (sin(time * 3.5f + row + col) + 1f) * 0.5f
      drawCircle(
        color = Color(0xFFF59E0B).copy(alpha = 0.55f + pulse * 0.35f),
        radius = 8f + pulse * 4f,
        center = Offset(x + ts * 0.5f, y + ts * 0.5f),
      )
      drawCircle(
        color = Color(0xFFFEF08A),
        radius = 3.5f,
        center = Offset(x + ts * 0.38f, y + ts * 0.4f),
      )
    }
    TileType.PORTAL_NEXT, TileType.PORTAL_PREV -> {
      drawRect(Color(0xFF1E293B), Offset(x, y), Size(ts + 0.5f, ts + 0.5f))
      val portalColor = if (tile == TileType.PORTAL_NEXT) Color(0xFF38BDF8) else Color(0xFFA855F7)
      val pulse = (sin(time * 4f) + 1f) * 0.5f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(portalColor, portalColor.copy(alpha = 0.25f), Color.Transparent),
          center = Offset(x + ts * 0.5f, y + ts * 0.5f),
          radius = ts * 0.55f,
        ),
        radius = ts * 0.55f,
        center = Offset(x + ts * 0.5f, y + ts * 0.5f),
      )
      drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = 10f + pulse * 5f,
        center = Offset(x + ts * 0.5f, y + ts * 0.5f),
        style = Stroke(width = 2.5f),
      )
    }
  }
}

private fun DrawScope.drawMerchantNpc(
  x: Float,
  y: Float,
  nearMerchant: Boolean,
  time: Float,
  textPaint: Paint,
  shadowPaint: Paint,
) {
  // Canopy tent
  drawRoundRect(
    color = Color(0xFF92400E),
    topLeft = Offset(x - 28f, y - 34f),
    size = Size(56f, 24f),
    cornerRadius = CornerRadius(6f, 6f),
  )
  drawRoundRect(
    color = Color(0xFFF59E0B),
    topLeft = Offset(x - 28f, y - 34f),
    size = Size(56f, 10f),
    cornerRadius = CornerRadius(6f, 6f),
  )
  // Shadow + Blacksmith body
  drawCircle(Color(0x55000000), radius = 15f, center = Offset(x, y + 6f))
  drawCircle(Color(0xFF475569), radius = 14f, center = Offset(x, y))
  drawCircle(Color(0xFFFDE68A), radius = 9f, center = Offset(x, y - 8f))
  drawRect(Color(0xFFB45309), Offset(x - 10f, y - 15f), Size(20f, 5f))

  val bob = sin(time * 3f) * 2f
  val label = if (nearMerchant) "ABRIR LOJA" else "FERREIRO"
  shadowPaint.textSize = 11f
  shadowPaint.alpha = 230
  textPaint.textSize = 11f
  textPaint.color = if (nearMerchant) 0xFFFBBF24.toInt() else 0xFFE2E8F0.toInt()
  textPaint.alpha = 255
  drawContext.canvas.nativeCanvas.drawText(label, x + 1f, y - 40f + bob + 1f, shadowPaint)
  drawContext.canvas.nativeCanvas.drawText(label, x, y - 40f + bob, textPaint)
}

private fun DrawScope.drawChest(
  chest: ChestEntity,
  isNear: Boolean,
  time: Float,
  textPaint: Paint,
  shadowPaint: Paint,
) {
  val (bodyColor, trimColor, glowColor) = when (chest.tier) {
    ChestTier.WOOD -> Triple(Color(0xFF92400E), Color(0xFFD97706), Color(0xFFFBBF24))
    ChestTier.IRON -> Triple(Color(0xFF475569), Color(0xFF38BDF8), Color(0xFF38BDF8))
    ChestTier.GOLD -> Triple(Color(0xFFB45309), Color(0xFFFBBF24), Color(0xFFFDE047))
    ChestTier.LEGENDARY -> Triple(Color(0xFF581C87), Color(0xFFFBBF24), Color(0xFFA855F7))
  }

  if (!chest.opened) {
    val pulse = (sin(time * 4f) + 1f) * 0.5f
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(glowColor.copy(alpha = 0.35f + pulse * 0.25f), Color.Transparent),
        center = Offset(chest.x, chest.y),
        radius = 30f + pulse * 8f,
      ),
      radius = 30f + pulse * 8f,
      center = Offset(chest.x, chest.y),
    )
  }

  // Ground shadow
  drawOval(
    color = Color(0x55000000),
    topLeft = Offset(chest.x - 18f, chest.y + 8f),
    size = Size(36f, 12f),
  )

  // Chest base
  drawRoundRect(
    color = bodyColor,
    topLeft = Offset(chest.x - 16f, chest.y - 10f),
    size = Size(32f, 22f),
    cornerRadius = CornerRadius(4f, 4f),
  )
  drawRoundRect(
    color = trimColor,
    topLeft = Offset(chest.x - 16f, chest.y - 10f),
    size = Size(32f, 22f),
    cornerRadius = CornerRadius(4f, 4f),
    style = Stroke(width = 2.5f),
  )

  if (chest.opened) {
    // Dark open interior & tilted lid
    drawRect(
      color = Color(0xFF1E1B18),
      topLeft = Offset(chest.x - 13f, chest.y - 7f),
      size = Size(26f, 9f),
    )
    drawRoundRect(
      color = trimColor.copy(alpha = 0.8f),
      topLeft = Offset(chest.x - 16f, chest.y - 18f),
      size = Size(32f, 8f),
      cornerRadius = CornerRadius(3f, 3f),
    )
  } else {
    // Lid seam & clasp
    drawLine(
      color = trimColor,
      start = Offset(chest.x - 16f, chest.y - 1f),
      end = Offset(chest.x + 16f, chest.y - 1f),
      strokeWidth = 2.5f,
    )
    drawRect(
      color = Color(0xFFFEF08A),
      topLeft = Offset(chest.x - 3.5f, chest.y - 4f),
      size = Size(7f, 7f),
    )
    if (isNear) {
      val bob = sin(time * 7f) * 3f
      shadowPaint.textSize = 12f
      shadowPaint.alpha = 240
      textPaint.textSize = 12f
      textPaint.color = 0xFFFDE047.toInt()
      textPaint.alpha = 255
      drawContext.canvas.nativeCanvas.drawText("ABRIR!", chest.x + 1f, chest.y - 22f + bob + 1f, shadowPaint)
      drawContext.canvas.nativeCanvas.drawText("ABRIR!", chest.x, chest.y - 22f + bob, textPaint)
    }
  }
}

private fun DrawScope.drawCoin(x: Float, y: Float, phase: Float) {
  val spinWidth = max(3f, abs(cos(phase)) * 8.5f)
  drawOval(
    color = Color(0x44000000),
    topLeft = Offset(x - 6f, y + 7f),
    size = Size(12f, 5f),
  )
  drawOval(
    color = Color(0xFFF59E0B),
    topLeft = Offset(x - spinWidth, y - 8f),
    size = Size(spinWidth * 2f, 16f),
  )
  drawOval(
    color = Color(0xFFFEF08A),
    topLeft = Offset(x - spinWidth * 0.55f, y - 5f),
    size = Size(spinWidth * 1.1f, 10f),
  )
}

private fun DrawScope.drawHeartDrop(x: Float, y: Float, phase: Float) {
  val s = 1f + sin(phase) * 0.12f
  drawCircle(Color(0x44EF4444), radius = 12f * s, center = Offset(x, y))
  val path = Path().apply {
    moveTo(x, y + 7f * s)
    cubicTo(x - 11f * s, y + 1f * s, x - 11f * s, y - 8f * s, x, y - 3f * s)
    cubicTo(x + 11f * s, y - 8f * s, x + 11f * s, y + 1f * s, x, y + 7f * s)
    close()
  }
  drawPath(path, color = Color(0xFFEF4444))
  drawCircle(Color(0xFFFCA5A5), radius = 2.2f, center = Offset(x - 3f, y - 3f))
}

private fun DrawScope.drawEnemy(
  enemy: EnemyEntity,
  time: Float,
  textPaint: Paint,
  shadowPaint: Paint,
) {
  val r = enemy.archetype.radius
  val flash = enemy.hitFlashTimer > 0f
  val primary = if (flash) Color.White else Color(enemy.archetype.primaryHex)
  val accent = if (flash) Color.White else Color(enemy.archetype.accentHex)

  // Shadow
  drawOval(
    color = Color(0x55000000),
    topLeft = Offset(enemy.x - r * 0.9f, enemy.y + r * 0.55f),
    size = Size(r * 1.8f, r * 0.65f),
  )

  if (enemy.isAggro) {
    val aggroPulse = (sin(time * 7f) + 1f) * 0.5f
    drawCircle(
      color = Color(0xFFEF4444).copy(alpha = 0.25f + aggroPulse * 0.25f),
      radius = r * (1.22f + aggroPulse * 0.12f),
      center = Offset(enemy.x, enemy.y),
      style = Stroke(width = 2f),
    )
  }

  when (enemy.archetype) {
    MonsterArchetype.SLIME -> {
      val squish = sin(time * 5f + enemy.x) * 2.5f
      drawOval(
        color = primary,
        topLeft = Offset(enemy.x - r - squish * 0.5f, enemy.y - r * 0.8f + squish),
        size = Size((r * 2f) + squish, (r * 1.7f) - squish),
      )
      drawCircle(accent, radius = r * 0.3f, center = Offset(enemy.x - r * 0.35f, enemy.y - r * 0.3f))
      drawCircle(Color(0xFF0F172A), radius = 2.6f, center = Offset(enemy.x - 5f, enemy.y - 2f))
      drawCircle(Color(0xFF0F172A), radius = 2.6f, center = Offset(enemy.x + 5f, enemy.y - 2f))
    }
    MonsterArchetype.BAT -> {
      val flap = sin(time * 14f + enemy.x) * 8f
      val wingPath = Path().apply {
        moveTo(enemy.x, enemy.y)
        lineTo(enemy.x - r * 1.4f, enemy.y - 6f + flap)
        lineTo(enemy.x - r * 0.7f, enemy.y + 6f)
        lineTo(enemy.x + r * 0.7f, enemy.y + 6f)
        lineTo(enemy.x + r * 1.4f, enemy.y - 6f + flap)
        close()
      }
      drawPath(wingPath, color = primary)
      drawCircle(primary, radius = r * 0.65f, center = Offset(enemy.x, enemy.y))
      drawCircle(accent, radius = 2.5f, center = Offset(enemy.x - 4f, enemy.y - 2f))
      drawCircle(accent, radius = 2.5f, center = Offset(enemy.x + 4f, enemy.y - 2f))
    }
    MonsterArchetype.SKELETON -> {
      drawCircle(primary, radius = r * 0.85f, center = Offset(enemy.x, enemy.y))
      drawCircle(Color(0xFF0F172A), radius = 3.2f, center = Offset(enemy.x - 5f, enemy.y - 2f))
      drawCircle(Color(0xFF0F172A), radius = 3.2f, center = Offset(enemy.x + 5f, enemy.y - 2f))
      drawCircle(accent, radius = 1.6f, center = Offset(enemy.x - 5f, enemy.y - 2f))
      drawCircle(accent, radius = 1.6f, center = Offset(enemy.x + 5f, enemy.y - 2f))
      // Bone blade
      drawLine(
        color = Color(0xFFCBD5E1),
        start = Offset(enemy.x + r * 0.6f, enemy.y + 4f),
        end = Offset(enemy.x + r * 1.3f, enemy.y - 10f),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round,
      )
    }
    else -> {
      // Goblin, Knight, and Bosses
      if (enemy.archetype.isBoss) {
        drawCircle(
          color = accent.copy(alpha = 0.28f),
          radius = r * 1.45f,
          center = Offset(enemy.x, enemy.y),
          style = Stroke(width = 3f),
        )
      }
      drawCircle(primary, radius = r, center = Offset(enemy.x, enemy.y))
      drawCircle(
        color = accent,
        radius = r,
        center = Offset(enemy.x, enemy.y),
        style = Stroke(width = if (enemy.archetype.isBoss) 3.5f else 2f),
      )
      // Eyes
      drawCircle(Color(0xFFFEF08A), radius = 3f, center = Offset(enemy.x - r * 0.3f, enemy.y - 3f))
      drawCircle(Color(0xFFFEF08A), radius = 3f, center = Offset(enemy.x + r * 0.3f, enemy.y - 3f))
      // Weapon
      drawLine(
        color = Color(0xFFE2E8F0),
        start = Offset(enemy.x + r * 0.7f, enemy.y + 4f),
        end = Offset(enemy.x + r * 1.35f, enemy.y - 12f),
        strokeWidth = if (enemy.archetype.isBoss) 5f else 3.5f,
        cap = StrokeCap.Round,
      )
      if (enemy.archetype.isBoss) {
        // Boss Crown
        drawRect(Color(0xFFFBBF24), Offset(enemy.x - 10f, enemy.y - r - 6f), Size(20f, 6f))
      }
    }
  }

  // Overhead HP Bar
  if (enemy.hp < enemy.maxHp || enemy.archetype.isBoss) {
    val barW = if (enemy.archetype.isBoss) 54f else 34f
    val barH = if (enemy.archetype.isBoss) 6f else 4.5f
    val barX = enemy.x - barW / 2f
    val barY = enemy.y - r - (if (enemy.archetype.isBoss) 16f else 10f)
    val frac = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
    drawRoundRect(Color(0xCC0F172A), Offset(barX - 1f, barY - 1f), Size(barW + 2f, barH + 2f), CornerRadius(3f, 3f))
    drawRoundRect(
      color = if (enemy.archetype.isBoss) Color(0xFFF59E0B) else Color(0xFFEF4444),
      topLeft = Offset(barX, barY),
      size = Size(barW * frac, barH),
      cornerRadius = CornerRadius(2f, 2f),
    )
    if (enemy.archetype.isBoss) {
      shadowPaint.textSize = 10.5f
      shadowPaint.alpha = 230
      textPaint.textSize = 10.5f
      textPaint.color = 0xFFFDE047.toInt()
      textPaint.alpha = 255
      drawContext.canvas.nativeCanvas.drawText(enemy.archetype.displayName, enemy.x + 1f, barY - 4f, shadowPaint)
      drawContext.canvas.nativeCanvas.drawText(enemy.archetype.displayName, enemy.x, barY - 5f, textPaint)
    }
  }

  // Aggro alert indicator when actively pursuing the hero
  if (enemy.isAggro && !enemy.archetype.isBoss) {
    val alertX = enemy.x + r * 0.9f
    val alertY = enemy.y - r - 4f
    shadowPaint.textSize = 12f
    shadowPaint.alpha = 230
    textPaint.textSize = 12f
    textPaint.color = 0xFFEF4444.toInt()
    textPaint.alpha = 255
    drawContext.canvas.nativeCanvas.drawText("!", alertX + 1f, alertY + 1f, shadowPaint)
    drawContext.canvas.nativeCanvas.drawText("!", alertX, alertY, textPaint)
  }
}

private fun DrawScope.drawHero(
  engine: RpgGameEngine,
  stats: ComputedHeroStats,
) {
  val hx = engine.heroX
  val hy = engine.heroY
  val r = engine.heroRadius
  val hurtBlink = engine.heroHurtTimer > 0f && ((engine.heroHurtTimer * 20f).toInt() % 2 == 0)
  val bob = sin(engine.walkCycle) * 1.8f

  val swordColor = runCatching {
    val hex = stats.equippedWeapon?.colorHex ?: "#F59E0B"
    Color(android.graphics.Color.parseColor(hex))
  }.getOrDefault(Color(0xFFF59E0B))

  val shieldColor = runCatching {
    val hex = stats.equippedArmor?.colorHex ?: "#1E293B"
    Color(android.graphics.Color.parseColor(hex))
  }.getOrDefault(Color(0xFF475569))

  // Shadow
  drawOval(
    color = Color(0x66000000),
    topLeft = Offset(hx - r * 0.9f, hy + r * 0.65f),
    size = Size(r * 1.8f, r * 0.65f),
  )

  // Dash trail
  if (engine.dashTimer > 0f) {
    drawCircle(
      color = Color(0x6638BDF8),
      radius = r * 1.25f,
      center = Offset(hx, hy),
    )
  }

  // Sword Swing Crescent Arc
  if (engine.swingTimer > 0f) {
    val t = 1f - (engine.swingTimer / engine.swingDuration).coerceIn(0f, 1f)
    val sweepAngle = engine.swingStartAngle + (engine.swingEndAngle - engine.swingStartAngle) * t
    val reach = when (stats.equippedWeapon?.itemRarity()?.tierOrder ?: 1) {
      5 -> 68f
      4 -> 62f
      3 -> 56f
      else -> 50f
    }

    // Arc trail
    val startDeg = Math.toDegrees(engine.swingStartAngle.toDouble()).toFloat()
    val sweepDeg = Math.toDegrees((sweepAngle - engine.swingStartAngle).toDouble()).toFloat()
    drawArc(
      color = swordColor.copy(alpha = 0.45f),
      startAngle = startDeg,
      sweepAngle = sweepDeg,
      useCenter = false,
      topLeft = Offset(hx - reach, hy - reach),
      size = Size(reach * 2f, reach * 2f),
      style = Stroke(width = 16f, cap = StrokeCap.Round),
    )
    drawArc(
      color = Color.White.copy(alpha = 0.75f),
      startAngle = startDeg,
      sweepAngle = sweepDeg,
      useCenter = false,
      topLeft = Offset(hx - reach + 4f, hy - reach + 4f),
      size = Size((reach - 4f) * 2f, (reach - 4f) * 2f),
      style = Stroke(width = 5f, cap = StrokeCap.Round),
    )

    // Active swinging blade
    val bladeEndX = hx + cos(sweepAngle) * (reach + 6f)
    val bladeEndY = hy + sin(sweepAngle) * (reach + 6f)
    drawLine(
      color = swordColor,
      start = Offset(hx + cos(sweepAngle) * 14f, hy + sin(sweepAngle) * 14f),
      end = Offset(bladeEndX, bladeEndY),
      strokeWidth = 7f,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = Color.White,
      start = Offset(hx + cos(sweepAngle) * 16f, hy + sin(sweepAngle) * 16f),
      end = Offset(bladeEndX, bladeEndY),
      strokeWidth = 2.5f,
      cap = StrokeCap.Round,
    )
  } else {
    // Idle held sword pointing slightly to side of facing
    val idleAng = engine.facingAngle + 0.55f
    val hiltX = hx + cos(idleAng) * 12f
    val hiltY = hy + sin(idleAng) * 12f + bob
    val tipX = hx + cos(idleAng - 0.25f) * 32f
    val tipY = hy + sin(idleAng - 0.25f) * 32f + bob
    drawLine(swordColor, Offset(hiltX, hiltY), Offset(tipX, tipY), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.7f), Offset(hiltX, hiltY), Offset(tipX, tipY), strokeWidth = 1.8f, cap = StrokeCap.Round)
  }

  // Hero Tunic & Head
  val tunicColor = if (hurtBlink) Color(0xFFEF4444) else Color(0xFF10B981)
  drawCircle(tunicColor, radius = r, center = Offset(hx, hy + bob))
  drawCircle(Color(0xFF064E3B), radius = r, center = Offset(hx, hy + bob), style = Stroke(width = 2.5f))

  // Off-hand Shield if equipped
  if (stats.equippedArmor != null) {
    val shieldAng = engine.facingAngle - 0.9f
    val sx = hx + cos(shieldAng) * (r * 0.85f)
    val sy = hy + sin(shieldAng) * (r * 0.85f) + bob
    drawCircle(shieldColor, radius = 7.5f, center = Offset(sx, sy))
    drawCircle(Color(0xFFFBBF24), radius = 7.5f, center = Offset(sx, sy), style = Stroke(width = 1.8f))
  }

  // Hero Hood/Hair + Eyes pointing toward facingAngle
  val fx = cos(engine.facingAngle)
  val fy = sin(engine.facingAngle)
  drawCircle(Color(0xFFFDE68A), radius = 8.5f, center = Offset(hx + fx * 3f, hy - 3f + bob + fy * 2f))
  drawCircle(Color(0xFF059669), radius = 7.5f, center = Offset(hx - fx * 3f, hy - 5f + bob - fy * 2f))
  // Eyes
  val perpX = -fy
  val perpY = fx
  drawCircle(
    Color(0xFF0F172A),
    radius = 2f,
    center = Offset(hx + fx * 6f + perpX * 3.2f, hy - 2f + fy * 4f + perpY * 3.2f + bob),
  )
  drawCircle(
    Color(0xFF0F172A),
    radius = 2f,
    center = Offset(hx + fx * 6f - perpX * 3.2f, hy - 2f + fy * 4f - perpY * 3.2f + bob),
  )
}
