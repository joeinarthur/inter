package com.internshipuncle.feature_more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment.Companion.CenterVertically
import com.internshipuncle.core.design.CanvasWhite
import com.internshipuncle.core.design.CharcoalDark
import com.internshipuncle.core.design.DividerGray
import com.internshipuncle.core.design.GreenPositive
import com.internshipuncle.core.design.InkBlack
import com.internshipuncle.core.design.PureWhite
import com.internshipuncle.core.design.RedNegative
import com.internshipuncle.core.design.SlateGray
import com.internshipuncle.core.design.SurfaceGray

@Composable
fun MoreScreen(
    onOpenResumeMatcher: () -> Unit,
    onOpenMockInterview: () -> Unit,
    onOpenCommunityFeed: () -> Unit,
    onOpenJdIntelligence: () -> Unit,
    onOpenRealityCheck: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenReferrals: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
            .verticalScroll(rememberScrollState())
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "MORE",
                style = MaterialTheme.typography.displaySmall,
                color = InkBlack,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp
            )

            FeaturedMatcherCard(onClick = onOpenResumeMatcher)

            FeatureMenuCard(
                icon = Icons.Outlined.RecordVoiceOver,
                title = "Mock Interview",
                subtitle = "Practice with Uncle - get grilled and improve",
                onClick = onOpenMockInterview
            )

            FeatureMenuCard(
                icon = Icons.Outlined.Groups,
                title = "Community Feed",
                subtitle = "Tips, questions, and wins from the network",
                onClick = onOpenCommunityFeed
            )

            FeatureMenuCard(
                icon = Icons.Outlined.Search,
                title = "JD Intelligence",
                subtitle = "Decode any job description",
                onClick = onOpenJdIntelligence
            )

            FeatureMenuCard(
                icon = Icons.Outlined.TrackChanges,
                title = "Reality Check",
                subtitle = "Your honest shortlist probability",
                onClick = onOpenRealityCheck
            )

            FeatureMenuCard(
                icon = Icons.Outlined.BarChart,
                title = "Progress",
                subtitle = "Track your journey",
                onClick = onOpenProgress
            )

            FeatureMenuCard(
                icon = Icons.Outlined.PersonAddAlt,
                title = "Referrals",
                subtitle = "Bring friends, earn XP",
                onClick = onOpenReferrals
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeaturedMatcherCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PureWhite,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(InkBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Analytics,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(26.dp)
                )
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Resume Matcher",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkBlack,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Compare your resume against any role and see what to fix first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SlateGray
            )
        }
    }
}

@Composable
private fun FeatureMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PureWhite,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PureWhite,
                    modifier = Modifier.size(30.dp)
                )
            }

            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkBlack,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CommunityFeedScreen() {
    val feedFilters = listOf("All", "Tips", "Wins", "Questions")
    var selectedFilter by remember { mutableStateOf("All") }
    val posts = remember {
        listOf(
            CommunityPost(
                author = "Aanya",
                role = "SDE Intern",
                time = "12m ago",
                label = "Win",
                title = "Got a callback after cleaning my resume bullets",
                body = "I trimmed each bullet to one action, one metric, one result. The matcher score jumped and the recruiter reply came the same day.",
                likes = 24,
                replies = 5
            ),
            CommunityPost(
                author = "Rahul",
                role = "Frontend Trainee",
                time = "1h ago",
                label = "Tip",
                title = "Use the JD matcher before every application",
                body = "I paste the job description, note the missing skills, and add one project line that proves the gap is covered.",
                likes = 41,
                replies = 9
            ),
            CommunityPost(
                author = "Meera",
                role = "Campus Recruit",
                time = "3h ago",
                label = "Question",
                title = "How do you handle resumes with no internship experience?",
                body = "I’m seeing strong projects but not enough outcomes. What format is working best for freshers right now?",
                likes = 13,
                replies = 11
            )
        )
    }

    val filteredPosts = remember(selectedFilter) {
        if (selectedFilter == "All") posts else posts.filter { it.label.equals(selectedFilter, ignoreCase = true) }
    }

    FeaturePageScaffold(
        eyebrow = "COMMUNITY",
        title = "Feed",
        description = "See what other students are learning, shipping, and fixing right now."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            feedFilters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InkBlack,
                        selectedLabelColor = PureWhite,
                        containerColor = SurfaceGray,
                        labelColor = InkBlack
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (selectedFilter == filter) InkBlack else DividerGray
                        )
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        filteredPosts.forEach { post ->
            CommunityPostCard(post = post)
        }
    }
}

@Composable
fun RealityCheckScreen() {
    val signals = listOf(
        "Resume uploaded",
        "JD matched",
        "Mock interview done"
    )
    val gaps = listOf(
        "No quantified bullet points",
        "Missing one backend project",
        "Weak company-specific tailoring"
    )

    FeaturePageScaffold(
        eyebrow = "REALITY",
        title = "Check",
        description = "A direct view of how your prep stack looks to a recruiter."
    ) {
        ScoreCard(
            score = 68,
            label = "Shortlist probability",
            accent = GreenPositive,
            body = "You are in a workable range. A stronger resume match and one more mock session would move this into the safer band."
        )

        TwoColumnStats(
            leftTitle = "Strengths",
            leftItems = signals,
            rightTitle = "Gaps",
            rightItems = gaps,
            rightAccent = RedNegative
        )

        SectionCard(title = "Next moves") {
            BulletList(
                items = listOf(
                    "Open Resume Matcher and raise the JD fit score.",
                    "Tighten the top 3 bullets with metrics.",
                    "Take one mock interview and review the follow-up notes."
                )
            )
        }
    }
}

@Composable
fun ProgressScreen() {
    FeaturePageScaffold(
        eyebrow = "PROGRESS",
        title = "Track your journey",
        description = "A simple view of how much of the prep loop you have already closed."
    ) {
        ScoreCard(
            score = 74,
            label = "Weekly consistency",
            accent = InkBlack,
            body = "You have momentum. Keep the rhythm with one resume tweak, one JD review, and one practice session every week."
        )

        SectionCard(title = "Milestones") {
            MilestoneRow(title = "Resume uploaded", value = "Done", filled = true)
            MilestoneRow(title = "JD reviews this week", value = "3", filled = true)
            MilestoneRow(title = "Mock interviews completed", value = "2", filled = true)
            MilestoneRow(title = "Referral invites sent", value = "1", filled = false)
        }

        SectionCard(title = "Journey ring") {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressLine(label = "Resume", percent = 0.84f)
                ProgressLine(label = "Jobs", percent = 0.68f)
                ProgressLine(label = "Interview", percent = 0.52f)
            }
        }
    }
}

@Composable
fun ReferralsScreen() {
    var invitesSent by remember { mutableStateOf(2) }
    var xp by remember { mutableStateOf(140) }

    FeaturePageScaffold(
        eyebrow = "REFERRALS",
        title = "Bring friends, earn XP",
        description = "Invite classmates, grow the circle, and unlock more coaching rewards."
    ) {
        ScoreCard(
            score = 2,
            label = "Invites sent",
            accent = CharcoalDark,
            body = "Every accepted invite adds XP and helps your group see stronger prep patterns."
        )

        SectionCard(title = "Your referral code") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceGray)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = CenterVertically
            ) {
                Column {
                    Text("IU-4829", style = MaterialTheme.typography.titleLarge, color = InkBlack, fontWeight = FontWeight.Bold)
                    Text("Share this code with friends", style = MaterialTheme.typography.bodyMedium, color = SlateGray)
                }

                Button(
                    onClick = {
                        invitesSent += 1
                        xp += 25
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Share")
                }
            }
        }

        SectionCard(title = "Rewards") {
            BulletList(
                items = listOf(
                    "25 XP for each accepted invite.",
                    "Unlock a resume audit after 3 invites.",
                    "Get a mock interview boost after 5 invites."
                )
            )
        }

        SectionCard(title = "Status") {
            MilestoneRow(title = "Invites sent", value = invitesSent.toString(), filled = true)
            MilestoneRow(title = "XP earned", value = xp.toString(), filled = true)
            MilestoneRow(title = "Next reward", value = "Resume audit", filled = false)
        }
    }
}

@Composable
private fun CommunityPostCard(post: CommunityPost) {
    var saved by remember { mutableStateOf(false) }
    var boosted by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PureWhite,
        shadowElevation = 4.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CharcoalDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.author.take(1).uppercase(),
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(post.author, style = MaterialTheme.typography.titleMedium, color = InkBlack, fontWeight = FontWeight.Bold)
                    Text("${post.role}  ·  ${post.time}", style = MaterialTheme.typography.bodySmall, color = SlateGray)
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SurfaceGray
                ) {
                    Text(
                        text = post.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkBlack,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(post.title, style = MaterialTheme.typography.titleLarge, color = InkBlack, fontWeight = FontWeight.Bold)
            Text(post.body, style = MaterialTheme.typography.bodyMedium, color = SlateGray)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionPill(
                    label = if (boosted) "Boosted" else "Helpful",
                    icon = Icons.Outlined.TrendingUp,
                    accent = if (boosted) GreenPositive else SlateGray,
                    onClick = { boosted = !boosted }
                )
                ActionPill(
                    label = if (saved) "Saved" else "Save",
                    icon = Icons.Outlined.Article,
                    accent = if (saved) InkBlack else SlateGray,
                    onClick = { saved = !saved }
                )
            }

            HorizontalDivider(color = DividerGray)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${post.likes} likes", style = MaterialTheme.typography.bodySmall, color = SlateGray)
                Text("${post.replies} replies", style = MaterialTheme.typography.bodySmall, color = SlateGray)
            }
        }
    }
}

@Composable
private fun FeaturePageScaffold(
    eyebrow: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = SlateGray,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = InkBlack,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateGray
            )
        }

        content()

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ScoreCard(
    score: Int,
    label: String,
    accent: Color,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PureWhite,
        shadowElevation = 4.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = SlateGray, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$score",
                        color = PureWhite,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkBlack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TwoColumnStats(
    leftTitle: String,
    leftItems: List<String>,
    rightTitle: String,
    rightItems: List<String>,
    rightAccent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(
            modifier = Modifier.weight(1f),
            title = leftTitle
        ) {
            BulletList(items = leftItems)
        }

        SectionCard(
            modifier = Modifier.weight(1f),
            title = rightTitle
        ) {
            BulletList(items = rightItems, accent = rightAccent)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PureWhite,
        shadowElevation = 3.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = InkBlack, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun BulletList(
    items: List<String>,
    accent: Color = GreenPositive
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    title: String,
    value: String,
    filled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (filled) Color(0xFFEAF7EF) else SurfaceGray,
            border = BorderStroke(1.dp, if (filled) GreenPositive.copy(alpha = 0.4f) else DividerGray)
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (filled) GreenPositive else SlateGray,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    percent: Float
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = InkBlack, fontWeight = FontWeight.Medium)
            Text("${(percent * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = SlateGray)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(CharcoalDark)
            )
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SurfaceGray,
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkBlack, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class CommunityPost(
    val author: String,
    val role: String,
    val time: String,
    val label: String,
    val title: String,
    val body: String,
    val likes: Int,
    val replies: Int
)
