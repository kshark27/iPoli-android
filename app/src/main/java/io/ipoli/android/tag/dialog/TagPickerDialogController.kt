package io.ipoli.android.tag.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction

import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.normalIcon
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.showShortToast
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.dialog.TagPickerViewState.StateType.*
import kotlinx.android.synthetic.main.dialog_tag_picker.view.*
import kotlinx.android.synthetic.main.item_tag_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 5/1/18.
 */
sealed class TagPickerAction : Action {
    data class AddTag(val tag: Tag) : TagPickerAction() {
        override fun toMap() = mapOf("tag" to tag.name)
    }

    data class RemoveTag(val tag: Tag) : TagPickerAction() {
        override fun toMap() = mapOf("tag" to tag.name)
    }

    data class Load(val selectedTags: Set<Tag>) : TagPickerAction() {
        override fun toMap() = mapOf("selectedTags" to selectedTags.joinToString(",") { it.name })
    }

    object Close : TagPickerAction()
}

object TagPickerReducer : BaseViewStateReducer<TagPickerViewState>() {
    override val stateKey = key<TagPickerViewState>()

    override fun reduce(
        state: AppState,
        subState: TagPickerViewState,
        action: Action
    ) = when (action) {
        is TagPickerAction.Load -> {
            state.dataState.player?.let {
                subState.copy(
                    type = DATA_LOADED,
                    petAvatar = it.pet.avatar,
                    tags = state.dataState.tags.sortedByDescending { t -> t.isFavorite },
                    selectedTags = action.selectedTags
                )
            } ?: subState.copy(
                type = LOADING,
                tags = state.dataState.tags.sortedByDescending { it.isFavorite },
                selectedTags = action.selectedTags
            )
        }

        is DataLoadedAction.PlayerChanged -> {
            subState.copy(
                type = DATA_LOADED,
                petAvatar = action.player.pet.avatar
            )
        }

        is DataLoadedAction.TagsChanged -> {
            val tags = action.tags
            subState.copy(
                type = TAGS_CHANGED,
                tags = tags,
                selectedTags = subState.selectedTags.filter { tags.contains(it) }.toSet()
            )
        }

        is TagPickerAction.AddTag -> {
            if (subState.maxTagsReached) {
                subState.copy(
                    type = SHOW_MAX_TAGS
                )
            } else {
                val selectedTags = subState.selectedTags + action.tag
                subState.copy(
                    type = TAGS_CHANGED,
                    selectedTags = selectedTags,
                    maxTagsReached = selectedTags.size >= Constants.MAX_TAGS_PER_ITEM
                )
            }

        }

        is TagPickerAction.RemoveTag -> {
            val selectedTags = subState.selectedTags - action.tag
            subState.copy(
                type = TAGS_CHANGED,
                selectedTags = selectedTags,
                maxTagsReached = selectedTags.size >= Constants.MAX_TAGS_PER_ITEM
            )
        }

        is TagPickerAction.Close -> {
            subState.copy(
                type = CLOSE
            )
        }

        else -> subState
    }

    override fun defaultState() = TagPickerViewState(
        type = LOADING,
        petAvatar = PetAvatar.ELEPHANT,
        tags = emptyList(),
        selectedTags = emptySet(),
        maxTagsReached = false
    )
}

data class TagPickerViewState(
    val type: StateType,
    val petAvatar: PetAvatar,
    val tags: List<Tag>,
    val selectedTags: Set<Tag>,
    val maxTagsReached: Boolean
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_LOADED,
        SWITCH,
        TAGS_CHANGED,
        SHOW_MAX_TAGS,
        CLOSE
    }
}

class TagPickerDialogController(args: Bundle? = null) :
    ReduxDialogController<TagPickerAction, TagPickerViewState, TagPickerReducer>(args) {
    override val reducer = TagPickerReducer

    private lateinit var listener: (Set<Tag>) -> Unit

    private lateinit var selectedTags: Set<Tag>

    constructor(selectedTags: Set<Tag> = emptySet(), listener: (Set<Tag>) -> Unit) : this() {
        this.selectedTags = selectedTags
        this.listener = listener
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_tag_picker, null)
        view.tagList.layoutManager = LinearLayoutManager(activity!!)
        view.tagList.adapter = FavouriteTagAdapter()
        return view
    }

    override fun onCreateLoadAction() = TagPickerAction.Load(selectedTags)

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.tag_picker_title)
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog {
        return dialogBuilder
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }


    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(TagPickerAction.Close)
            }
        }
    }

    override fun render(state: TagPickerViewState, view: View) {
        when (state.type) {

            DATA_LOADED -> {
                changeIcon(state.petHeadImage)
                renderFavouriteTags(view, state)
            }

            TAGS_CHANGED -> {
                renderFavouriteTags(view, state)
            }

            SHOW_MAX_TAGS -> {
                view.tagList.adapter = FavouriteTagAdapter()
                renderFavouriteTags(view, state)
                showShortToast(R.string.max_tags_message)
            }

            CLOSE -> {
                listener(state.selectedTags)
                dismiss()
            }

            else -> {
            }
        }
    }

    private fun renderFavouriteTags(
        view: View,
        state: TagPickerViewState
    ) {
        (view.tagList.adapter as FavouriteTagAdapter).updateAll(state.viewModels)
    }

    data class TagViewModel(
        val name: String,
        val icon: Icon?,
        val color: Color,
        val isChecked: Boolean,
        val tag: Tag
    ) : RecyclerViewViewModel {
        override val id: String
            get() = tag.id
    }

    inner class FavouriteTagAdapter :
        BaseRecyclerViewAdapter<TagViewModel>(
            R.layout.item_tag_picker
        ) {
        override fun onBindViewModel(
            vm: TagViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.tagName.text = vm.name
            view.tagName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                IconicsDrawable(view.context)
                    .normalIcon(
                        vm.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                        vm.color.androidColor.color500
                    ).respectFontBounds(true),
                null, null, null
            )
            view.tagCheckBox.setOnCheckedChangeListener(null)
            view.tagCheckBox.isChecked = vm.isChecked
            view.setOnClickListener {
                view.tagCheckBox.isChecked = !vm.isChecked
            }
            view.tagCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    dispatch(TagPickerAction.AddTag(vm.tag))
                } else {
                    dispatch(TagPickerAction.RemoveTag(vm.tag))
                }
            }
        }
    }

    private val TagPickerViewState.viewModels: List<TagViewModel>
        get() = tags.map {
            TagViewModel(
                name = it.name,
                icon = it.icon,
                color = it.color,
                isChecked = selectedTags.contains(it),
                tag = it
            )
        }

    private val TagPickerViewState.petHeadImage
        get() = AndroidPetAvatar.valueOf(petAvatar.name).headImage

}