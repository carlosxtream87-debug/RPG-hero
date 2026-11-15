package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ZoneId(
  val key: String,
  val displayName: String,
  val subtitle: String,
  val minRecommendedLevel: Int,
) {
  MEADOW(
    key = "meadow",
    displayName = "Clareira Verde",
    subtitle = "Terras iniciais com slimes, goblins e baús escondidos",
    minRecommendedLevel = 1,
  ),
  RUINS(
    key = "ruins",
    displayName = "Ruínas Sombrias",
    subtitle = "Salões antigos guardados por esqueletos e morcegos",
    minRecommendedLevel = 3,
  ),
  KEEP(
    key = "keep",
    displayName = "Fortaleza Escarlate",
    subtitle = "Cidadela de lava com cavaleiros de ferro e baús lendários",
    minRecommendedLevel = 6,
  );

  companion object {
    fun fromKey(key: String): ZoneId = entries.firstOrNull { it.key == key } ?: MEADOW
  }
}

enum class ItemSlot(val key: String, val label: String) {
  WEAPON("weapon", "Espada"),
  ARMOR("armor", "Escudo"),
  CHARM("charm", "Amuleto");

  companion object {
    fun fromKey(key: String): ItemSlot = entries.firstOrNull { it.key == key } ?: WEAPON
  }
}

enum class ItemRarity(
  val key: String,
  val label: String,
  val tierOrder: Int,
  val colorHex: String,
) {
  COMMON("common", "Comum", 1, "#94A3B8"),
  UNCOMMON("uncommon", "Incomum", 2, "#22C55E"),
  RARE("rare", "Raro", 3, "#38BDF8"),
  EPIC("epic", "Épico", 4, "#A855F7"),
  LEGENDARY("legendary", "Lendário", 5, "#F59E0B");

  companion object {
    fun fromKey(key: String): ItemRarity = entries.firstOrNull { it.key == key } ?: COMMON
  }
}

@IgnoreExtraProperties
data class HeroSave(
  @DocumentId val id: String = "",
  val userId: String = "",
  val heroName: String = "Herói de Eldoria",
  val level: Long = 1L,
  val xp: Long = 0L,
  val coins: Long = 0L,
  val hp: Long = 100L,
  val maxHp: Long = 100L,
  val baseAttack: Long = 14L,
  val baseDefense: Long = 4L,
  val potions: Long = 2L,
  val currentZone: String = ZoneId.MEADOW.key,
  val monstersSlain: Long = 0L,
  val chestsOpened: Long = 0L,
  val equippedWeaponId: String? = null,
  val equippedArmorId: String? = null,
  val equippedCharmId: String? = null,
  val openedChestIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun xpToNextLevel(): Int = (level.toInt() * 60) + ((level.toInt() - 1) * 35)
}

@IgnoreExtraProperties
data class HeroItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val templateId: String = "",
  val name: String = "",
  val slot: String = ItemSlot.WEAPON.key,
  val rarity: String = ItemRarity.COMMON.key,
  val attackBonus: Long = 0L,
  val defenseBonus: Long = 0L,
  val hpBonus: Long = 0L,
  val critBonus: Long = 0L,
  val coinBonusPct: Long = 0L,
  val sellPrice: Long = 10L,
  val colorHex: String = "#94A3B8",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun itemSlot(): ItemSlot = ItemSlot.fromKey(slot)
  fun itemRarity(): ItemRarity = ItemRarity.fromKey(rarity)
  fun powerScore(): Int =
    (attackBonus * 3 + defenseBonus * 3 + hpBonus / 4 + critBonus * 2 + coinBonusPct).toInt()
}

data class ComputedHeroStats(
  val totalAttack: Int,
  val totalDefense: Int,
  val totalMaxHp: Int,
  val critChancePct: Int,
  val coinBonusPct: Int,
  val equippedWeapon: HeroItem?,
  val equippedArmor: HeroItem?,
  val equippedCharm: HeroItem?,
) {
  companion object {
    fun calculate(save: HeroSave, items: List<HeroItem>): ComputedHeroStats {
      val weapon = items.firstOrNull { it.id == save.equippedWeaponId }
      val armor = items.firstOrNull { it.id == save.equippedArmorId }
      val charm = items.firstOrNull { it.id == save.equippedCharmId }
      val equipped = listOfNotNull(weapon, armor, charm)
      return ComputedHeroStats(
        totalAttack = save.baseAttack.toInt() + equipped.sumOf { it.attackBonus.toInt() },
        totalDefense = save.baseDefense.toInt() + equipped.sumOf { it.defenseBonus.toInt() },
        totalMaxHp = save.maxHp.toInt() + equipped.sumOf { it.hpBonus.toInt() },
        critChancePct = (8 + equipped.sumOf { it.critBonus.toInt() }).coerceIn(5, 75),
        coinBonusPct = equipped.sumOf { it.coinBonusPct.toInt() }.coerceIn(0, 200),
        equippedWeapon = weapon,
        equippedArmor = armor,
        equippedCharm = charm,
      )
    }
  }
}
