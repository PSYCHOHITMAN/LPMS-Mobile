package com.pointblank.lpms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.ui.theme.*

@Composable
fun LpmsLogo(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).background(Brand, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("LP", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("MS", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Brand)
        }
        Text("for READ Educational Trust", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun LpmsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun MetricCard(number: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(number, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = Brand, modifier = Modifier.size(16.dp))
                Text(label, fontSize = 13.sp, color = Muted)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Brand, modifier = Modifier.size(20.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun FeedItem(icon: ImageVector, text: String, time: String, tint: Color = Brand) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
        Text(time, fontSize = 11.sp, color = Muted)
    }
    HorizontalDivider(color = Line)
}

@Composable
fun ProgressRow(label: String, fraction: Float, color: Color = Brand) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color, trackColor = Track, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun RoleBadge(role: Role) {
    val (bg, fg) = when (role) {
        Role.LEARNER -> BlueTint to Blue
        Role.TEACHER -> PurpleTint to Purple
        Role.COORDINATOR -> BrandTint to BrandDark
        Role.ADMIN -> TealTint to TealDark
        Role.PARENT -> Color(0xFFFFF1E3) to Color(0xFF9A5B1A)
    }
    Text(role.name.lowercase().replaceFirstChar { it.uppercase() }, color = fg, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun ErrorBanner(message: String) {
    Text(message, color = BrandDark, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(BrandTint, RoundedCornerShape(12.dp)).padding(12.dp))
}

@Composable
fun InfoBanner(message: String) {
    Text(message, color = TealDark, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(TealTint, RoundedCornerShape(12.dp)).padding(12.dp))
}

@Composable
fun OfflineBanner() {
    Text(
        "You're offline. You can keep working; what you save is kept on this phone and uploads when you're back online.",
        color = Color(0xFF7A4B00), fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF1D6)).padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Grade R, 1, 2, 3 ... as tappable chips, so a grade can never be typed wrongly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradeChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.pointblank.lpms.core.util.Grades.choices.forEach { g ->
            FilterChip(
                selected = selected == g, onClick = { onSelect(g) }, label = { Text(g) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandTint, selectedLabelColor = BrandDark)
            )
        }
    }
}
