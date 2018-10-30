package io.ipoli.android.common.persistence

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import kotlin.coroutines.experimental.suspendCoroutine

fun Query.execute(): QuerySnapshot =
    Tasks.await(get(Source.SERVER))

val Query.documents: List<DocumentSnapshot> get() = execute().documents

fun DocumentReference.getSync(): DocumentSnapshot =
    Tasks.await(get(Source.SERVER))

suspend fun DocumentReference.getAsync(): DocumentSnapshot =
    suspendCoroutine {
        get().addOnCompleteListener { t ->
            if (t.isSuccessful) {
                it.resume(t.result!!)
            } else {
                it.resumeWithException(t.exception!!)
            }
        }
    }