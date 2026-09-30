package com.adityoarr.securevaultnotes.domain.model

data class Note(
    val id: Long = 0,
    val title: String,
    val description: String,
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

data class Attachment(
    val id: Long = 0,
    val noteId: Long = 0,
    val filePath: String, // Path absolut ke file terenkripsi di internal storage
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis()
)