package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.HeroItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.ZoneId
import com.example.game.ChestTier
import com.example.ui.theme.ArcaneAzure
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.HyruleEmerald
import com.example.ui.theme.TreasureGold
import com.example.ui.viewmodel.LootRevealState
import com.example.ui.viewmodel.RpgUiData

private fun rarityColor(rarity: ItemRarity): Color = when (rarity) {
  ItemRarity.COMMON -> Color(0xFF94A3B8)
  ItemRarity.UNCOMMON -> Color(0xFF22C55E)
  ItemRarity.RARE -> Color(0xFF38BDF8)
  ItemRarity.EPIC -> Color(0xFFA855F7)
  ItemRarity.LEGENDARY -> Color(0xFFF59E0B)
}

private fun slotIcon(slot: ItemSlot): ImageVector = when (slot) {
  ItemSlot.WEAPON -> Icons.Filled.SportsKabaddi
  ItemSlot.ARMOR -> Icons.Filled.Shield
  ItemSlot.CHARM -> Icons.Filled.AutoAwesome
}

@Composable
fun LootRevealDialog(
  reveal: LootRevealState,
  onEquipAndClose: () -> Unit,
  onKeepInBag: () -> Unit,
) {
  val item = reveal.item
  val rarity = item.itemRarity()
  val rColor = rarityColor(rarity)
  val currentPower = reveal.currentEquippedInSlot?.powerScore() ?: 0
  val newPower = item.powerScore()
  val diff = newPower - currentPower

  Dialog(onDismissRequest = onKeepInBag) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(2.dp, rColor, RoundedCornerShape(24.dp)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = reveal.chestTier.label.uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = TreasureGold,
        )

        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(rColor.copy(alpha = 0.18f))
            .border(2.dp, rColor, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = slotIcon(item.itemSlot()),
            contentDescription = item.name,
            tint = rColor,
            modifier = Modifier.size(34.dp),
          )
        }

        Surface(color = rColor.copy(alpha = 0.2f), shape = RoundedCornerShape(999.dp)) {
          Text(
            text = "${item.itemSlot().label} • ${rarity.label}",
            style = MaterialTheme.typography.labelMedium,
            color = rColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )
        }

        Text(
          text = item.name,
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
        )

        ItemStatBadges(item = item)

        if (reveal.currentEquippedInSlot != null) {
          val cmpText = when {
            diff > 0 -> "+$diff Poder vs. ${reveal.currentEquippedInSlot.name} (Melhor!)"
            diff < 0 -> "$diff Poder vs. ${reveal.currentEquippedInSlot.name}"
            else -> "Mesmo poder que ${reveal.currentEquippedInSlot.name}"
          }
          Text(
            text = cmpText,
            style = MaterialTheme.typography.labelMedium,
            color = if (diff > 0) HyruleEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (reveal.bonusCoins > 0 || reveal.bonusXp > 0) {
          Text(
            text = "+${reveal.bonusCoins} Moedas  •  +${reveal.bonusXp} XP",
            style = MaterialTheme.typography.labelLarge,
            color = TreasureGold,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onKeepInBag,
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Text("Guardar")
          }
          Button(
            onClick = onEquipAndClose,
            colors = ButtonDefaults.buttonColors(containerColor = rColor, contentColor = Color(0xFF07110B)),
            modifier = Modifier.weight(1.3f).height(48.dp),
          ) {
            Text("Equipar Agora", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
fun BackpackDialog(
  data: RpgUiData,
  onEquip: (HeroItem) -> Unit,
  onSell: (HeroItem) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedSlot by remember { mutableStateOf<ItemSlot?>(null) }
  val filteredItems = remember(data.items, selectedSlot) {
    if (selectedSlot == null) data.items else data.items.filter { it.itemSlot() == selectedSlot }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 620.dp)
        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Mochila & Status", style = MaterialTheme.typography.headlineSmall, color = Color.White)
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
          }
        }

        // RPG Attributes Summary
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            StatPill("ATQ", "${data.stats.totalAttack}", CrimsonHeart)
            StatPill("DEF", "${data.stats.totalDefense}", ArcaneAzure)
            StatPill("HP", "${data.stats.totalMaxHp}", HyruleEmerald)
            StatPill("CRIT", "${data.stats.critChancePct}%", TreasureGold)
            StatPill("MOEDA", "+${data.stats.coinBonusPct}%", TreasureGold)
          }
        }

        // Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = selectedSlot == null,
            onClick = { selectedSlot = null },
            label = { Text("Todos") },
          )
          ItemSlot.entries.forEach { slot ->
            FilterChip(
              selected = selectedSlot == slot,
              onClick = { selectedSlot = if (selectedSlot == slot) null else slot },
              label = { Text(slot.label) },
            )
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filteredItems, key = { it.id }) { item ->
            val equipped = item.id == data.save.equippedWeaponId ||
              item.id == data.save.equippedArmorId ||
              item.id == data.save.equippedCharmId
            val rColor = rarityColor(item.itemRarity())

            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (equipped) rColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (equipped) rColor else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
              shape = RoundedCornerShape(14.dp),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rColor.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(slotIcon(item.itemSlot()), contentDescription = null, tint = rColor, modifier = Modifier.size(22.dp))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(item.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                  Text(
                    "${item.itemSlot().label} • ${item.itemRarity().label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = rColor,
                  )
                  ItemStatBadges(item = item)
                }

                if (equipped) {
                  Surface(color = HyruleEmerald, shape = RoundedCornerShape(999.dp)) {
                    Text(
                      "EM USO",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF04140C),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                } else {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                    Button(
                      onClick = { onEquip(item) },
                      modifier = Modifier.height(36.dp),
                    ) {
                      Text("Equipar", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                      onClick = { onSell(item) },
                      modifier = Modifier.height(34.dp),
                    ) {
                      Text("+${item.sellPrice} 🪙", style = MaterialTheme.typography.labelSmall, color = TreasureGold)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ShopDialog(
  data: RpgUiData,
  onBuyPotion: () -> Unit,
  onTrainStats: () -> Unit,
  onBuyChest: (ChestTier, Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val trainCost = 40L + (data.save.baseAttack - 14L) * 15L

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 600.dp)
        .border(1.5.dp, TreasureGold.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Loja do Ferreiro", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Suas moedas: ${data.save.coins} 🪙", style = MaterialTheme.typography.labelLarge, color = TreasureGold)
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
          }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          item {
            ShopOfferRow(
              icon = Icons.Filled.LocalDrink,
              accent = CrimsonHeart,
              title = "Poção de Vida",
              subtitle = "Restaura 45% do HP máximo em combate (${data.save.potions} na mochila)",
              cost = 25L,
              canAfford = data.save.coins >= 25L,
              onClick = onBuyPotion,
            )
          }
          item {
            ShopOfferRow(
              icon = Icons.Filled.FitnessCenter,
              accent = HyruleEmerald,
              title = "Treino do Guerreiro",
              subtitle = "+2 ATQ base, +1 DEF base e +10 HP Máx permanentes",
              cost = trainCost,
              canAfford = data.save.coins >= trainCost,
              onClick = onTrainStats,
            )
          }
          item {
            ShopOfferRow(
              icon = Icons.Filled.Star,
              accent = ArcaneAzure,
              title = "Baú de Ferro Misterioso",
              subtitle = "Garante item Raro ou Épico para seu herói",
              cost = 80L,
              canAfford = data.save.coins >= 80L,
              onClick = { onBuyChest(ChestTier.IRON, 80L) },
            )
          }
          item {
            ShopOfferRow(
              icon = Icons.Filled.AutoAwesome,
              accent = Color(0xFFA855F7),
              title = "Baú Dourado Real",
              subtitle = "Garante item Épico ou Lendário superior",
              cost = 200L,
              canAfford = data.save.coins >= 200L,
              onClick = { onBuyChest(ChestTier.GOLD, 200L) },
            )
          }
          item {
            ShopOfferRow(
              icon = Icons.Filled.AutoAwesome,
              accent = TreasureGold,
              title = "Baú Lendário de Eldoria",
              subtitle = "Garante equipamento Lendário de poder máximo",
              cost = 450L,
              canAfford = data.save.coins >= 450L,
              onClick = { onBuyChest(ChestTier.LEGENDARY, 450L) },
            )
          }
        }
      }
    }
  }
}

@Composable
fun ZoneMapDialog(
  data: RpgUiData,
  onSelectZone: (ZoneId) -> Unit,
  onDismiss: () -> Unit,
) {
  val current = ZoneId.fromKey(data.save.currentZone)
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Mapa de Eldoria", style = MaterialTheme.typography.headlineSmall, color = Color.White)
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
          }
        }

        ZoneId.entries.forEach { zone ->
          val isCurrent = zone == current
          Surface(
            color = if (isCurrent) HyruleEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                1.dp,
                if (isCurrent) HyruleEmerald else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp),
              ),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(Icons.Filled.Explore, contentDescription = null, tint = if (isCurrent) HyruleEmerald else TreasureGold)
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(zone.displayName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(zone.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nível recomendado: ${zone.minRecommendedLevel}+", style = MaterialTheme.typography.labelSmall, color = TreasureGold)
              }
              if (isCurrent) {
                Text("AQUI", style = MaterialTheme.typography.labelMedium, color = HyruleEmerald)
              } else {
                Button(onClick = { onSelectZone(zone) }, modifier = Modifier.height(40.dp)) {
                  Text("Ir")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AccountDialog(
  data: RpgUiData,
  userEmail: String,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(data.save.heroName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
          }
        }

        if (userEmail.isNotBlank()) {
          Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nível: ${data.save.level}  •  Moedas: ${data.save.coins} 🪙", color = TreasureGold, style = MaterialTheme.typography.titleSmall)
            Text("Monstros derrotados: ${data.save.monstersSlain}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("Baús abertos: ${data.save.chestsOpened}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("Itens na mochila: ${data.items.size}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
          }
        }

        Button(
          onClick = onSignOut,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.action_sign_out))
        }
      }
    }
  }
}

@Composable
private fun ShopOfferRow(
  icon: ImageVector,
  accent: Color,
  title: String,
  subtitle: String,
  cost: Long,
  canAfford: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Button(
        onClick = onClick,
        enabled = canAfford,
        modifier = Modifier.height(40.dp),
      ) {
        Icon(Icons.Filled.MonetizationOn, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$cost", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelLarge, color = color)
  }
}

@Composable
private fun ItemStatBadges(item: HeroItem) {
  val parts = buildList {
    if (item.attackBonus > 0) add("+${item.attackBonus} ATQ")
    if (item.defenseBonus > 0) add("+${item.defenseBonus} DEF")
    if (item.hpBonus > 0) add("+${item.hpBonus} HP")
    if (item.critBonus > 0) add("+${item.critBonus}% CRIT")
    if (item.coinBonusPct > 0) add("+${item.coinBonusPct}% 🪙")
  }
  Text(
    text = parts.joinToString("  •  ").ifBlank { "Item básico" },
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
