package com.mengxing.agh

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 顶栏下的细条：显示当前工作区目录，点击切换。 */
@Composable
fun WorkspaceBar(activeCwd: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val name = dirName(activeCwd)
    Surface(color = cs.background) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp)).clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.FolderOpen, null, tint = cs.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (name.isEmpty()) "选择工作区" else name,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("切换 ›", style = MaterialTheme.typography.labelSmall, color = cs.primary)
        }
    }
}

private fun dirName(path: String): String {
    if (path.isEmpty()) return ""
    val p = path.trimEnd('/')
    val i = p.lastIndexOf('/')
    return if (i >= 0) p.substring(i + 1) else p
}

/** 工作区选择面板。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSheet(
    state: ChatUiState,
    onPick: (AcpClient.Workspace) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("选择工作区", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = cs.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("新会话将在所选目录下工作", style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (state.workspaces.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无工作区", color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(state.workspaces, key = { it.path }) { w ->
                        val active = w.path == state.activeCwd
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(w) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.FolderOpen, null, tint = cs.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(w.path, style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (active) Icon(Icons.Filled.Check, null, tint = cs.primary,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
