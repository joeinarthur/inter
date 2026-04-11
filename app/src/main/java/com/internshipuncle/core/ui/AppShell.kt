package com.internshipuncle.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internshipuncle.core.design.CoolGray
import com.internshipuncle.core.design.DeepNavy
import com.internshipuncle.core.design.FrostWhite
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.NavPillDark
import com.internshipuncle.core.design.PaleBlue
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RoyalBlue
import com.internshipuncle.core.design.SkyBlueLight
import com.internshipuncle.core.design.SkyBlueMedium

data class TopLevelDestination(
    val label: String,
    val route: String,
    val icon: ImageVector? = null
)

@Composable
fun AppShell(
    title: String,
    showBottomBar: Boolean,
    destinations: List<TopLevelDestination>,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SkyBlueLight, SkyBlueMedium)
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {},
            bottomBar = {
                if (showBottomBar) {
                    AppBottomBar(
                        destinations = destinations,
                        selectedRoute = selectedRoute,
                        onDestinationSelected = onDestinationSelected
                    )
                }
            },
            content = content
        )
    }
}

@Composable
private fun AppBottomBar(
    destinations: List<TopLevelDestination>,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = PureWhite.copy(alpha = 0.90f),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val isSelected = destination.route == selectedRoute
                    AppNavItem(
                        icon = destination.icon,
                        isSelected = isSelected,
                        onClick = { onDestinationSelected(destination.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavItem(
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) RoyalBlue else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) PureWhite else CoolGray,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navItemContent"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        }
    }
}

// ── Shared UI Components ──────────────────────────────────────────────

@Composable
fun PlaceholderScreen(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    sections: List<Pair<String, String>> = emptyList(),
    actions: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = InternshipUncleTheme.spacing.medium, vertical = InternshipUncleTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.mediumLarge)
    ) {
        // Hero card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = PureWhite.copy(alpha = 0.85f),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sections.isNotEmpty()) {
            sections.forEach { (sectionTitle, sectionBody) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = PureWhite.copy(alpha = 0.8f),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sectionBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }

        actions()
    }
}
