package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ComputedHeroStats
import com.example.data.model.HeroItem
import com.example.data.model.HeroSave
import com.example.data.model.ItemSlot
import com.example.data.model.ZoneId
import com.example.data.repository.RpgRepository
import com.example.game.ChestEntity
import com.example.game.ChestTier
import com.example.game.RpgCatalog
import com.example.game.RpgGameEngine
import com.example.game.TileType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class RpgUiData(
  val save: HeroSave,
  val items: List<HeroItem>,
  val stats: ComputedHeroStats,
  val hasPendingWrites: Boolean,
  val hasBetterGearInBag: Boolean,
)

data class LootRevealState(
  val chestTier: ChestTier,
  val item: HeroItem,
  val savedItemId: String?,
  val bonusCoins: Int,
  val bonusXp: Int,
  val currentEquippedInSlot: HeroItem?,
)

enum class ActiveModal {
  NONE,
  BACKPACK,
  SHOP,
  ZONES,
  ACCOUNT,
}

class RpgViewModel(
  private val repository: RpgRepository,
  private val currentUserId: String,
  private val heroDisplayName: String,
) : ViewModel() {

  val engine = RpgGameEngine()

  var joyX by mutableFloatStateOf(0f)
  var joyY by mutableFloatStateOf(0f)
  var frameTick by mutableLongStateOf(0L)
    private set

  private val _activeModal = MutableStateFlow(ActiveModal.NONE)
  val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

  private val _lootReveal = MutableStateFlow<LootRevealState?>(null)
  val lootReveal: StateFlow<LootRevealState?> = _lootReveal.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  // Live in-memory working save synced from Firestore and pushed back cleanly
  private var localSave: HeroSave? = null
  private var localItems: List<HeroItem> = emptyList()
  private var localStats: ComputedHeroStats? = null
  private var initializedWorld = false
  private var dirtySinceMs = 0L
  private var lastSyncMs = 0L

  val uiState: StateFlow<UiState<RpgUiData>> = combine(
    repository.observeHeroSave(currentUserId),
    repository.observeItems(currentUserId),
  ) { saveSnap, items ->
    val save = saveSnap.save
    if (save == null) {
      UiState.Loading
    } else {
      // Keep localSave initialized from Firestore when first loaded or when createdAt arrives
      val effectiveSave = if (localSave == null || localSave?.createdAt == null) {
        save
      } else {
        localSave!!.copy(createdAt = save.createdAt)
      }
      localSave = effectiveSave
      localItems = items
      val stats = ComputedHeroStats.calculate(effectiveSave, items)
      localStats = stats

      if (!initializedWorld) {
        initializedWorld = true
        engine.loadZone(
          zoneId = ZoneId.fromKey(effectiveSave.currentZone),
          openedChestIds = effectiveSave.openedChestIds.toSet(),
        )
      }

      val hasBetter = checkHasBetterGear(effectiveSave, items, stats)
      UiState.Success(
        RpgUiData(
          save = effectiveSave,
          items = items,
          stats = stats,
          hasPendingWrites = saveSnap.hasPendingWrites,
          hasBetterGearInBag = hasBetter,
        )
      )
    }
  }
    .catch { error ->
      val fsError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (fsError == null && error is CancellationException) throw error
      val root = fsError ?: error
      Log.w(TAG, "Failed observing RPG state", root)
      emit(UiState.Error(root.message ?: "Erro ao carregar progresso da aventura"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  init {
    viewModelScope.launch {
      repository.ensureStarterHero(currentUserId, heroDisplayName)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Não foi possível iniciar o herói."
        }
    }
  }

  fun stepFrame(dtSeconds: Float, nowMs: Long) {
    if (_activeModal.value != ActiveModal.NONE || _lootReveal.value != null) {
      return
    }
    val save = localSave ?: return
    val stats = localStats ?: return

    val events = engine.step(
      dtRaw = dtSeconds,
      joyX = joyX,
      joyY = joyY,
      stats = stats,
      currentHp = save.hp.toInt(),
    )

    var updated = save
    var changed = false

    if (events.coinsCollected > 0 || events.xpGained > 0 || events.monstersKilled > 0 || events.hpDelta != 0) {
      changed = true
      var newHp = (updated.hp.toInt() + events.hpDelta).coerceIn(0, stats.totalMaxHp)
      var newCoins = updated.coins + events.coinsCollected
      var newXp = updated.xp + events.xpGained
      var newLevel = updated.level
      var newMaxHp = updated.maxHp
      var newBaseAtk = updated.baseAttack
      var newBaseDef = updated.baseDefense
      val newSlain = updated.monstersSlain + events.monstersKilled

      // Check Level Up!
      var leveledUp = false
      while (newLevel < 99 && newXp >= ((newLevel.toInt() * 60) + ((newLevel.toInt() - 1) * 35))) {
        val needed = (newLevel.toInt() * 60) + ((newLevel.toInt() - 1) * 35)
        newXp -= needed
        newLevel += 1
        newMaxHp += 15
        newBaseAtk += 3
        newBaseDef += 1
        leveledUp = true
      }

      if (leveledUp) {
        val refreshedStats = ComputedHeroStats.calculate(
          updated.copy(
            level = newLevel,
            maxHp = newMaxHp,
            baseAttack = newBaseAtk,
            baseDefense = newBaseDef,
          ),
          localItems,
        )
        newHp = refreshedStats.totalMaxHp
        engine.spawnBurst(engine.heroX, engine.heroY, 0xFFFBBF24, 24, 180f)
        engine.addFloatingText("NÍVEL $newLevel!", engine.heroX, engine.heroY - 36f, 0xFFFBBF24, isBig = true)
        _bannerMessage.value = "Subiu para o Nível $newLevel! HP restaurado e atributos aumentados!"
      }

      // Check Hero Defeat
      if (newHp <= 0) {
        newHp = stats.totalMaxHp
        val safeZone = ZoneId.MEADOW
        engine.loadZone(safeZone, updated.openedChestIds.toSet())
        updated = updated.copy(
          hp = newHp.toLong(),
          currentZone = safeZone.key,
        )
        _bannerMessage.value = "Você caiu em combate e voltou à Clareira Verde com HP cheio!"
        commitSaveNow(updated)
      } else {
        updated = updated.copy(
          level = newLevel,
          xp = newXp,
          coins = newCoins,
          hp = newHp.toLong(),
          maxHp = newMaxHp,
          baseAttack = newBaseAtk,
          baseDefense = newBaseDef,
          monstersSlain = newSlain,
        )
        if (leveledUp) {
          commitSaveNow(updated)
        }
      }
    }

    if (events.portalTriggered != null) {
      val currentZone = ZoneId.fromKey(updated.currentZone)
      val targetZone = when (events.portalTriggered) {
        TileType.PORTAL_NEXT -> when (currentZone) {
          ZoneId.MEADOW -> ZoneId.RUINS
          ZoneId.RUINS -> ZoneId.KEEP
          ZoneId.KEEP -> ZoneId.KEEP
        }
        TileType.PORTAL_PREV -> when (currentZone) {
          ZoneId.KEEP -> ZoneId.RUINS
          ZoneId.RUINS -> ZoneId.MEADOW
          ZoneId.MEADOW -> ZoneId.MEADOW
        }
        else -> currentZone
      }
      if (targetZone != currentZone) {
        val fromNext = events.portalTriggered == TileType.PORTAL_PREV
        engine.loadZone(targetZone, updated.openedChestIds.toSet(), fromNextZone = fromNext)
        updated = updated.copy(currentZone = targetZone.key)
        _bannerMessage.value = "Entrou em: ${targetZone.displayName}"
        commitSaveNow(updated)
        changed = false
      }
    }

    if (changed) {
      localSave = updated
      localStats = ComputedHeroStats.calculate(updated, localItems)
      if (dirtySinceMs == 0L) dirtySinceMs = nowMs
      if (nowMs - lastSyncMs >= 3500L) {
        commitSaveNow(updated)
      }
    }

    frameTick++
  }

  fun onPrimaryAction(): Boolean {
    val chest = engine.triggerSwordOrInteract()
    if (chest != null) {
      openWorldChest(chest)
      return true
    }
    if (engine.nearMerchant) {
      openModal(ActiveModal.SHOP)
      return true
    }
    return false
  }

  fun onDashAction(): Boolean = engine.triggerDash()

  fun onUsePotion() {
    val save = localSave ?: return
    val stats = localStats ?: return
    if (save.potions <= 0) {
      _bannerMessage.value = "Sem poções! Compre na Loja do Ferreiro."
      return
    }
    if (save.hp >= stats.totalMaxHp) {
      _bannerMessage.value = "Seu HP já está no máximo!"
      return
    }
    val heal = max(45, (stats.totalMaxHp * 0.45f).toInt())
    val newHp = min(stats.totalMaxHp, save.hp.toInt() + heal)
    val updated = save.copy(
      hp = newHp.toLong(),
      potions = save.potions - 1L,
    )
    engine.spawnBurst(engine.heroX, engine.heroY, 0xFF22C55E, 14, 120f)
    engine.addFloatingText("+$heal HP", engine.heroX, engine.heroY - 30f, 0xFF22C55E, isBig = true)
    commitSaveNow(updated)
  }

  private fun openWorldChest(chest: ChestEntity) {
    val save = localSave ?: return
    if (chest.opened) return
    engine.markChestOpened(chest.id)

    val bestSwordAtk = localItems
      .filter { it.itemSlot() == ItemSlot.WEAPON }
      .maxOfOrNull { it.attackBonus.toInt() } ?: 6

    val rolled = RpgCatalog.rollChestLoot(
      userId = currentUserId,
      chestTier = chest.tier,
      heroLevel = save.level.toInt(),
      currentBestWeaponAttack = bestSwordAtk,
    )

    val bonusCoins = chest.tier.bonusCoins + ((chest.tier.bonusCoins * (localStats?.coinBonusPct ?: 0)) / 100)
    val bonusXp = chest.tier.bonusXp
    val newOpenedList = if (chest.isBossBonus) {
      save.openedChestIds
    } else {
      (save.openedChestIds + chest.id).distinct().take(40)
    }

    var newXp = save.xp + bonusXp
    var newLevel = save.level
    var newMaxHp = save.maxHp
    var newBaseAtk = save.baseAttack
    var newBaseDef = save.baseDefense
    while (newLevel < 99 && newXp >= ((newLevel.toInt() * 60) + ((newLevel.toInt() - 1) * 35))) {
      newXp -= (newLevel.toInt() * 60) + ((newLevel.toInt() - 1) * 35)
      newLevel += 1
      newMaxHp += 15
      newBaseAtk += 3
      newBaseDef += 1
    }

    val updated = save.copy(
      level = newLevel,
      xp = newXp,
      coins = save.coins + bonusCoins,
      maxHp = newMaxHp,
      baseAttack = newBaseAtk,
      baseDefense = newBaseDef,
      chestsOpened = save.chestsOpened + 1L,
      openedChestIds = newOpenedList,
    )
    commitSaveNow(updated)

    viewModelScope.launch {
      val itemId = repository.addLootItem(currentUserId, rolled).getOrNull()
      val currentSlotItem = when (rolled.itemSlot()) {
        ItemSlot.WEAPON -> localStats?.equippedWeapon
        ItemSlot.ARMOR -> localStats?.equippedArmor
        ItemSlot.CHARM -> localStats?.equippedCharm
      }
      _lootReveal.value = LootRevealState(
        chestTier = chest.tier,
        item = rolled.copy(id = itemId ?: ""),
        savedItemId = itemId,
        bonusCoins = bonusCoins,
        bonusXp = bonusXp,
        currentEquippedInSlot = currentSlotItem,
      )
    }
  }

  fun equipItem(item: HeroItem) {
    val save = localSave ?: return
    if (item.id.isBlank()) return
    val updated = when (item.itemSlot()) {
      ItemSlot.WEAPON -> save.copy(equippedWeaponId = item.id)
      ItemSlot.ARMOR -> save.copy(equippedArmorId = item.id)
      ItemSlot.CHARM -> save.copy(equippedCharmId = item.id)
    }
    val newStats = ComputedHeroStats.calculate(updated, localItems)
    val clampedHp = min(updated.hp.toInt(), newStats.totalMaxHp).toLong()
    commitSaveNow(updated.copy(hp = clampedHp))
    _bannerMessage.value = "Equipado: ${item.name}"
  }

  fun equipRevealedItemAndClose() {
    val reveal = _lootReveal.value ?: return
    val id = reveal.savedItemId
    if (!id.isNullOrBlank()) {
      equipItem(reveal.item.copy(id = id))
    }
    _lootReveal.value = null
  }

  fun dismissLootReveal() {
    _lootReveal.value = null
  }

  fun sellItem(item: HeroItem) {
    val save = localSave ?: return
    if (item.id.isBlank()) return
    if (item.id == save.equippedWeaponId || item.id == save.equippedArmorId || item.id == save.equippedCharmId) {
      _bannerMessage.value = "Desequipe ou troque este item antes de vender."
      return
    }
    val updated = save.copy(coins = save.coins + item.sellPrice)
    commitSaveNow(updated)
    viewModelScope.launch {
      repository.deleteItem(currentUserId, item.id)
    }
    _bannerMessage.value = "Vendeu ${item.name} por +${item.sellPrice} moedas!"
  }

  fun buyPotion() {
    val save = localSave ?: return
    val cost = 25L
    if (save.coins < cost) {
      _bannerMessage.value = "Moedas insuficientes (precisa de $cost 🪙)."
      return
    }
    if (save.potions >= 99) {
      _bannerMessage.value = "Mochila de poções cheia!"
      return
    }
    commitSaveNow(save.copy(coins = save.coins - cost, potions = save.potions + 1L))
    _bannerMessage.value = "Comprou 1 Poção de Vida!"
  }

  fun buyMysteryChest(tier: ChestTier, cost: Long) {
    val save = localSave ?: return
    if (save.coins < cost) {
      _bannerMessage.value = "Moedas insuficientes (precisa de $cost 🪙)."
      return
    }
    val bestSwordAtk = localItems
      .filter { it.itemSlot() == ItemSlot.WEAPON }
      .maxOfOrNull { it.attackBonus.toInt() } ?: 6
    val rolled = RpgCatalog.rollChestLoot(
      userId = currentUserId,
      chestTier = tier,
      heroLevel = save.level.toInt(),
      currentBestWeaponAttack = bestSwordAtk,
    )
    val updated = save.copy(
      coins = save.coins - cost,
      chestsOpened = save.chestsOpened + 1L,
    )
    commitSaveNow(updated)

    viewModelScope.launch {
      val itemId = repository.addLootItem(currentUserId, rolled).getOrNull()
      val currentSlotItem = when (rolled.itemSlot()) {
        ItemSlot.WEAPON -> localStats?.equippedWeapon
        ItemSlot.ARMOR -> localStats?.equippedArmor
        ItemSlot.CHARM -> localStats?.equippedCharm
      }
      _activeModal.value = ActiveModal.NONE
      _lootReveal.value = LootRevealState(
        chestTier = tier,
        item = rolled.copy(id = itemId ?: ""),
        savedItemId = itemId,
        bonusCoins = 0,
        bonusXp = 15,
        currentEquippedInSlot = currentSlotItem,
      )
    }
  }

  fun trainHeroStats() {
    val save = localSave ?: return
    val cost = 40L + (save.baseAttack - 14L) * 15L
    if (save.coins < cost) {
      _bannerMessage.value = "Moedas insuficientes (precisa de $cost 🪙)."
      return
    }
    val updated = save.copy(
      coins = save.coins - cost,
      baseAttack = save.baseAttack + 2L,
      baseDefense = save.baseDefense + 1L,
      maxHp = save.maxHp + 10L,
      hp = min(save.hp + 10L, (localStats?.totalMaxHp ?: 100) + 10L),
    )
    commitSaveNow(updated)
    _bannerMessage.value = "Treinamento concluído! +2 ATQ, +1 DEF, +10 HP Máx"
  }

  fun travelToZone(zoneId: ZoneId) {
    val save = localSave ?: return
    engine.loadZone(zoneId, save.openedChestIds.toSet())
    commitSaveNow(save.copy(currentZone = zoneId.key))
    _activeModal.value = ActiveModal.NONE
    _bannerMessage.value = "Viajou para ${zoneId.displayName}"
  }

  fun openModal(modal: ActiveModal) {
    joyX = 0f
    joyY = 0f
    _activeModal.value = modal
  }

  fun closeModal() {
    _activeModal.value = ActiveModal.NONE
  }

  fun clearBanner() {
    _bannerMessage.value = null
  }

  fun syncNowIfDirty() {
    val save = localSave ?: return
    if (dirtySinceMs > 0L) {
      commitSaveNow(save)
    }
  }

  private fun commitSaveNow(save: HeroSave) {
    localSave = save
    localStats = ComputedHeroStats.calculate(save, localItems)
    dirtySinceMs = 0L
    lastSyncMs = System.currentTimeMillis()
    if (save.createdAt == null) return
    viewModelScope.launch {
      repository.saveProgress(save)
    }
  }

  private fun checkHasBetterGear(
    save: HeroSave,
    items: List<HeroItem>,
    stats: ComputedHeroStats,
  ): Boolean {
    val bestWep = stats.equippedWeapon?.powerScore() ?: 0
    val bestArm = stats.equippedArmor?.powerScore() ?: 0
    val bestChm = stats.equippedCharm?.powerScore() ?: 0
    return items.any { item ->
      when (item.itemSlot()) {
        ItemSlot.WEAPON -> item.id != save.equippedWeaponId && item.powerScore() > bestWep
        ItemSlot.ARMOR -> item.id != save.equippedArmorId && item.powerScore() > bestArm
        ItemSlot.CHARM -> item.id != save.equippedCharmId && item.powerScore() > bestChm
      }
    }
  }

  private companion object {
    const val TAG = "RpgVM"
  }
}
