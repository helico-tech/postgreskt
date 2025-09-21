package nl.helico.postgreskt_old.types

import io.ktor.util.AttributeKey

data class TypeDef(
    val oid: Int,
    val name: String,
)

val TypeDefsKey = AttributeKey<List<TypeDef>>("TypeDefs")
