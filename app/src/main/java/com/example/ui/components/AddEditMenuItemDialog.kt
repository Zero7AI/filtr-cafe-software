package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MenuItemEntity
import com.example.ui.theme.CoffeeBrownPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMenuItemDialog(
    itemToEdit: MenuItemEntity?,
    existingCategories: List<String> = emptyList(),
    onSave: (name: String, category: String, price: Double, description: String, isAvailable: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var category by remember { mutableStateOf(itemToEdit?.category ?: "ICED COFFEE") }
    var priceStr by remember { mutableStateOf(itemToEdit?.price?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var description by remember { mutableStateOf(itemToEdit?.description ?: "") }
    var isAvailable by remember { mutableStateOf(itemToEdit?.isAvailable ?: true) }

    var categoryExpanded by remember { mutableStateOf(false) }

    val defaultCategories = listOf("ICED COFFEE", "FRAPPE", "FRIES", "SANDWICH", "HOT COFFEE", "BEVERAGES", "BAKERY", "SHAKES")
    val categoryOptions = (existingCategories + defaultCategories).distinct().filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (itemToEdit != null) Icons.Default.Edit else Icons.Default.AddCircle,
                contentDescription = null,
                tint = CoffeeBrownPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (itemToEdit != null) "Edit Menu Item" else "Add New Menu Item",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CoffeeBrownPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_edit_menu_item_dialog")
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    placeholder = { Text("e.g. Hazelnut Frappe") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {
                            category = it
                            categoryExpanded = true
                        },
                        label = { Text("Category") },
                        placeholder = { Text("Select or type category...") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("menu_item_category_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontWeight = if (option.equals(category, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    category = option
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Price (₹)") },
                    placeholder = { Text("e.g. 129.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_price_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Short item description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isAvailable,
                        onCheckedChange = { isAvailable = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "In Stock / Available for Sale",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        onSave(name.trim(), category.trim(), p, description.trim(), isAvailable)
                    }
                },
                enabled = name.isNotBlank() && priceStr.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_menu_item_button")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoffeeBrownPrimary)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
