package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun getSafeFirestoreInstance(context: Context): FirebaseFirestore {
    return try {
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        val dbId = if (resId != 0) context.getString(resId) else ""
        if (dbId.isNotBlank() && dbId != "(default)") {
            FirebaseFirestore.getInstance(dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    } catch (t: Throwable) {
        Log.w("SafeFirebase", "Custom firestore_database_id failed, falling back to default instance: ${t.message}")
        try {
            FirebaseFirestore.getInstance()
        } catch (t2: Throwable) {
            Log.e("SafeFirebase", "Default firestore instance error: ${t2.message}")
            FirebaseFirestore.getInstance()
        }
    }
}
