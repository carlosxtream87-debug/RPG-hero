package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ArcaneAzure
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.HyruleEmerald
import com.example.ui.theme.TreasureGold
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun SimpleOnScreenControls(
  hasNearbyChest: Boolean,
  hasNearbyMerchant: Boolean,
  potionsCount: Int,
  dashReady: Boolean,
  onJoystickMove: (Float, Float) -> Unit,
  onPrimaryAction: () -> Unit,
  onUsePotion: () -> Unit,
  onDash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current

  Row(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Bottom,
  ) {
    // Left: Simple Virtual Analog Stick
    VirtualJoystick(
      onMove = onJoystickMove,
      modifier = Modifier.testTag("virtual_joystick"),
    )

    // Right: Simple RPG Action Cluster (Potion + Dash + Big Attack/Open Button)
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Potion quick button
        Box(contentAlignment = Alignment.TopEnd) {
          Surface(
            color = Color(0xCC122119),
            shape = CircleShape,
            modifier = Modifier
              .size(54.dp)
              .border(
                width = 1.5.dp,
                color = if (potionsCount > 0) CrimsonHeart else Color(0xFF334155),
                shape = CircleShape,
              )
              .clip(CircleShape)
              .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUsePotion()
              }
              .testTag("potion_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.LocalDrink,
                contentDescription = stringResource(R.string.cd_potion),
                tint = if (potionsCount > 0) CrimsonHeart else Color(0xFF64748B),
                modifier = Modifier.size(24.dp),
              )
            }
          }
          Surface(
            color = if (potionsCount > 0) CrimsonHeart else Color(0xFF334155),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = potionsCount.toString(),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        }

        // Dash quick button
        Surface(
          color = Color(0xCC122119),
          shape = CircleShape,
          modifier = Modifier
            .size(54.dp)
            .border(
              width = 1.5.dp,
              color = if (dashReady) ArcaneAzure else Color(0xFF334155),
              shape = CircleShape,
            )
            .clip(CircleShape)
            .clickable {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onDash()
            }
            .testTag("dash_button"),
        ) {
          Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Bolt,
              contentDescription = stringResource(R.string.cd_dash),
              tint = if (dashReady) ArcaneAzure else Color(0xFF64748B),
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = "ESQUIVA",
              style = MaterialTheme.typography.labelSmall,
              color = if (dashReady) Color.White else Color(0xFF64748B),
            )
          }
        }
      }

      // Primary Sword Attack / Open Chest / Talk to Merchant Button
      val primaryColor = when {
        hasNearbyChest -> TreasureGold
        hasNearbyMerchant -> ArcaneAzure
        else -> HyruleEmerald
      }
      val primaryIcon = when {
        hasNearbyChest -> Icons.Filled.Key
        hasNearbyMerchant -> Icons.Filled.Storefront
        else -> Icons.Filled.SportsKabaddi
      }
      val primaryLabel = when {
        hasNearbyChest -> "ABRIR"
        hasNearbyMerchant -> "LOJA"
        else -> "ESPADA"
      }

      Box(
        modifier = Modifier
          .size(82.dp)
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              colors = listOf(
                primaryColor,
                primaryColor.copy(alpha = 0.72f),
              )
            )
          )
          .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
          .clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPrimaryAction()
          }
          .testTag("attack_action_button"),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = primaryIcon,
            contentDescription = stringResource(R.string.cd_attack),
            tint = Color(0xFF04140C),
            modifier = Modifier.size(30.dp),
          )
          Text(
            text = primaryLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color(0xFF04140C),
          )
        }
      }
    }
  }
}

@Composable
private fun VirtualJoystick(
  onMove: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var puckOffset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier = modifier
      .size(134.dp)
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val center = Offset(size.width / 2f, size.height / 2f)
          val maxRadius = size.width * 0.36f

          fun updateFromPos(pos: Offset) {
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            val dist = hypot(dx, dy)
            val clamped = min(dist, maxRadius)
            val nx = if (dist > 0.001f) dx / dist else 0f
            val ny = if (dist > 0.001f) dy / dist else 0f
            puckOffset = Offset(nx * clamped, ny * clamped)
            val strength = (clamped / maxRadius).coerceIn(0f, 1f)
            onMove(nx * strength, ny * strength)
          }

          updateFromPos(down.position)
          do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
            if (change != null && change.pressed) {
              updateFromPos(change.position)
              change.consume()
            } else {
              break
            }
          } while (true)

          puckOffset = Offset.Zero
          onMove(0f, 0f)
        }
      },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val outerRadius = size.minDimension * 0.45f

      // Outer semi-transparent pad
      drawCircle(
        color = Color(0xAA0B1510),
        radius = outerRadius,
        center = center,
      )
      drawCircle(
        color = Color(0xFF4ADE80).copy(alpha = 0.45f),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 2.5.dp.toPx()),
      )

      // Subtle directional crosshairs
      val tick = outerRadius * 0.65f
      drawLine(Color.White.copy(alpha = 0.16f), Offset(center.x - tick, center.y), Offset(center.x + tick, center.y), 2f)
      drawLine(Color.White.copy(alpha = 0.16f), Offset(center.x, center.y - tick), Offset(center.x, center.y + tick), 2f)

      // Movable thumb puck
      val puckCenter = center + puckOffset
      drawCircle(
        color = Color(0xFF10B981).copy(alpha = 0.9f),
        radius = 23.dp.toPx(),
        center = puckCenter,
      )
      drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = 23.dp.toPx(),
        center = puckCenter,
        style = Stroke(width = 2.dp.toPx()),
      )
    }
  }
}
