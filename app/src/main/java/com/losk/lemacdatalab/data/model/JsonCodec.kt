package com.losk.lemacdatalab.data

import com.losk.lemacdatalab.model.*
import org.json.JSONObject

fun Registro.json() = JSONObject().apply {
    put("id", id); put("ambito", ambito.name); put("fecha", fecha)
    put("intensidad", intensidad); put("categoria", categoria); put("contexto", contexto)
    put("estrategia", estrategia); put("resultado", resultado); put("nota", nota)
    put("objetivoId", objetivoId ?: JSONObject.NULL); put("eliminado", eliminado)
}

fun registroDeJson(j: JSONObject, owner: String, revision: Int = 1, pendiente: Boolean = false) = Registro(
    id = j.getString("id"), usuarioId = owner, ambito = Ambito.valueOf(j.getString("ambito")),
    fecha = j.getLong("fecha"), intensidad = j.getInt("intensidad"), categoria = j.getString("categoria"),
    contexto = j.getString("contexto"), estrategia = j.optString("estrategia"), resultado = j.optInt("resultado"),
    nota = j.optString("nota"), objetivoId = if (j.isNull("objetivoId")) null else j.getString("objetivoId"),
    revision = revision, pendiente = pendiente, eliminado = j.optBoolean("eliminado")
)

fun Objetivo.json() = JSONObject().apply {
    put("id", id); put("descripcion", descripcion); put("meta", meta); put("estado", estado)
    put("fecha", fecha); put("eliminado", eliminado)
}

fun objetivoDeJson(j: JSONObject, owner: String, revision: Int = 1, pendiente: Boolean = false) = Objetivo(
    id = j.getString("id"), usuarioId = owner, descripcion = j.getString("descripcion"), meta = j.getInt("meta"),
    estado = j.getString("estado"), fecha = j.getLong("fecha"), revision = revision,
    pendiente = pendiente, eliminado = j.optBoolean("eliminado")
)

fun Recurso.json() = JSONObject().apply {
    put("id", id); put("ambito", ambito.name); put("tipo", tipo.name); put("titulo", titulo)
    put("contenido", contenido); put("eliminado", eliminado)
}

fun recursoDeJson(j: JSONObject, revision: Int = 1, pendiente: Boolean = false) = Recurso(
    id = j.getString("id"), ambito = Ambito.valueOf(j.getString("ambito")), tipo = TipoRecurso.valueOf(j.getString("tipo")),
    titulo = j.getString("titulo"), contenido = j.getString("contenido"), revision = revision,
    pendiente = pendiente, eliminado = j.optBoolean("eliminado")
)
