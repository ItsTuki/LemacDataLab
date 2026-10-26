package com.losk.lemacdatalab.model

data class User(
    val email: String,
    val token: String? = null
)