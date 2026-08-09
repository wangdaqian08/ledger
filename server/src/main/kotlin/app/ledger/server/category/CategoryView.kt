package app.ledger.server.category

import java.util.UUID

data class CategoryView(
    val id: UUID,
    val key: String,
    val nameEn: String,
    val nameZh: String,
    val icon: String,
    val hue: Short,
    val builtIn: Boolean,
)
