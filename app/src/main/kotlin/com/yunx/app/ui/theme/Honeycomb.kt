package com.yunx.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object Honeycomb {
    val CellWidth = 104.dp
    val CellHeight = 90.dp
    val CellGap = 6.dp

    fun flatTopHexShape(): Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w * 0.25f, 0f)
        lineTo(w * 0.75f, 0f)
        lineTo(w, h * 0.5f)
        lineTo(w * 0.75f, h)
        lineTo(w * 0.25f, h)
        lineTo(0f, h * 0.5f)
        close()
    }
}

data class HoneycombCell(
    val avatarText: String,
    val label: String,
    val isLoggedIn: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
fun HoneycombCellView(
    cell: HoneycombCell,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(Honeycomb.CellWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val shape = Honeycomb.flatTopHexShape()
        val bg = if (cell.isLoggedIn) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        val fg = if (cell.isLoggedIn) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(
            modifier = Modifier
                .width(Honeycomb.CellWidth)
                .height(Honeycomb.CellHeight)
                .clip(shape)
                .background(bg)
                .then(
                    if (cell.onClick != null) {
                        Modifier.clickable(onClickLabel = cell.label) { cell.onClick.invoke() }
                    } else Modifier
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 4.dp)
                    .size(34.dp),
                shape = CircleShape,
                color = if (cell.isLoggedIn) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = cell.avatarText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = fg
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 18.dp)
                    .size(8.dp)
                    .background(
                        color = if (cell.isLoggedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = cell.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(Honeycomb.CellWidth)
        )
    }
}

@Composable
fun HoneycombGrid(
    cells: List<HoneycombCell>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var index = 0
        var rowIndex = 0
        while (index < cells.size) {
            val offsetRow = rowIndex % 2 == 1
            val count = if (offsetRow) 2 else 3
            val end = minOf(index + count, cells.size)
            val rowCells = cells.subList(index, end)
            if (!offsetRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(Honeycomb.CellGap)) {
                    rowCells.forEach { HoneycombCellView(it) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Honeycomb.CellWidth + Honeycomb.CellGap)) {
                    rowCells.forEach { HoneycombCellView(it) }
                }
            }
            if (end < cells.size) {
                Spacer(modifier = Modifier.height(Honeycomb.CellGap))
            }
            index = end
            rowIndex++
        }
    }
}

@Composable
fun HoneycombPreview(selected: Boolean, modifier: Modifier = Modifier) {
    val shape = Honeycomb.flatTopHexShape()
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val w = Honeycomb.CellWidth * 0.62f
    val h = Honeycomb.CellHeight * 0.62f
    val gap = Honeycomb.CellGap
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(w)
                        .height(h)
                        .clip(shape)
                        .background(container)
                )
            }
        }
        Spacer(modifier = Modifier.height(gap))
        Row(horizontalArrangement = Arrangement.spacedBy(w + gap)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(w)
                        .height(h)
                        .clip(shape)
                        .background(container)
                )
            }
        }
    }
}
