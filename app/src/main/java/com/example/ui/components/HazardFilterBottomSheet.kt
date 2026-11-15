package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

data class FilterOption(
    val id: String,
    val title: String,
    val count: Int,
    val iconEmoji: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardFilterBottomSheet(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onlyCritical: Boolean,
    onToggleOnlyCritical: (Boolean) -> Unit,
    showRepaired: Boolean,
    onToggleShowRepaired: (Boolean) -> Unit,
    categoryCounts: Map<String, Int>,
    totalAlertsCount: Int,
    onDismiss: () -> Unit,
    onResetFilters: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val options = listOf(
        FilterOption("all", "Todos los peligros", totalAlertsCount, "⚠️"),
        FilterOption("pozo", "Baches y Pozos", categoryCounts["pozo"] ?: 0, "🕳️"),
        FilterOption("control_radar", "Controles y Radares", categoryCounts["control_radar"] ?: 0, "👮"),
        FilterOption("ruta_detonada", "Ruta Detonada / Sin Asfalto", categoryCounts["ruta_detonada"] ?: 0, "💥"),
        FilterOption("neblina", "Neblina y Humo", categoryCounts["neblina"] ?: 0, "🌫️"),
        FilterOption("corte_obra", "Cortes y Obras", categoryCounts["corte_obra"] ?: 0, "🚧"),
        FilterOption("vehiculo_averiado", "Auto Varado en Banquina", categoryCounts["vehiculo_averiado"] ?: 0, "🚗"),
        FilterOption("aventon", "Aventón / Dedo Verificado", categoryCounts["aventon"] ?: 0, "👍"),
        FilterOption("alcantarilla", "Alcantarillas rotas", categoryCounts["alcantarilla"] ?: 0, "⚡"),
        FilterOption("agua", "Agua acumulada", categoryCounts["agua"] ?: 0, "🌊"),
        FilterOption("animales", "Animales sueltos", categoryCounts["animales"] ?: 0, "🐄"),
        FilterOption("semaforo", "Semáforos apagados", categoryCounts["semaforo"] ?: 0, "🚦")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate950,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Seleccionar Peligros a Mostrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tipo de Incidente en la Vía (12 opciones disponibles):",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Column Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options) { opt ->
                    val isSelected = if (opt.id == "all") {
                        selectedCategory == null || selectedCategory == "all"
                    } else {
                        selectedCategory == opt.id
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (opt.id == "all") onSelectCategory(null) else onSelectCategory(opt.id)
                            }
                            .testTag("filter_opt_${opt.id}"),
                        color = if (isSelected) Amber500.copy(alpha = 0.12f) else Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Amber500 else Slate800
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = opt.iconEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opt.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Amber400 else Color.White,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${opt.count} alerta${if (opt.count == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Ajustes Adicionales:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Switch: Solo Peligros Críticos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(AlertRed500, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Solo Peligros Críticos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
                Switch(
                    checked = onlyCritical,
                    onCheckedChange = onToggleOnlyCritical,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlertRed500,
                        checkedTrackColor = AlertRed500.copy(alpha = 0.3f),
                        uncheckedThumbColor = Slate700,
                        uncheckedTrackColor = Slate900
                    )
                )
            }

            // Checkbox: Mostrar Pozos ya Reparados
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SafetyGreen500, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mostrar Pozos ya Reparados",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
                Checkbox(
                    checked = showRepaired,
                    onCheckedChange = onToggleShowRepaired,
                    colors = CheckboxDefaults.colors(
                        checkedColor = SafetyGreen500,
                        uncheckedColor = Slate700,
                        checkmarkColor = Color.Black
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reset Button
            Button(
                onClick = {
                    onResetFilters()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_filters_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate900,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate800)
            ) {
                Text(
                    text = "Restablecer todos los filtros",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
