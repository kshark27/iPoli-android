package io.ipoli.android.challenge.add

import android.graphics.PorterDuff
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import kotlinx.android.synthetic.main.controller_add_challenge_name.view.*
import kotlinx.android.synthetic.main.item_choose_tag.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/8/18.
 */
class AddChallengeNameViewController(args: Bundle? = null) :
    BaseViewController<EditChallengeAction, EditChallengeViewState>(
        args
    ) {
    override val stateKey = EditChallengeReducer.stateKey

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_add_challenge_name)

        view.challengeDifficulty.background.setColorFilter(
            colorRes(R.color.md_white),
            PorterDuff.Mode.SRC_ATOP
        )
        view.challengeDifficulty.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_add_challenge_difficulty_item,
            R.id.spinnerItemId,
            view.resources.getStringArray(R.array.difficulties)
        )

        view.challengeDifficulty.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    dispatch(EditChallengeAction.ChangeDifficulty(position))
                }

            }

        view.challengeTagList.layoutManager =
            LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        view.challengeTagList.adapter = TagAdapter()
//        view.challengeTagList.adapter = EditItemTagAdapter(removeTagCallback = {
//            dispatch(EditChallengeAction.RemoveTag(it))
//        })
        return view
    }

    override fun onCreateLoadAction() = EditChallengeAction.LoadTags

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.next_wizard_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.actionNext -> {
                dispatch(EditChallengeAction.ValidateName(view!!.challengeName.text.toString()))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: EditChallengeViewState, view: View) {
        renderColor(view, state)
        renderIcon(view, state)
        when (state.type) {
            TAGS_CHANGED -> {
                renderTags(view, state)
            }

            VALIDATION_ERROR_EMPTY_NAME -> {
                view.challengeName.error = stringRes(R.string.think_of_a_name)
            }

            VALIDATION_NAME_SUCCESSFUL -> {
                dispatch(EditChallengeAction.ShowNext)
            }
            else -> {
            }
        }
    }

    override fun colorStatusBars() {}

    private fun renderTags(
        view: View,
        state: EditChallengeViewState
    ) {
//        (view.challengeTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
        (view.challengeTagList.adapter as TagAdapter).updateAll(state.tagViewModels)
        val add = view.addChallengeTag
        if (state.maxTagsReached) {
            add.gone()
            view.maxTagsMessage.visible()
        } else {
            add.visible()
            view.maxTagsMessage.gone()

            val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
            add.setAdapter(adapter)
            add.setOnItemClickListener { _, _, position, _ ->
                dispatch(EditChallengeAction.AddTag(adapter.getItem(position).name))
                add.setText("")
            }
            add.threshold = 0
            add.setOnTouchListener { _, _ ->
                add.showDropDown()
                false
            }
        }
    }

    private fun renderIcon(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeSelectedIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .largeIcon(state.iicon)
        )

        view.challengeIcon.onDebounceClick {
            navigate().toIconPicker(
                { icon ->
                    dispatch(EditChallengeAction.ChangeIcon(icon))
                }, state.icon
            )

        }
    }

    private fun renderColor(
        view: View,
        state: EditChallengeViewState
    ) {
        colorLayout(view, state)
        view.challengeColor.onDebounceClick {
            navigate()
                .toColorPicker(
                    { c ->
                        dispatch(EditChallengeAction.ChangeColor(c))
                    }, state.color
                )
        }
    }

    private fun colorLayout(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeDifficulty.setPopupBackgroundResource(state.color.androidColor.color500)
    }

    private val EditChallengeViewState.iicon: IIcon
        get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist

    private val EditChallengeViewState.tagViewModels
        get() = tags.map {

            val isSelected = selectedTagIds.contains(it.id)

            TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it,
                iconColor = if (isSelected) it.color.androidColor.color500 else R.color.md_light_text_70,
                background = if (isSelected) R.drawable.circle_white else R.drawable.bordered_circle_white_background,
                isSelected = isSelected
            )
        }

    data class TagViewModel(
        val name: String,
        val icon: IIcon,
        val tag: Tag,
        @ColorRes val iconColor: Int,
        @DrawableRes val background: Int,
        val isSelected: Boolean
    ) : RecyclerViewViewModel {
        override val id: String
            get() = tag.id
    }

    inner class TagAdapter : BaseRecyclerViewAdapter<TagViewModel>(R.layout.item_choose_tag) {

        override fun onBindViewModel(vm: TagViewModel, view: View, holder: SimpleViewHolder) {

            view.tagIcon.setImageDrawable(
                IconicsDrawable(view.context)
                    .listItemIcon(
                        vm.icon,
                        vm.iconColor
                    )
            )
            view.tagBackground.setBackgroundResource(vm.background)

            if (vm.isSelected) {
                view.dispatchOnClick {
                    EditChallengeAction.RemoveTag(vm.tag)
                }
            } else {
                view.dispatchOnClick {
                    EditChallengeAction.AddTag(vm.tag.name)
                }
            }
        }

    }
}