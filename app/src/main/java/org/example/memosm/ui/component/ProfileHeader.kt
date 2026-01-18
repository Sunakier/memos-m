import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import org.example.memosm.R
import org.example.memosm.model.User

@Composable
fun ProfileHeader(
    user: User?,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val token = user?.token
                    val imageRequest = remember<coil3.request.ImageRequest>(user?.avatarUrl, token) {
                        val headers = coil3.network.NetworkHeaders.Builder()
                            .apply {
                                if (!token.isNullOrEmpty()) {
                                    set("Authorization", "Bearer $token")
                                }
                            }
                            .build()

                        coil3.request.ImageRequest.Builder(context)
                            .data(user?.avatarUrl)
                            .httpHeaders(headers)
                            .listener(
                                onError = { _, result -> android.util.Log.e("MemosMessage", "ProfileHeader loading error: ${user?.avatarUrl}", result.throwable) },
                                onSuccess = { _, _ -> android.util.Log.d("MemosMessage", "ProfileHeader loading success: ${user?.avatarUrl}") }
                            )
                            .build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.profile_avatar_description),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.displayName ?: stringResource(R.string.memo_unknown_user),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!user?.username.isNullOrBlank()) "@${user.username}" else stringResource(
                                R.string.memo_unknown_user
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        user?.name?.let { name ->
                            val id = name.removePrefix("users/")
                            Text(
                                text = "${stringResource(R.string.profile_user_id)}: $id",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                if (!user?.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = user.description, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Edit button in top-right corner
            if (onEditClick != null) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.profile_edit_account),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
