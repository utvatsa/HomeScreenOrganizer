package com.homestudio.local.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homestudio.local.BuildConfig
import kotlinx.coroutines.delay

class TutorialTargetRegistry {
    val targets = mutableStateMapOf<String, Rect>()
}

fun Modifier.tutorialTarget(registry: TutorialTargetRegistry?, key: String): Modifier {
    if (registry == null) return this
    return this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            registry.targets[key] = coords.boundsInRoot()
        }
    }
}

data class TutorialStepSpec(
    val id: String,
    val dest: OrganizerDest,
    val targetKey: String,
    val category: String,
    val title: String,
    val description: String,
    val scrollToBottom: Boolean = false,
)

val VERBOSE_TUTORIAL_STEPS: List<TutorialStepSpec> = listOf(
    TutorialStepSpec(
        id = "dock_nav",
        dest = OrganizerDest.STUDIO,
        targetKey = "dock_root",
        category = "Navigation • One UI 9 Dock",
        title = "Floating Expressive Dock",
        description = "Switch effortlessly between your three main workspaces: Studio (plan & customize home pages), Current (inspect your exact live Samsung One UI layout), and Catalog (categorize every installed app). Tap Next to tour the entire app safely without changing your setup.",
    ),
    TutorialStepSpec(
        id = "hamburger_drawer",
        dest = OrganizerDest.STUDIO,
        targetKey = "hamburger_btn",
        category = "Menu • Hamburger Drawer",
        title = "Settings, Setup Checklist, Guide & Replay",
        description = "Tap the circular ≡ button anytime to open the navigation drawer. Inside you'll find Settings & Header Theme (Light / Dark / System mode + typography), Setup & Companion App Readiness, the User Guide, Interactive Tutorial replay, and About (utvatsa/HomeScreenOrganizer).",
    ),
    TutorialStepSpec(
        id = "presets_onehand",
        dest = OrganizerDest.STUDIO,
        targetKey = "studio_capacity",
        category = "Studio • One-Hand Grid Rules",
        title = "One-Hand Item Cap & First-Use Presets",
        description = "Choose how many items fit per page (default 8 so icons stay within bottom one-hand reach and top headers remain unobstructed). Use Presets above to generate Brief (3 layouts), Balanced (6 layouts), or Detailed (8–12+ all-apps layouts) built strictly from apps installed on this phone.",
    ),
    TutorialStepSpec(
        id = "suggested_custom",
        dest = OrganizerDest.STUDIO,
        targetKey = "studio_builders",
        category = "Studio • Suites & Folder Tools",
        title = "+ Suggested Suites & + Custom Builder",
        description = "Tap + Suggested (such as Payments Suite or Banking Suites) to review detected installed apps & auto-grouped folders: check/uncheck any app or folder, rename folders inline, toggle apps inside folders, or Break Down Folders into solo icons. Tap + Custom to build any layout from scratch.",
    ),
    TutorialStepSpec(
        id = "viewer_wallpaper",
        dest = OrganizerDest.STUDIO,
        targetKey = "viewer_controls",
        category = "Preview • Orientation & Wallpaper",
        title = "Vertical Stack / Horizontal Pages & Wallpaper",
        description = "Switch freely between Vertical Stack (all pages scrolling down) and Horizontal Pages (swipeable One UI carousel with page dots). Toggle Wallpaper: On/Off to preview headers cleanly, or tap Choose Wallpaper to update ONLY your unlocked Home Screen wallpaper (lock screen stays untouched).",
        scrollToBottom = true,
    ),
    TutorialStepSpec(
        id = "current_live",
        dest = OrganizerDest.CURRENT,
        targetKey = "current_summary",
        category = "Current Tab • Live Phone Snapshot",
        title = "View Exact Current Phone Pages & Folders",
        description = "Current shows the exact synced live layout of your phone — including transparent Category Header widgets, 2x2 mini-mosaic folders, and Default Home badge. Tap any folder icon in the preview to inspect its apps, toggle wallpaper backdrops, or tap Load Into Studio to clone it for editing.",
    ),
    TutorialStepSpec(
        id = "catalog_tagging",
        dest = OrganizerDest.CATALOG,
        targetKey = "catalog_search",
        category = "Catalog • Custom App Recognition",
        title = "Search & Tag Any Installed App",
        description = "Catalog lists every launchable app installed on your phone (never uninstalled suggestions). Search by name or package, filter by User/Custom/Unmapped, and tap Tag on any work or regional app to assign custom categories and a Suite Folder so presets place it automatically.",
    ),
    TutorialStepSpec(
        id = "theme_and_setup",
        dest = OrganizerDest.SETTINGS,
        targetKey = "theme_pill",
        category = "Appearance • Done",
        title = "Light / Dark Mode & Global Header Styling",
        description = "Tap the top-right Auto / Light / Dark pill—or adjust options right here in Settings—to match PennyWise-inspired AMOLED Dark or Crisp Light mode, customize header font size/opacity/weight, and open Setup to check Samsung Good Lock & Home Up readiness. Tap Finish Tutorial to begin!",
    ),
)

/**
 * Clean zero-flash animated Splash overlay shown briefly every time the app launches.
 */
@Composable
fun BrandSplashOverlay(
    visible: Boolean,
    iconResId: Int = com.homestudio.local.R.mipmap.ic_launcher_warm_paper,
) {
    val p = LocalStudioPalette.current
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            animateIn = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(300, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(p.background)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(tween(340)) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    AppBrandIconBadge(size = 88.dp, iconResId = iconResId)
                    Text(
                        text = "HomeScreenOrganizer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = p.textPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = p.cardElevated,
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Text(
                            text = "One UI Home Studio • v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = p.secondaryAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive non-destructive Spotlight Coachmark Walkthrough Overlay.
 * Locks underlying UI interaction while highlighting live screens step-by-step.
 */
@Composable
fun TutorialCoachmarkOverlay(
    stepIndex: Int,
    steps: List<TutorialStepSpec>,
    registry: TutorialTargetRegistry,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val p = LocalStudioPalette.current
    val density = LocalDensity.current
    val step = steps[stepIndex.coerceIn(steps.indices)]
    val progress = (stepIndex + 1).toFloat() / steps.size.toFloat()

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Intercept ALL taps/drags underneath the coachmark card so live screens cannot be edited!
            .pointerInput(Unit) {
                detectTapGestures { /* Read-only walkthrough lock */ }
            },
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val padPx = with(density) { 8.dp.toPx() }
        val cornerPx = with(density) { 18.dp.toPx() }

        val fallbackRect = remember(screenW, screenH) {
            Rect(
                left = screenW * 0.08f,
                top = screenH * 0.16f,
                right = screenW * 0.92f,
                bottom = screenH * 0.34f,
            )
        }
        val rawTarget = registry.targets[step.targetKey]
        val paddedTarget = if (rawTarget != null && rawTarget.width > 4f && rawTarget.height > 4f) {
            Rect(
                left = (rawTarget.left - padPx).coerceAtLeast(0f),
                top = (rawTarget.top - padPx).coerceAtLeast(0f),
                right = (rawTarget.right + padPx).coerceAtMost(screenW),
                bottom = (rawTarget.bottom + padPx).coerceAtMost(screenH),
            )
        } else {
            fallbackRect
        }

        val spotlight by animateRectAsState(
            targetValue = paddedTarget,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
            label = "spotlight",
        )

        // Dimmed backdrop + illuminated clear spotlight hole
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(spotlight.left, spotlight.top),
                size = Size(spotlight.width, spotlight.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = p.accent.copy(alpha = pulseAlpha),
                topLeft = Offset(spotlight.left, spotlight.top),
                size = Size(spotlight.width, spotlight.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        val placeCardAbove = spotlight.center.y > screenH * 0.50f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(if (placeCardAbove) Alignment.TopCenter else Alignment.BottomCenter)
                    .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 360f)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = p.card),
                border = BorderStroke(1.5.dp, p.accent.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                            color = p.accentSoft,
                        ) {
                            Text(
                                text = "Step ${stepIndex + 1}/${steps.size} • ${step.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = p.accent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = p.cardElevated,
                        ) {
                            Text(
                                text = "Read-Only",
                                style = MaterialTheme.typography.labelSmall,
                                color = p.textMuted,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = p.accent,
                        trackColor = p.border,
                    )

                    AnimatedContent(
                        targetState = step,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "coachmarkText",
                    ) { current ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = p.textPrimary,
                            )
                            Text(
                                text = current.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textSecondary,
                                lineHeight = 19.sp,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onSkip) {
                            Text("Skip Tutorial", color = p.textMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (stepIndex > 0) {
                                OutlinedButton(onClick = onPrevious) {
                                    Text("Previous")
                                }
                            }
                            Button(onClick = onNext) {
                                Text(if (stepIndex == steps.lastIndex) "Finish" else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}
