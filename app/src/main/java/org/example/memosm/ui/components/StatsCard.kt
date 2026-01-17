import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.memosm.R
import org.example.memosm.model.UserStats
import org.example.memosm.ui.nav.StatItem

@Composable
fun StatsCard(stats: UserStats?) {
    val notAvailable = stringResource(R.string.common_not_available)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.profile_statistics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // First Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = stringResource(R.string.profile_stats_memos),
                    value = stats?.totalMemoCount?.toString() ?: notAvailable,
                    icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_tags),
                    value = stats?.tagCount?.size?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Tag,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_pinned),
                    value = stats?.pinnedMemos?.size?.toString() ?: notAvailable,
                    icon = Icons.Outlined.PushPin,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Second Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = stringResource(R.string.profile_stats_links),
                    value = stats?.memoTypeStats?.linkCount?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_code),
                    value = stats?.memoTypeStats?.codeCount?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Code,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_todo),
                    value = stats?.memoTypeStats?.todoCount?.toString() ?: notAvailable,
                    icon = Icons.Outlined.TaskAlt,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}