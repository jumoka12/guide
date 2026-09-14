package com.ampgames.vidsaver.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VidSaver's own palette: one confident blue against true neutral greys.
 *
 * Material 3's default greys carry a blue tint, which fights a blue accent and
 * makes everything look faintly cold. These are neutral on purpose, so the
 * accent is the only colour on screen and content (video thumbnails) stays the
 * loudest thing.
 */

// --- Accent ------------------------------------------------------------------
internal val BrandPrimary = Color(0xFF2457D6)
internal val BrandOnPrimary = Color(0xFFFFFFFF)
internal val BrandPrimaryContainer = Color(0xFFE6EDFD)
internal val BrandOnPrimaryContainer = Color(0xFF0C2B7A)

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

// --- Dark: warm-free near-black, not pure black ------------------------------
internal val DarkPrimary = Color(0xFFAEC4FF)
internal val DarkOnPrimary = Color(0xFF002A78)
internal val DarkPrimaryContainer = Color(0xFF17398F)
internal val DarkOnPrimaryContainer = Color(0xFFDCE4FF)

internal val DarkBackground = Color(0xFF121416)
internal val DarkSurface = Color(0xFF121416)
internal val DarkSurfaceVariant = Color(0xFF282C30)
internal val DarkOnSurface = Color(0xFFE6E8EA)
internal val DarkOnSurfaceVariant = Color(0xFFB4BBC2)
internal val DarkOutline = Color(0xFF3C4147)
internal val DarkOutlineVariant = Color(0xFF2A2E33)
internal val DarkSecondary = Color(0xFFB4BBC2)
internal val DarkSecondaryContainer = Color(0xFF2E3338)
internal val DarkOnSecondaryContainer = Color(0xFFE6E8EA)

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
 * blue accent without competing with it.
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
