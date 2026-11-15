package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.TreasureGold

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, TreasureGold.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_rpg_hero),
            contentDescription = stringResource(R.string.cd_hero_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    Color(0x220B1510),
                    Color(0xE60B1510),
                  )
                )
              )
              .padding(20.dp),
            contentAlignment = Alignment.BottomStart,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Surface(
                color = TreasureGold,
                shape = RoundedCornerShape(999.dp),
              ) {
                Text(
                  text = stringResource(R.string.auth_badge),
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFF1F1202),
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
              }
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
              )
              Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2E8F0),
              )
            }
          }
        }

        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            RoundedCornerShape(20.dp),
          ),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            FeatureRow(
              icon = Icons.Filled.SportsKabaddi,
              title = stringResource(R.string.feature_combat_title),
              description = stringResource(R.string.feature_combat_desc),
            )
            FeatureRow(
              icon = Icons.Filled.AutoAwesome,
              title = stringResource(R.string.feature_loot_title),
              description = stringResource(R.string.feature_loot_desc),
            )
            FeatureRow(
              icon = Icons.Filled.Shield,
              title = stringResource(R.string.feature_rpg_title),
              description = stringResource(R.string.feature_rpg_desc),
            )
          }
        }

        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.auth_signing_in),
              style = MaterialTheme.typography.titleMedium,
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.Login,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = stringResource(R.string.auth_google_button),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        Text(
          text = stringResource(R.string.auth_footer_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun FeatureRow(
  icon: ImageVector,
  title: String,
  description: String,
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
