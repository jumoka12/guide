package com.ampgames.vidsaver.ui.gallery

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.media.FileNames

const val RENAME_DIALOG_TEST_TAG = "rename_dialog"

/**
 * Renames a saved video. The extension is not shown or editable — changing it
 * would not change the container, so letting the user edit it only produces
 * files that lie about their format.
 */
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val sanitized = FileNames.sanitize(name)
    val isValid = name.isNotBlank() && sanitized.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(RENAME_DIALOG_TEST_TAG),
        title = { Text(stringResource(R.string.gallery_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = !isValid,
                label = { Text(stringResource(R.string.gallery_rename_label)) },
                supportingText = {
                    if (!isValid) {
                        Text(stringResource(R.string.gallery_rename_invalid))
                    } else if (sanitized != name.trim()) {
                        // Be upfront that the name will be adjusted, rather than
                        // silently saving something different from what was typed.
                        Text(stringResource(R.string.gallery_rename_adjusted, sanitized))
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.testTag("rename_field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = isValid,
                modifier = Modifier.testTag("rename_confirm"),
            ) {
                Text(stringResource(R.string.gallery_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.gallery_rename_cancel))
            }
        },
    )
}
