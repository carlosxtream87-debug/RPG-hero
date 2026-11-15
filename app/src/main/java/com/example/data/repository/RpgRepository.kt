package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.HeroItem
import com.example.data.model.HeroSave
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.ZoneId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class SaveSnapshot(
  val save: HeroSave?,
  val hasPendingWrites: Boolean,
)

class RpgRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun observeHeroSave(userId: String): Flow<SaveSnapshot> {
    val docRef = db.collection("users").document(userId)
      .collection("hero_saves").document(PRIMARY_SAVE_ID)
    return docRef.snapshots()
      .map { snapshot ->
        val save = if (snapshot.exists()) {
          snapshot.toObject(HeroSave::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
        SaveSnapshot(save = save, hasPendingWrites = snapshot.metadata.hasPendingWrites())
      }
      .catch { error ->
        handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  fun observeItems(userId: String): Flow<List<HeroItem>> {
    val colRef = db.collection("users").document(userId).collection("hero_items")
    return colRef.snapshots()
      .map { snapshot ->
        snapshot.toObjects(HeroItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.powerScore() }
      }
      .catch { error ->
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun ensureStarterHero(userId: String, heroName: String): Result<HeroSave> {
    val saveRef = db.collection("users").document(userId)
      .collection("hero_saves").document(PRIMARY_SAVE_ID)
    val itemsCol = db.collection("users").document(userId).collection("hero_items")

    return try {
      val existing = saveRef.get().await()
      if (existing.exists()) {
        val parsed = existing.toObject(HeroSave::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (parsed != null) return Result.success(parsed)
      }

      val starterSwordRef = itemsCol.document(STARTER_SWORD_ID)
      val starterSwordSnap = starterSwordRef.get().await()
      if (!starterSwordSnap.exists()) {
        val swordPayload = mapOf(
          "userId" to userId,
          "templateId" to "wooden_blade",
          "name" to "Espada do Aprendiz",
          "slot" to ItemSlot.WEAPON.key,
          "rarity" to ItemRarity.COMMON.key,
          "attackBonus" to 6L,
          "defenseBonus" to 0L,
          "hpBonus" to 0L,
          "critBonus" to 2L,
          "coinBonusPct" to 0L,
          "sellPrice" to 10L,
          "colorHex" to "#D97706",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        starterSwordRef.set(swordPayload).await()
      }

      val cleanName = heroName.trim().take(40).ifBlank { "Herói de Eldoria" }
      val savePayload = mapOf(
        "userId" to userId,
        "heroName" to cleanName,
        "level" to 1L,
        "xp" to 0L,
        "coins" to 20L,
        "hp" to 100L,
        "maxHp" to 100L,
        "baseAttack" to 14L,
        "baseDefense" to 4L,
        "potions" to 2L,
        "currentZone" to ZoneId.MEADOW.key,
        "monstersSlain" to 0L,
        "chestsOpened" to 0L,
        "equippedWeaponId" to STARTER_SWORD_ID,
        "openedChestIds" to emptyList<String>(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      saveRef.set(savePayload).await()

      val created = saveRef.get().await()
        .toObject(HeroSave::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: HeroSave(
          id = PRIMARY_SAVE_ID,
          userId = userId,
          heroName = cleanName,
          coins = 20L,
          equippedWeaponId = STARTER_SWORD_ID,
        )
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, saveRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveProgress(save: HeroSave): Result<Unit> {
    val uid = save.userId
    require(uid.isNotBlank()) { "userId must not be blank" }
    requireNotNull(save.createdAt) { "createdAt must be present before updating HeroSave" }
    val saveId = save.id.ifBlank { PRIMARY_SAVE_ID }
    val docRef = db.collection("users").document(uid).collection("hero_saves").document(saveId)
    return try {
      docRef.set(
        save.copy(
          id = saveId,
          openedChestIds = save.openedChestIds.distinct().take(40),
          updatedAt = null,
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun addLootItem(userId: String, item: HeroItem): Result<String> {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val docRef = db.collection("users").document(userId).collection("hero_items").document()
    val payload = mapOf(
      "userId" to userId,
      "templateId" to item.templateId,
      "name" to item.name.take(60),
      "slot" to item.slot,
      "rarity" to item.rarity,
      "attackBonus" to item.attackBonus,
      "defenseBonus" to item.defenseBonus,
      "hpBonus" to item.hpBonus,
      "critBonus" to item.critBonus,
      "coinBonusPct" to item.coinBonusPct,
      "sellPrice" to item.sellPrice,
      "colorHex" to item.colorHex,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteItem(userId: String, itemId: String): Result<Unit> {
    require(userId.isNotBlank() && itemId.isNotBlank()) { "userId and itemId required" }
    val docRef = db.collection("users").document(userId).collection("hero_items").document(itemId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun getHeroSaveOnce(userId: String): Result<HeroSave?> {
    val docRef = db.collection("users").document(userId)
      .collection("hero_saves").document(PRIMARY_SAVE_ID)
    return try {
      val snap = docRef.get().await()
      val save = if (snap.exists()) {
        snap.toObject(HeroSave::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else {
        null
      }
      Result.success(save)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  companion object {
    const val PRIMARY_SAVE_ID = "primary"
    const val STARTER_SWORD_ID = "starter_sword"
  }
}
