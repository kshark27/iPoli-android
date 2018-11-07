package io.ipoli.android.common.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.R
import kotlinx.android.synthetic.main.dialog_feedback.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/2/17.
 */

class FeedbackDialogController : BaseDialogController {

    interface FeedbackListener {
        fun onSendFeedback(feedback: String)
        fun onSuggestIdea()
    }

    private var listener: FeedbackListener = object : FeedbackListener {
        override fun onSendFeedback(feedback: String) {}
        override fun onSuggestIdea() {}
    }

    constructor(listener: FeedbackListener) : this() {
        this.listener = listener
    }

    constructor(args: Bundle? = null) : super(args)

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton("Send") { _, _ ->
                val feedback = contentView.feedback.text.toString()
                listener.onSendFeedback(feedback)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.request_feature) { _, _ ->
                listener.onSuggestIdea()
            }
            .create()

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View =
        inflater.inflate(R.layout.dialog_feedback, null)

    override fun onHeaderViewCreated(headerView: View?) {
        headerView!!.dialogHeaderTitle.setText(R.string.rate_dialog_feedback_title)
        headerView.dialogHeaderIcon.setImageResource(R.drawable.logo)
    }
}