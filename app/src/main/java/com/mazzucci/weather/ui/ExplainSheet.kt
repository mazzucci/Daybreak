package com.mazzucci.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Explanation
import com.mazzucci.weather.domain.Term

/** Opens the explanation for a tapped term. Null where explanations aren't available (e.g. screenshots of parts). */
val LocalExplain = compositionLocalOf<((Term) -> Unit)?> { null }

/** The explanation sheet: title, what it means in general, and what today's value means. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainSheet(explanation: Explanation, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ExplainContent(explanation)
    }
}

/** The sheet's content on its own, so it can be rendered in screenshots. */
@Composable
fun ExplainContent(explanation: Explanation) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(explanation.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(explanation.now, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "What it means",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(explanation.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
