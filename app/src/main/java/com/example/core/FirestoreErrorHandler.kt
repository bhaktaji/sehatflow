package com.example.core

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    GET, LIST, CREATE, UPDATE, DELETE, LISTEN
}

fun handleFirestoreError(error: Exception, operation: OperationType, path: String) {
    val json = JSONObject().apply {
        put("error_type", error.javaClass.simpleName)
        put("operation", operation.name)
        put("path", path)
        put("message", error.localizedMessage ?: "Unknown error")
        if (error is FirebaseFirestoreException) {
            put("code", error.code.name)
        }
    }
    Log.e("FirestoreError", json.toString(), error)
}
