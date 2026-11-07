package app.allinonereminder.ui.items

import app.allinonereminder.model.ItemType

/** Pick-lists for the Unit and Form fields, per item type. Users can still type anything else. */
object DoseOptions {
    fun units(type: ItemType): List<String> = when (type) {
        ItemType.MEDICINE -> listOf("tablet", "capsule", "mg", "mcg", "g", "ml", "IU", "drops", "puffs", "patch", "sachet", "teaspoon")
        ItemType.INJECTION -> listOf("units", "IU", "ml", "mg", "mcg", "dose")
        ItemType.WATER_DRINK -> listOf("ml", "glass", "cup", "litre", "oz", "bottle")
        ItemType.FOOD -> listOf("serving", "piece", "bowl", "cup", "plate", "slice", "g")
        ItemType.SUPPLEMENT -> listOf("tablet", "capsule", "softgel", "gummy", "scoop", "mg", "mcg", "g", "ml", "IU", "drops")
        else -> listOf("dose")
    }

    fun forms(type: ItemType): List<String> = when (type) {
        ItemType.MEDICINE -> listOf("Tablet", "Capsule", "Syrup", "Drops", "Cream or ointment", "Inhaler", "Spray", "Patch", "Powder", "Suppository")
        ItemType.INJECTION -> listOf("Pen", "Vial", "Prefilled syringe", "Pump")
        ItemType.WATER_DRINK -> listOf("Water", "Tea", "Coffee", "Juice", "Milk", "ORS / electrolyte")
        ItemType.FOOD -> listOf("Meal", "Snack", "Fruit", "Salad", "Soup")
        ItemType.SUPPLEMENT -> listOf("Tablet", "Capsule", "Gummy", "Powder", "Liquid", "Softgel")
        else -> emptyList()
    }

    fun defaultUnit(type: ItemType): String = units(type).first()
    fun defaultForm(type: ItemType): String = forms(type).firstOrNull() ?: ""
}
