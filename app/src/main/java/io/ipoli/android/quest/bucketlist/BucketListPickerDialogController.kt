package io.ipoli.android.quest.bucketlist

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.bucketlist.BucketListPickerViewState.StateType.*
import kotlinx.android.synthetic.main.dialog_bucket_list_picker.view.*
import kotlinx.android.synthetic.main.item_bucket_list_picker_quest.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

sealed class BucketListPickerAction : Action {
    object Load : BucketListPickerAction()
    data class SelectItem(val questId: String) : BucketListPickerAction()
    data class DeselectItem(val questId: String) : BucketListPickerAction()
    object Done : BucketListPickerAction()
}

object BucketListPickerReducer : BaseViewStateReducer<BucketListPickerViewState>() {

    override val stateKey = key<BucketListPickerViewState>()

    override fun reduce(
        state: AppState,
        subState: BucketListPickerViewState,
        action: Action
    ) =
        when (action) {

            is BucketListPickerAction.Load -> {
                val newState = subState.copy(
                    bucketItems = state.dataState.unscheduledQuests
                        .filter { !it.isCompleted }
                        .map {
                            BucketListPickerViewState.BucketItem(it, false)
                        }
                )
                state.dataState.player?.let {
                    newState.copy(
                        type = DATA_LOADED,
                        petAvatar = it.pet.avatar
                    )
                } ?: newState.copy(
                    type = LOADING
                )
            }

            is BucketListPickerAction.SelectItem ->
                subState.copy(
                    type = DATA_CHANGED,
                    bucketItems = subState.bucketItems.map {
                        if (it.quest.id == action.questId)
                            it.copy(
                                isSelected = true
                            )
                        else
                            it
                    }
                )

            is BucketListPickerAction.DeselectItem ->
                subState.copy(
                    type = DATA_CHANGED,
                    bucketItems = subState.bucketItems.map {
                        if (it.quest.id == action.questId)
                            it.copy(
                                isSelected = false
                            )
                        else
                            it
                    }
                )

            is BucketListPickerAction.Done ->
                subState.copy(
                    type = DONE
                )

            is DataLoadedAction.UnscheduledQuestsChanged -> {
                subState.copy(
                    type = DATA_CHANGED,
                    bucketItems = action.quests
                        .filter { !it.isCompleted }
                        .map {
                            BucketListPickerViewState.BucketItem(it, false)
                        }
                )
            }

            is DataLoadedAction.PlayerChanged -> {
                subState.copy(
                    type = PET_AVATAR_CHANGED,
                    petAvatar = action.player.pet.avatar
                )
            }

            else -> subState
        }

    override fun defaultState() =
        BucketListPickerViewState(
            type = LOADING,
            petAvatar = PetAvatar.ELEPHANT,
            bucketItems = emptyList()
        )
}

data class BucketListPickerViewState(
    val type: StateType,
    val petAvatar: PetAvatar,
    val bucketItems: List<BucketItem>
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_LOADED,
        PET_AVATAR_CHANGED,
        DATA_CHANGED,
        DONE
    }

    data class BucketItem(val quest: Quest, val isSelected: Boolean)
}

class BucketListPickerDialogController(args: Bundle? = null) :
    ReduxDialogController<BucketListPickerAction, BucketListPickerViewState, BucketListPickerReducer>(
        args
    ) {

    override val reducer = BucketListPickerReducer

    private var listener: (List<Quest>) -> Unit = {}

    constructor(listener: (List<Quest>) -> Unit) : this() {
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.dialog_bucket_list_picker_title)
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_bucket_list_picker, null)
        view.questList.layoutManager = LinearLayoutManager(view.context)
        view.questList.adapter = QuestAdapter()
        return view
    }

    override fun onCreateLoadAction() = BucketListPickerAction.Load

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton(R.string.schedule, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(BucketListPickerAction.Done)
            }
        }
    }

    override fun render(state: BucketListPickerViewState, view: View) {
        when (state.type) {

            DATA_LOADED -> {
                changeIcon(state.petHeadImage)
                (view.questList.adapter as QuestAdapter).updateAll(state.bucketItemViewModels)
            }

            DATA_CHANGED -> {
                (view.questList.adapter as QuestAdapter).updateAll(state.bucketItemViewModels)
            }

            PET_AVATAR_CHANGED -> {
                changeIcon(state.petHeadImage)
            }

            DONE -> {
                listener(state.bucketItems.filter { it.isSelected }.map { it.quest })
                dismiss()
            }

            else -> {
            }
        }
    }

    private val BucketListPickerViewState.petHeadImage
        get() = AndroidPetAvatar.valueOf(petAvatar.name).headImage

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    data class QuestItem(
        override val id: String,
        val name: String,
        val startTime: String,
        @ColorRes val color: Int,
        val tags: List<TagViewModel>,
        val icon: IIcon,
        val isRepeating: Boolean,
        val isFromChallenge: Boolean,
        val isSelected: Boolean
    ) : RecyclerViewViewModel

    inner class QuestAdapter :
        BaseRecyclerViewAdapter<QuestItem>(R.layout.item_bucket_list_picker_quest) {
        override fun onBindViewModel(vm: QuestItem, view: View, holder: SimpleViewHolder) {
            view.questName.text = vm.name

            view.questIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))

            view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))

            view.questStartTime.text = vm.startTime

            view.questStartTime.setCompoundDrawablesRelativeWithIntrinsicBounds(
                IconicsDrawable(view.context)
                    .icon(GoogleMaterial.Icon.gmd_timer)
                    .sizeDp(16)
                    .colorRes(colorTextSecondaryResource)
                    .respectFontBounds(true),
                null, null, null
            )

            if (vm.tags.isNotEmpty()) {
                renderTag(view.questTagName, vm.tags.first())
            } else {
                view.questTagName.gone()
            }

            view.questRepeatIndicator.visibility =
                if (vm.isRepeating) View.VISIBLE else View.GONE
            view.questChallengeIndicator.visibility =
                if (vm.isFromChallenge) View.VISIBLE else View.GONE

            view.questSelected.setOnCheckedChangeListener(null)
            view.onDebounceClick {
                view.questSelected.isChecked = !vm.isSelected
            }
            view.questSelected.isChecked = vm.isSelected
            view.questSelected.setOnCheckedChangeListener { _, isChecked ->
                dispatch(
                    if (isChecked)
                        BucketListPickerAction.SelectItem(vm.id)
                    else
                        BucketListPickerAction.DeselectItem(vm.id)
                )
            }
        }
    }

    private fun renderTag(tagNameView: TextView, tag: TagViewModel) {
        tagNameView.visible()
        tagNameView.text = tag.name
        TextViewCompat.setTextAppearance(
            tagNameView,
            R.style.TextAppearance_AppCompat_Caption
        )

        val indicator = tagNameView.compoundDrawablesRelative[0] as GradientDrawable
        indicator.mutate()
        val size = ViewUtils.dpToPx(8f, tagNameView.context).toInt()
        indicator.setSize(size, size)
        indicator.setColor(colorRes(tag.color))
        tagNameView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            indicator,
            null,
            null,
            null
        )
    }

    private val BucketListPickerViewState.bucketItemViewModels: List<QuestItem>
        get() =
            bucketItems.map {
                val q = it.quest
                QuestItem(
                    id = q.id,
                    name = q.name,
                    tags = q.tags.map { t ->
                        TagViewModel(
                            t.name,
                            t.color.androidColor.color500
                        )
                    },
                    startTime = QuestStartTimeFormatter.formatWithDuration(
                        q,
                        activity!!,
                        shouldUse24HourFormat
                    ),
                    color = q.color.androidColor.color500,
                    icon = q.icon?.androidIcon?.icon
                        ?: Ionicons.Icon.ion_checkmark,
                    isRepeating = q.isFromRepeatingQuest,
                    isFromChallenge = q.isFromChallenge,
                    isSelected = it.isSelected
                )
            }
}