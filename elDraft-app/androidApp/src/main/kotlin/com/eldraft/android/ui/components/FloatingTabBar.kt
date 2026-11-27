package com.eldraft.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eldraft.android.ui.theme.ElDraftTheme

/** Cuánto más ancha es la pestaña seleccionada respecto a las demás (que solo llevan ícono). */
private const val SELECTED_WEIGHT = 1.9f

/**
 * Una pestaña del [FloatingTabBar]. [key] identifica la sección (en `MainScaffold`
 * es la ruta del NavHost) y [label] llega ya resuelto: el componente no toca `R`
 * para que se pueda previsualizar y reutilizar sin depender de una pantalla.
 */
data class FloatingTabBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Barra de pestañas flotante: un pill de cristal (superficie translúcida con borde
 * de luz) despegado de los bordes, con sombra,
 * que se dibuja ENCIMA del contenido en vez de empujarlo (el slot `bottomBar` de
 * `Scaffold` hace lo segundo). Reemplaza al `NavigationBar` de Material, que trae
 * alto fijo de 80.dp y su propio manejo de insets — dos cosas que estorban cuando
 * la barra tiene que quedar suspendida sobre el mapa y las listas.
 *
 * Como flota, el contenido de cada pestaña tiene que reservarse el espacio por su
 * cuenta con [floatingTabBarInset]; si no, la última card de una lista queda
 * debajo del pill.
 */
@Composable
fun FloatingTabBar(
    items: List<FloatingTabBarItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Borde de cristal: el canto de arriba atrapa la luz (el color de la propia
    // superficie, que destaca sobre el fondo gris) y el de abajo se funde en una
    // línea tenue. Sin él, una superficie translúcida se pierde sobre el contenido.
    val edge = Brush.verticalGradient(
        listOf(colors.surface, colors.onSurface.copy(alpha = ElDraftTheme.alpha.hairline)),
    )
    Surface(
        shape = ElDraftTheme.shape.pill,
        color = colors.surface.copy(alpha = ElDraftTheme.alpha.glass),
        border = BorderStroke(ElDraftTheme.size.borderHairline, edge),
        shadowElevation = ElDraftTheme.elevation.overlay,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ElDraftTheme.spacing.lg)
            .height(ElDraftTheme.size.tabBarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElDraftTheme.spacing.xs)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                TabBarItem(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                    // La seleccionada es más ancha: ahí cabe el ícono con su etiqueta.
                    modifier = Modifier.weight(if (item.key == selectedKey) SELECTED_WEIGHT else 1f),
                )
            }
        }
    }
}

/**
 * Espacio que hay que dejar libre al fondo de una pantalla para que su contenido
 * no quede tapado por la barra: el alto del pill, su margen inferior y el inset
 * del sistema (la barra de gestos mide distinto que la de tres botones).
 *
 * [extra] suma el hueco propio que la pantalla ya quisiera dejar al final.
 */
@Composable
fun floatingTabBarInset(extra: Dp = 0.dp): Dp =
    ElDraftTheme.size.tabBarHeight +
        ElDraftTheme.spacing.md +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        extra

/** [floatingTabBarInset] como `contentPadding` de una lista. */
@Composable
fun floatingTabBarPadding(extra: Dp = 0.dp): PaddingValues =
    PaddingValues(bottom = floatingTabBarInset(extra))

/**
 * Una pestaña: la seleccionada muestra ícono y etiqueta; las demás solo el ícono,
 * para que quepan cinco sin apretarse. La seleccionada se marca con el mismo pill
 * naranja suave (`primaryContainer`) que usaba el indicador del `NavigationBar`,
 * para no estrenar un lenguaje visual de "activo" distinto al del resto de la app.
 */
@Composable
private fun TabBarItem(
    item: FloatingTabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = ElDraftTheme.spacing.sm)
            .clip(ElDraftTheme.shape.pill)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xxs),
        ) {
            Icon(
                item.icon,
                // Sin etiqueta visible el ícono es el único nombre que lee TalkBack;
                // con ella, repetirlo haría que la leyera dos veces.
                contentDescription = if (selected) null else item.label,
                tint = content,
                modifier = Modifier.size(ElDraftTheme.size.iconLg),
            )
            if (selected) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(widthDp = 400, backgroundColor = 0xFFF2F2F4, showBackground = true)
@Composable
private fun FloatingTabBarPreview() {
    ElDraftTheme {
        FloatingTabBar(
            items = listOf(
                FloatingTabBarItem("organizo", "Organizo", Icons.Filled.SportsSoccer),
                FloatingTabBarItem("juego", "Juego", Icons.AutoMirrored.Filled.DirectionsRun),
                FloatingTabBarItem("equipos", "Equipos", Icons.Filled.Groups),
                FloatingTabBarItem("buscar", "Buscar Cupo", Icons.Filled.Map),
                FloatingTabBarItem("perfil", "Perfil", Icons.Filled.Person),
            ),
            selectedKey = "organizo",
            onSelect = {},
            modifier = Modifier.padding(vertical = ElDraftTheme.spacing.md),
        )
    }
}
