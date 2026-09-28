@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Place

/** Manage saved places: toggle current location, reorder with up/down, remove. */
@Composable
fun PlacesScreen(
    places: List<Place>,
    useCurrentLocation: Boolean,
    onUseCurrentLocationChange: (Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Place) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add place") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    headlineContent = { Text("Current location") },
                    supportingContent = { Text("Show the weather where you are as the first page") },
                    trailingContent = { Switch(useCurrentLocation, onUseCurrentLocationChange) },
                )
                HorizontalDivider()
            }
            if (places.isEmpty()) {
                item {
                    Text(
                        "No saved places yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(places, key = { _, p -> p.id }) { i, place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.detail?.let { { Text(it) } },
                    trailingContent = {
                        Row {
                            IconButton({ onMove(i, i - 1) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${place.name} up")
                            }
                            IconButton({ onMove(i, i + 1) }, enabled = i < places.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${place.name} down")
                            }
                            IconButton({ onRemove(place) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${place.name}")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
