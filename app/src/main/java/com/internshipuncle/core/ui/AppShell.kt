package com.internshipuncle.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internshipuncle.R
import com.internshipuncle.core.design.CanvasWhite
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.ErrorRed
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.InternshipUncleTheme
import com.internshipuncle.core.design.NavIconWhite
import com.internshipuncle.core.design.NavSelectedBg
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.SurfaceGray

data class TopLevelDestination(
    val label: String,
    val route: String,
    val icon: ImageVector? = null
)

@Composable
fun AppShell(
    title: String,
    showTopBar: Boolean,
    showBottomBar: Boolean,
    destinations: List<TopLevelDestination>,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = { if (showTopBar) AppTopBar() },
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
private fun AppTopBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PureWhite,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.favicon_removebg_preview),
                    contentDescription = "Internship Uncle logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Text(
                    text = "INTERNSHIP UNCLE",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkBlack,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F3F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = "Profile",
                    tint = CharcoalDark,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = PureWhite,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val isSelected = destination.route == selectedRoute
                    AppNavItem(
                        icon = destination.icon,
                        label = destination.label,
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
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) NavSelectedBg else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) ErrorRed else NavIconWhite,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navScale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    tint = iconTint
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) ErrorRed else SlateGray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
            .background(CanvasWhite)
            .padding(
                horizontal = InternshipUncleTheme.spacing.medium,
                vertical = InternshipUncleTheme.spacing.large
            ),
        verticalArrangement = Arrangement.spacedBy(InternshipUncleTheme.spacing.medium)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceGray,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    color = SlateGray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkBlack
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray
                )
            }
        }

        if (sections.isNotEmpty()) {
            sections.forEach { (sectionTitle, sectionBody) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceGray,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = InkBlack,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sectionBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateGray,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }

        actions()
    }
}
