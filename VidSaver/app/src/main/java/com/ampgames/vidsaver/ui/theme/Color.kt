package com.ampgames.vidsaver.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VidSaver's palette: one hot red against warm-free charcoal.
 *
 * The app is dark by design. A downloader lives next to video, and video looks
 * best on a dark ground; the red is the only colour on screen so every red
 * element reads as "the action". The greys are neutral on purpose — Material's
 * defaults carry a blue tint that fights a red accent.
 */

// --- Accent ------------------------------------------------------------------
internal val BrandPrimary = Color(0xFFD9322B)
internal val BrandOnPrimary = Color(0xFFFFFFFF)
internal val BrandPrimaryContainer = Color(0xFFFFDAD6)
internal val BrandOnPrimaryContainer = Color(0xFF410002)

/** Home-screen accent for the premium crown: gold, so it is not another red. */
internal val BrandGold = Color(0xFFF2A93B)

// --- Light: neutral greys, near-white ground ---------------------------------
internal val LightBackground = Color(0xFFFFFFFF)
internal val LightSurface = Color(0xFFFFFFFF)

/** Cards, chips, the search field — a whisper off the background. */
internal val LightSurfaceVariant = Color(0xFFF1F3F5)
internal val LightOnSurface = Color(0xFF16191C)
internal val LightOnSurfaceVariant = Color(0xFF5C636A)
internal val LightOutline = Color(0xFFCED4DA)
internal val LightOutlineVariant = Color(0xFFE9ECEF)
internal val LightSecondary = Color(0xFF5C636A)
internal val LightSecondaryContainer = Color(0xFFE9ECEF)
internal val LightOnSecondaryContainer = Color(0xFF2B3035)

// --- Dark: charcoal, not pure black ------------------------------------------
internal val DarkPrimary = Color(0xFFE5433A)
internal val DarkOnPrimary = Color(0xFFFFFFFF)
internal val DarkPrimaryContainer = Color(0xFF4A1F1D)
internal val DarkOnPrimaryContainer = Color(0xFFFFDAD6)

internal val DarkBackground = Color(0xFF1A1A1C)
internal val DarkSurface = Color(0xFF1A1A1C)

/** Pills, chips, the address field: one step lighter than the ground. */
internal val DarkSurfaceVariant = Color(0xFF2B2B2E)
internal val DarkOnSurface = Color(0xFFEDEDEE)
internal val DarkOnSurfaceVariant = Color(0xFFB0B0B5)
internal val DarkOutline = Color(0xFF4A4A4F)
internal val DarkOutlineVariant = Color(0xFF323235)
internal val DarkSecondary = Color(0xFFB0B0B5)
internal val DarkSecondaryContainer = Color(0xFF323235)
internal val DarkOnSecondaryContainer = Color(0xFFEDEDEE)

// --- Shared ------------------------------------------------------------------
internal val BrandError = Color(0xFFBA1A1A)
internal val BrandOnError = Color(0xFFFFFFFF)
internal val BrandErrorContainer = Color(0xFFFFDAD6)
internal val BrandOnErrorContainer = Color(0xFF410002)

/**
 * Monogram tints for the home-screen site tiles.
 *
 * Deliberately muted rather than each site's real brand colour: seven saturated
 * logos on one screen is a ransom note, and these have to sit under a single
 * red accent without competing with it.
 */
internal val SiteTints: List<Color> = listOf(
    Color(0xFF3B6FE0),
    Color(0xFFC2557A),
    Color(0xFF4D9E8F),
    Color(0xFFCC7A3D),
    Color(0xFF7A5FC4),
    Color(0xFF4A8FCC),
    Color(0xFF5C9E4D),
)

/** Stable tint for a domain, so a site keeps the same colour between launches. */
internal fun tintForDomain(domain: String): Color =
    SiteTints[(domain.hashCode().mod(SiteTints.size))]
