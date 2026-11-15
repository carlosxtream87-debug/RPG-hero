package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ZoneId
import com.example.ui.theme.ArcaneAzure
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.HyruleEmerald
import com.example.ui.theme.TreasureGold
import com.example.ui.viewmodel.ActiveModal
import com.example.ui.viewmodel.RpgUiData

@Composable
fun RpgTopHud(
  data: RpgUiData,
  onOpenModal: (ActiveModal) -> Unit,
  modifier: Modifier = Modifier,
) {
  val save = data.save
  val stats = data.stats
  val zone = ZoneId.fromKey(save.currentZone)
  val hpFraction = (save.hp.toFloat() / stats.totalMaxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
  val xpNeeded = save.xpToNextLevel().coerceAtLeast(1)
  val xpFraction = (save.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Primary HUD strip
    Surface(
      color = Color(0xD90B1510),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF2B4A38), RoundedCornerShape(18.dp)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Level Crest
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A2E23))
            .border(2.dp, TreasureGold, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "NV",
              style = MaterialTheme.typography.labelSmall,
              color = TreasureGold,
            )
            Text(
              text = save.level.toString(),
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
            )
          }
        }

        // HP + XP Bars
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = CrimsonHeart,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${save.hp}/${stats.totalMaxHp}",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
              imageVector = if (data.hasPendingWrites) Icons.Filled.CloudSync else Icons.Filled.CloudDone,
              contentDescription = null,
              tint = if (data.hasPendingWrites) TreasureGold else HyruleEmerald,
              modifier = Modifier.size(14.dp),
            )
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(Color(0xFF1E293B)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(hpFraction)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CrimsonHeart),
            )
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(Color(0xFF1E293B)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(xpFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ArcaneAzure),
            )
          }
        }

        // Coins Counter
        Surface(
          color = Color(0xFF271B07),
          shape = RoundedCornerShape(999.dp),
          modifier = Modifier.border(1.dp, TreasureGold.copy(alpha = 0.7f), RoundedCornerShape(999.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.MonetizationOn,
              contentDescription = null,
              tint = TreasureGold,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = save.coins.toString(),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFFFEF08A),
            )
          }
        }
      }
    }

    // Secondary compact RPG toolbar: Zone Chip + Mochila + Loja + Conta
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Surface(
        color = Color(0xCC0B1510),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .border(1.dp, Color(0xFF2B4A38), RoundedCornerShape(14.dp))
          .clip(RoundedCornerShape(14.dp))
          .clickable { onOpenModal(ActiveModal.ZONES) }
          .testTag("zone_chip_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Explore,
            contentDescription = stringResource(R.string.cd_map),
            tint = HyruleEmerald,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = zone.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "• ATQ ${stats.totalAttack}",
            style = MaterialTheme.typography.labelSmall,
            color = TreasureGold,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Backpack button with upgrade dot
        Box(contentAlignment = Alignment.TopEnd) {
          HudIconButton(
            icon = Icons.Filled.Backpack,
            contentDescription = stringResource(R.string.cd_backpack),
            tint = TreasureGold,
            onClick = { onOpenModal(ActiveModal.BACKPACK) },
            modifier = Modifier.testTag("backpack_button"),
          )
          if (data.hasBetterGearInBag) {
            Box(
              modifier = Modifier
                .padding(4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(HyruleEmerald)
                .border(1.5.dp, Color.White, CircleShape),
            )
          }
        }

        HudIconButton(
          icon = Icons.Filled.Storefront,
          contentDescription = stringResource(R.string.cd_shop),
          tint = ArcaneAzure,
          onClick = { onOpenModal(ActiveModal.SHOP) },
          modifier = Modifier.testTag("shop_button"),
        )

        HudIconButton(
          icon = Icons.Filled.Person,
          contentDescription = "Perfil do Herói",
          tint = Color.White,
          onClick = { onOpenModal(ActiveModal.ACCOUNT) },
          modifier = Modifier.testTag("account_button"),
        )
      }
    }
  }
}

@Composable
private fun HudIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color(0xCC0B1510),
    shape = CircleShape,
    modifier = modifier
      .size(48.dp)
      .border(1.dp, Color(0xFF2B4A38), CircleShape),
  ) {
    IconButton(onClick = onClick) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}
