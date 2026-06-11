package com.perpenda.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perpenda.data.repository.CompletionCache
import com.perpenda.data.repository.PathRepository
import com.perpenda.model.ReviewDue
import com.perpenda.model.UnitManifestEntry
import com.perpenda.ui.components.AppScreenScaffold
import com.perpenda.ui.components.PrimaryActionButton
import com.perpenda.ui.components.SectionHeader
import com.perpenda.ui.components.screenContentPadding
import com.perpenda.ui.theme.PerpendaTheme
import com.perpenda.viewmodel.PathHomeEvent
import com.perpenda.viewmodel.PathHomeUiState
import com.perpenda.viewmodel.PathHomeViewModel
import com.perpenda.viewmodel.UnitGateState

@Composable
fun PathHomeScreen(
    pathRepository: PathRepository,
    completionCache: CompletionCache,
    onOpenUnit: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAuthExpired: () -> Unit,
    onNotice: (String) -> Unit = {}
) {
    val viewModel: PathHomeViewModel = viewModel(
        factory = PathHomeViewModel.factory(pathRepository, completionCache)
    )

    // Drive every load from the lifecycle: each time this screen reaches
    // RESUMED — first composition, return from settings/auth_login,
    // return from the unit reader — re-fetch the path and sync the
    // user's completion state from the server. Single source of truth,
    // no flags, no `remember`/`rememberSaveable` guards. Cost: one extra
    // path GET + completions GET per resume. Acceptable for v1; aligns
    // with Google's "VM exposes state, UI drives effects" guidance for
    // Compose state production. See docs/guides/ANDROID_BEST_PRACTICES.md.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val state = viewModel.uiState

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PathHomeEvent.AuthExpired -> onAuthExpired()
                is PathHomeEvent.UnitCompleted -> onNotice(
                    when {
                        event.pathComplete -> "${event.completedTitle} complete — path complete!"
                        event.unlockedTitle != null ->
                            "${event.completedTitle} complete — ${event.unlockedTitle} unlocked"
                        else -> "${event.completedTitle} complete"
                    }
                )
            }
        }
    }

    AppScreenScaffold(
        title = "LLM Systems for PMs",
        subtitle = "Path home",
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    ) { contentPadding ->
        when (val current = state) {
            is PathHomeUiState.Loading -> LoadingBox(modifier = Modifier.screenContentPadding(contentPadding))
            is PathHomeUiState.Error -> ErrorBox(
                message = if (current.authExpired) "Sign in to continue." else current.message,
                onRetry = viewModel::load,
                modifier = Modifier.screenContentPadding(contentPadding)
            )
            is PathHomeUiState.Loaded -> LoadedBody(
                state = current,
                onOpenUnit = onOpenUnit,
                onReviewTap = { unitId ->
                    // F5: D6 — tapping a due review marks it reviewed
                    // (ladder advances, best-effort). D2 — a review is
                    // a re-surface, so reuse the existing unit route.
                    viewModel.markReviewed(unitId)
                    onOpenUnit(unitId)
                },
                modifier = Modifier.screenContentPadding(contentPadding)
            )
        }
    }
}

@Composable
private fun LoadedBody(
    state: PathHomeUiState.Loaded,
    onOpenUnit: (String) -> Unit,
    onReviewTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val units = state.path.units
        val completedCount = units.count { state.unitStates[it.id] == UnitGateState.DONE }
        val listState = rememberLazyListState()

        // By unit 10+ the current card sits below the fold; position the
        // list on it so the user never scrolls to find their place. Keyed
        // on the current unit id: runs on entry and again only when a
        // completion moves the frontier — not on every silent refresh.
        val currentUnitId = state.nextUnit?.id
        LaunchedEffect(currentUnitId) {
            val index = units.indexOfFirst { it.id == currentUnitId }
            // +1 for the "Units" header item; scroll only when the target
            // is beyond the first few rows, so early units don't jump.
            if (index > 1) listState.animateScrollToItem(index + 1)
        }

        // The path is the scrolling surface; only the Continue button is
        // pinned below. No intro description: the app store listing and the
        // app-bar title already establish what this is, and the stepper makes
        // the sequence self-evident — a re-pitch here is redundant on a screen
        // the user sees every session.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
        ) {
            item(key = "units-header") {
                SectionHeader(title = "Units · $completedCount of ${units.size}")
            }

            itemsIndexed(items = units, key = { _, unit -> unit.id }) { index, unit ->
                val gate = state.unitStates[unit.id] ?: UnitGateState.AVAILABLE
                // Block: avoids the `else { ... }` block-vs-lambda ambiguity.
                val onOpen: (() -> Unit)? =
                    if (gate == UnitGateState.LOCKED) null else ({ onOpenUnit(unit.id) })
                PathNodeRow(
                    unit = unit,
                    state = gate,
                    isFirst = index == 0,
                    isLast = index == units.lastIndex,
                    // The connecting line fills in "behind" completed nodes.
                    topTraveled = index > 0 &&
                        state.unitStates[units[index - 1].id] == UnitGateState.DONE,
                    bottomTraveled = gate == UnitGateState.DONE,
                    onClick = onOpen
                )
            }

            // F5 / D4: reviews due surface ALONGSIDE the path — optional,
            // never a gate. Rendered only when non-empty, so a learner with
            // nothing due (or a failed best-effort fetch) sees no section.
            if (state.reviewsDue.isNotEmpty()) {
                item(key = "reviews-header") {
                    SectionHeader(title = "Reviews due")
                }
                item(key = "reviews") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.reviewsDue.forEach { review ->
                            ReviewRow(
                                review = review,
                                onClick = { onReviewTap(review.unitId) }
                            )
                        }
                    }
                }
            }
        }

        val nextUnit = state.nextUnit
        when {
            nextUnit != null -> PrimaryActionButton(
                text = "Continue · ${nextUnit.title}",
                onClick = { onOpenUnit(nextUnit.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            state.pathComplete -> Text(
                text = "Path complete. Phase 2 (the grader) opens next.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            // No unit is currently unlocked but the path isn't complete —
            // a blocked state (e.g. inconsistent prereq data). Don't show
            // completion; surface that there's nothing to continue to.
            else -> Text(
                text = "No units are unlocked right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * One unit as a node on the connected path: a left rail (the
 * connecting line + a numbered/checked/locked node) and a content card.
 * A LOCKED unit passes `onClick = null` and renders muted + non-tappable;
 * every other state is openable (a DONE unit stays open for review).
 */
@Composable
private fun PathNodeRow(
    unit: UnitManifestEntry,
    state: UnitGateState,
    isFirst: Boolean,
    isLast: Boolean,
    topTraveled: Boolean,
    bottomTraveled: Boolean,
    onClick: (() -> Unit)?
) {
    val locked = state == UnitGateState.LOCKED
    val traveledColor = MaterialTheme.colorScheme.primary
    val untraveledColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(48.dp)) {
                val cx = size.width / 2f
                val strokePx = 3.dp.toPx()
                if (!isFirst) {
                    drawLine(
                        color = if (topTraveled) traveledColor else untraveledColor,
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height / 2f),
                        strokeWidth = strokePx
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = if (bottomTraveled) traveledColor else untraveledColor,
                        start = Offset(cx, size.height / 2f),
                        end = Offset(cx, size.height),
                        strokeWidth = strokePx
                    )
                }
            }
            NodeBadge(state = state, position = unit.position)
        }

        val callback = onClick
        val cardModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (callback != null) Modifier.clickable { callback() } else Modifier
            )
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(
                1.dp,
                if (locked) PerpendaTheme.colors.hairlineSub else PerpendaTheme.colors.hairline
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (locked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = when (state) {
                            UnitGateState.LOCKED -> "Locked · finish earlier units first"
                            UnitGateState.DONE -> "Unit ${unit.position} · completed"
                            UnitGateState.CURRENT -> "Unit ${unit.position} · in progress"
                            UnitGateState.AVAILABLE -> "Unit ${unit.position} · ${unit.status}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // No trailing icon when locked — the node's lock glyph is
                // the single indicator (avoids the double-lock). The CURRENT
                // card also drops it: the pinned Continue button below the
                // list is the single "act" affordance for the current unit,
                // so its card reads as status ("in progress"), not as a
                // second play control. The card itself stays tappable.
                if (!locked && state != UnitGateState.CURRENT) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The circular node on the rail: checked (done), numbered (current/available), or locked. */
@Composable
private fun NodeBadge(state: UnitGateState, position: Int) {
    val diameter = 34.dp
    when (state) {
        UnitGateState.DONE -> Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        UnitGateState.CURRENT -> Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        UnitGateState.AVAILABLE -> Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        UnitGateState.LOCKED -> Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ReviewRow(
    review: ReviewDue,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, PerpendaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Spaced review",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Spaced review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Open review"
            )
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        PrimaryActionButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
