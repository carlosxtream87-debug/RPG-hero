package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HeroItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RpgRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureStarterHero_andUpdateProgress_succeedsForOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RpgRepository(firestore)

    val starter = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureStarterHero(uid, "Link").getOrThrow()
    }
    assertEquals(1L, starter.level)
    assertEquals(20L, starter.coins)
    assertNotNull(starter.createdAt)

    val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveProgress(
        starter.copy(
          coins = 150L,
          level = 2L,
          xp = 30L,
          monstersSlain = 5L,
          chestsOpened = 1L,
          openedChestIds = listOf("meadow_chest_1"),
        )
      )
    }
    assertTrue(updateRes.isSuccess)

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeHeroSave(uid).first { it.save?.coins == 150L }
    }
    assertEquals(2L, observed.save?.level)
  }

  @Test
  fun addLootItem_andObserveItems_worksAndDeletesForOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RpgRepository(firestore)

    val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.addLootItem(
        uid,
        HeroItem(
          userId = uid,
          templateId = "solar_blade",
          name = "Espada Solar",
          slot = ItemSlot.WEAPON.key,
          rarity = ItemRarity.EPIC.key,
          attackBonus = 28L,
          defenseBonus = 2L,
          hpBonus = 20L,
          critBonus = 12L,
          coinBonusPct = 10L,
          sellPrice = 120L,
          colorHex = "#A855F7",
        )
      ).getOrThrow()
    }
    assertTrue(itemId.isNotBlank())

    val items = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeItems(uid).first { list -> list.any { it.id == itemId } }
    }
    assertEquals("Espada Solar", items.first { it.id == itemId }.name)

    val delRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteItem(uid, itemId)
    }
    assertTrue(delRes.isSuccess)
  }

  @Test
  fun getHeroSave_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = RpgRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureStarterHero(aliceUid, "Alice").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = RpgRepository(firestore)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getHeroSaveOnce(aliceUid)
    }
    assertTrue(result.isFailure)
    val fsEx = result.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun observeItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = RpgRepository(firestore)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeItems("some_uid").first()
      }
    } catch (e: Throwable) {
      caught = e
    }
    val fsEx = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_rpg@test.com"
    const val BOB_EMAIL = "bob_rpg@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
