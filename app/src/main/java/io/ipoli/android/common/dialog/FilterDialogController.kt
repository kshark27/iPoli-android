package io.ipoli.android.common.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.dialog.FilterViewState.StateType.*
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.normalIcon
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.dialog_filter.view.*
import kotlinx.android.synthetic.main.item_tag_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

sealed class FilterAction : Action {
    data class Load(val showCompleted: Boolean, val selectedTags: Set<Tag>) : FilterAction()

    data class ChangeShowAllTags(val showAll: Boolean) : FilterAction()
    data class SelectTag(val tag: Tag) : FilterAction()
    data class DeselectTag(val tag: Tag) : FilterAction()
    data class ChangeShowCompleted(val showCompleted: Boolean) : FilterAction()

    object Apply : FilterAction()
}

object FilterReducer : BaseViewStateReducer<FilterViewState>() {

    override fun reduce(
        state: AppState,
        subState: FilterViewState,
        action: Action
    ): FilterViewState =
        when (action) {

            is FilterAction.Load -> {
                val tags = state.dataState.tags.sortedByDescending { t -> t.isFavorite }
                state.dataState.player?.let {
                    subState.copy(
                        type = DATA_LOADED,
                        showCompleted = action.showCompleted,
                        petAvatar = it.pet.avatar,
                        tags = tags,
                        selectedTags = action.selectedTags
                    )
                } ?: subState.copy(
                    type = LOADING,
                    showCompleted = action.showCompleted,
                    tags = tags,
                    selectedTags = action.selectedTags
                )
            }

            is FilterAction.ChangeShowAllTags -> {
                if (action.showAll) {
                    subState.copy(
                        type = TAGS_CHANGED,
                        selectedTags = emptySet()
                    )
                } else {
                    subState.copy(
                        type = TAGS_CHANGED,
                        selectedTags = subState.tags.toSet()
                    )
                }
            }

            is FilterAction.ChangeShowCompleted -> {
                subState.copy(
                    type = DATA_LOADED,
                    showCompleted = action.showCompleted
                )
            }

            is FilterAction.SelectTag ->
                subState.copy(
                    type = TAGS_CHANGED,
                    selectedTags = subState.selectedTags + action.tag
                )

            is FilterAction.DeselectTag -> {
                val selectedTags = subState.selectedTags - action.tag
                subState.copy(
                    type = SHOW_COMPLETED_CHANGED,
                    selectedTags = selectedTags
                )
            }

            is FilterAction.Apply ->
                subState.copy(
                    type = APPLY
                )

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


            else -> subState
        }

    override fun defaultState() =
        FilterViewState(
            type = LOADING,
            showCompleted = true,
            petAvatar = PetAvatar.ELEPHANT,
            tags = emptyList(),
            selectedTags = emptySet()
        )

    override val stateKey = key<FilterViewState>()

}

data class FilterViewState(
    val type: StateType,
    val showCompleted: Boolean,
    val petAvatar: PetAvatar,
    val tags: List<Tag>,
    val selectedTags: Set<Tag>
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_LOADED,
        TAGS_CHANGED,
        SHOW_COMPLETED_CHANGED,
        APPLY
    }
}

class FilterDialogController(args: Bundle? = null) :
    ReduxDialogController<FilterAction, FilterViewState, FilterReducer>(args) {

    override val reducer = FilterReducer

    private var listener: (Boolean, Set<Tag>) -> Unit = { _, _ -> }

    private var showCompleted = true
    private var selectedTags: Set<Tag> = emptySet()

    constructor(
        showCompleted: Boolean,
        selectedTags: Set<Tag> = emptySet(),
        listener: (Boolean, Set<Tag>) -> Unit
    ) : this() {
        this.showCompleted = showCompleted
        this.selectedTags = selectedTags
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.dialog_filter_title)
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_filter, null)

        view.tagList.layoutManager = LinearLayoutManager(view.context)
        view.tagList.adapter = TagAdapter()
        return view
    }

    override fun onCreateLoadAction() = FilterAction.Load(showCompleted, selectedTags)

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(FilterAction.Apply)
            }
        }
    }


    override fun render(state: FilterViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                changeIcon(state.petHeadImage)
                renderShowCompleted(view, state)
                renderTags(view, state)
                renderShowAllTags(view, state)
            }

            TAGS_CHANGED -> {
                renderTags(view, state)
                renderShowAllTags(view, state)
            }

            SHOW_COMPLETED_CHANGED ->
                renderShowCompleted(view, state)

            APPLY -> {
                listener(state.showCompleted, state.selectedTags)
                dismiss()
            }

            else -> {
            }
        }
    }

    private fun renderShowCompleted(
        view: View,
        state: FilterViewState
    ) {
        view.showCompleted.setOnCheckedChangeListener(null)
        view.showCompleted.isChecked = state.showCompleted
        view.showCompleted.setOnCheckedChangeListener { _, isChecked ->
            dispatch(FilterAction.ChangeShowCompleted(isChecked))
        }
    }

    private fun renderShowAllTags(
        view: View,
        state: FilterViewState
    ) {
        view.showAllTags.setOnCheckedChangeListener(null)
        view.showAllTags.isChecked = state.selectedTags.isEmpty()
        view.showAllTags.setOnCheckedChangeListener { _, isChecked ->
            dispatch(FilterAction.ChangeShowAllTags(isChecked))
        }
    }

    private fun renderTags(
        view: View,
        state: FilterViewState
    ) {
        (view.tagList.adapter as TagAdapter).updateAll(state.viewModels)
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

    inner class TagAdapter :
        BaseRecyclerViewAdapter<TagViewModel>(R.layout.item_tag_picker) {

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
                    dispatch(FilterAction.SelectTag(vm.tag))
                } else {
                    dispatch(FilterAction.DeselectTag(vm.tag))
                }
            }

        }
    }

    private val FilterViewState.viewModels: List<TagViewModel>
        get() = tags.map {
            TagViewModel(
                name = it.name,
                icon = it.icon,
                color = it.color,
                isChecked = selectedTags.contains(it),
                tag = it
            )
        }

    private val FilterViewState.petHeadImage
        get() = AndroidPetAvatar.valueOf(petAvatar.name).headImage

}