package site.unclefish.wearmixue.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import site.unclefish.wearmixue.R
import site.unclefish.wearmixue.ui.components.Body
import site.unclefish.wearmixue.ui.components.ScreenColumn
import site.unclefish.wearmixue.ui.components.Title

@Composable
internal fun LogoutConfirmScreen(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    ScreenColumn {
        Title(stringResource(R.string.action_logout))
        Body(stringResource(R.string.logout_confirm_prompt))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    modifier = Modifier.size(24.dp)
                )
            }
            FilledIconButton(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_logout),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
