package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOut
import com.example.data.repository.RpgRepository
import com.example.ui.canvas.RpgWorldCanvas
import com.example.ui.components.AccountDialog
import com.example.ui.components.BackpackDialog
import com.example.ui.components.LootRevealDialog
import com.example.ui.components.RpgTopHud
import com.example.ui.components.ShopDialog
import com.example.ui.components.SimpleOnScreenControls
import com.example.ui.components.ZoneMapDialog
import com.example.ui.theme.HyruleEmerald
import com.example.ui.theme.TreasureGold
import com.example.ui.viewmodel.ActiveModal
import com.example.ui.viewmodel.RpgViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RpgGameScreen(
  currentUserId: String,
  userDisplayName: String,
  userEmail: String,
) {
  val viewModel: RpgViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        RpgViewModel(
          repository = RpgRepository(db),
          currentUserId = currentUserId,
          heroDisplayName = userDisplayName,
        )
      }
    },
  )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeModal by viewModel.activeModal.collectAsStateWithLifecycle()
  val lootReveal by viewModel.lootReveal.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val credentialManager = androidx.compose.runtime.remember(context) { CredentialManager.create(context) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  if (activeModal != ActiveModal.NONE) {
    BackHandler { viewModel.closeModal() }
  }
  if (lootReveal != null) {
    BackHandler { viewModel.dismissLootReveal() }
  }

  DisposableEffect(viewModel) {
    onDispose {
      viewModel.syncNowIfDirty()
    }
  }

  LaunchedEffect(bannerMessage) {
    if (bannerMessage != null) {
      delay(2600L)
      viewModel.clearBanner()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    when (val state = uiState) {
      is UiState.Loading -> {
        Column(
          modifier = Modifier.align(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(color = HyruleEmerald)
          Text(
            text = "Carregando mundo de Eldoria...",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
          )
        }
      }

      is UiState.Error -> {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Erro ao carregar aventura",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
          )
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
          )
        }
      }

      is UiState.Success -> {
        val data = state.data

        // 60 FPS game simulation loop
        LaunchedEffect(viewModel) {
          var lastNanos = 0L
          while (isActive) {
            withFrameNanos { nanos ->
              if (lastNanos != 0L) {
                val dt = (nanos - lastNanos) / 1_000_000_000f
                viewModel.stepFrame(dt, System.currentTimeMillis())
              }
              lastNanos = nanos
            }
          }
        }

        // 1. 2D Zelda-Style World Canvas
        RpgWorldCanvas(
          engine = viewModel.engine,
          stats = data.stats,
          frameTick = viewModel.frameTick,
          modifier = Modifier.fillMaxSize(),
        )

        // 2. Top Compact RPG HUD
        RpgTopHud(
          data = data,
          onOpenModal = viewModel::openModal,
          modifier = Modifier.align(Alignment.TopCenter),
        )

        // 3. Transient Event Toast Banner (Level Up, Zone Travel, Purchases)
        AnimatedVisibility(
          visible = bannerMessage != null,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 128.dp, start = 20.dp, end = 20.dp),
        ) {
          Surface(
            color = Color(0xE60B1510),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.border(1.dp, TreasureGold, RoundedCornerShape(999.dp)),
          ) {
            Text(
              text = bannerMessage.orEmpty(),
              style = MaterialTheme.typography.labelLarge,
              color = Color(0xFFFEF08A),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }

        // 4. Bottom Simple On-Screen Controls
        SimpleOnScreenControls(
          hasNearbyChest = viewModel.engine.nearChest != null,
          hasNearbyMerchant = viewModel.engine.nearMerchant,
          potionsCount = data.save.potions.toInt(),
          dashReady = viewModel.engine.dashCooldown <= 0f,
          onJoystickMove = { jx, jy ->
            viewModel.joyX = jx
            viewModel.joyY = jy
          },
          onPrimaryAction = { viewModel.onPrimaryAction() },
          onUsePotion = viewModel::onUsePotion,
          onDash = { viewModel.onDashAction() },
          modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 5. RPG Modals
        when (activeModal) {
          ActiveModal.BACKPACK -> BackpackDialog(
            data = data,
            onEquip = viewModel::equipItem,
            onSell = viewModel::sellItem,
            onDismiss = viewModel::closeModal,
          )
          ActiveModal.SHOP -> ShopDialog(
            data = data,
            onBuyPotion = viewModel::buyPotion,
            onTrainStats = viewModel::trainHeroStats,
            onBuyChest = viewModel::buyMysteryChest,
            onDismiss = viewModel::closeModal,
          )
          ActiveModal.ZONES -> ZoneMapDialog(
            data = data,
            onSelectZone = viewModel::travelToZone,
            onDismiss = viewModel::closeModal,
          )
          ActiveModal.ACCOUNT -> AccountDialog(
            data = data,
            userEmail = userEmail,
            onSignOut = {
              viewModel.syncNowIfDirty()
              viewModel.closeModal()
              signOut(credentialManager = credentialManager, scope = scope)
            },
            onDismiss = viewModel::closeModal,
          )
          ActiveModal.NONE -> Unit
        }

        lootReveal?.let { reveal ->
          LootRevealDialog(
            reveal = reveal,
            onEquipAndClose = viewModel::equipRevealedItemAndClose,
            onKeepInBag = viewModel::dismissLootReveal,
          )
        }
      }
    }
  }
}
