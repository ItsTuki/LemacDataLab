package com.losk.lemacdatalab.model

import java.util.UUID

data class RegistroAnimoEntity(
    val id_registro: String = UUID.randomUUID().toString(),
    val id_usuario: String,
    val fecha_hora: String,
    val intensidad: Int,
    val contexto: String,
    val nota_opcional: String,
    val estado_sincronizacion: String = "Pendiente",
    val eliminado_localmente: Int = 0
)