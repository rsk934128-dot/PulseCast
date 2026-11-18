package com.example.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface CallNotificationAction {
    val roomCode: String

    data class Answer(override val roomCode: String) : CallNotificationAction
    data class Decline(override val roomCode: String) : CallNotificationAction
    data class Hangup(override val roomCode: String) : CallNotificationAction
}

object CallActionBus {
    private val _pendingAction = MutableStateFlow<CallNotificationAction?>(null)
    val pendingAction: StateFlow<CallNotificationAction?> = _pendingAction.asStateFlow()

    fun emit(action: CallNotificationAction) {
        _pendingAction.value = action
    }

    fun clear(action: CallNotificationAction) {
        if (_pendingAction.value == action) {
            _pendingAction.value = null
        }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty().trim()
        when (intent.action) {
            ACTION_DECLINE_CALL -> {
                CallRingtoneController.stopAllGlobal(context)
                CallNotificationManager(context).dismissIncomingCallNotification()
                if (roomCode.isNotEmpty()) {
                    CallActionBus.emit(CallNotificationAction.Decline(roomCode))
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            if (currentUser != null) {
                                val dbId = context.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(dbId)
                                db.collection("call_sessions")
                                    .document(roomCode)
                                    .update(
                                        mapOf(
                                            "status" to "ended",
                                            "durationSec" to 0L,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                    .await()
                            }
                        } catch (_: Exception) {
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_HANGUP_CALL -> {
                CallNotificationManager(context).dismissOngoingCallNotification()
                if (roomCode.isNotEmpty()) {
                    CallActionBus.emit(CallNotificationAction.Hangup(roomCode))
                }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.calls.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.calls.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP_CALL = "com.example.calls.ACTION_HANGUP_CALL"
        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val EXTRA_CALL_ACTION = "extra_call_action"
    }
}
