package remy.irremote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun buttonColor(color: String?): Int = when (color) {
    "red" -> 0xFFC62828
    "green" -> 0xFF2E7D32
    "blue" -> 0xFF1565C0
    "yellow" -> 0xFFF9A825
    else -> 0xFF303030
}.toInt()

fun labelColor(color: String?): Int = if (color == "yellow") 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(darkColorScheme(background = Color.Black), content = content)

@Composable
fun RemoteTabs(remotes: List<Remote>, selected: Int, onSelect: (Int) -> Unit) =
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp) {
        remotes.forEachIndexed { i, r -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(r.name) }) }
    }

@Composable
fun RemoteGrid(remote: Remote, highlighted: (Int) -> Boolean = { false }, onClick: (Int) -> Unit) =
    LazyVerticalGrid(
        GridCells.Fixed(remote.columns),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(remote.buttons, span = { _, b -> GridItemSpan(if (b.type == "separator") maxLineSpan else 1) }) { i, b ->
            when (b.type) {
                "separator" -> HorizontalDivider(color = Color.DarkGray)
                "blank" -> Spacer(Modifier.height(56.dp))
                else -> RemoteButton(b, highlighted(i)) { onClick(i) }
            }
        }
    }

@Composable
fun RemoteButton(b: IrButton, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(56.dp).clip(shape).background(Color(buttonColor(b.color)))
            .then(if (highlighted) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(b.label, color = Color(labelColor(b.color)), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) = AppTheme {
    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(Modifier.safeDrawingPadding()) { content() }
    }
}
