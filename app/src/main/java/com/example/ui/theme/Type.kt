package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val CinzelFontFamily = FontFamily(
  Font(R.font.cinzel, FontWeight.Normal),
  Font(R.font.cinzel, FontWeight.Bold),
)

val PlusJakartaSansFontFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val EldoriaTypography = Typography(
  displaySmall = TextStyle(
    fontFamily = CinzelFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.5.sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = CinzelFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.25.sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = CinzelFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = CinzelFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  titleSmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 13.sp,
  ),
)
