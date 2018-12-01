package io.ipoli.android.challenge.add

import android.os.Bundle
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.tag.widget.TagAdapter
import kotlinx.android.synthetic.main.controller_add_challenge_name.view.*

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

        view.challengeDifficulty.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_add_challenge_difficulty_item,
            R.id.spinnerItemId,
            view.resources.getStringArray(R.array.difficulties)
        )

        view.challengeDifficulty.post {
            styleSelectedDifficulty(view)
            view.challengeDifficulty.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        v: View?,
                        position: Int,
                        id: Long
                    ) {
                        styleSelectedDifficulty(view)
                        dispatch(EditChallengeAction.ChangeDifficulty(position))
                    }

                }

        }

        view.challengeTagList.layoutManager =
            LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
        view.challengeTagList.adapter = TagAdapter(
            removeTagCallback = { t ->
                dispatch(EditChallengeAction.RemoveTag(t))
            },
            addTagCallback = { t ->
                dispatch(EditChallengeAction.AddTag(t.name))
            }
        )
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
                dispatch(
                    EditChallengeAction.ValidateName(
                        view!!.challengeName.text.toString(),
                        view!!.challengeMotivation.text.toString()
                    )
                )
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
        (view.challengeTagList.adapter as TagAdapter).updateAll(state.tagViewModels)

        if (state.maxTagsReached) {
            view.maxTagsMessage.visible()
        } else {
            view.maxTagsMessage.gone()
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
        view.challengeColor.onDebounceClick {
            navigate()
                .toColorPicker(
                    { c ->
                        dispatch(EditChallengeAction.ChangeColor(c))
                    }, state.color
                )
        }
    }

    private fun styleSelectedDifficulty(view: View) {
        view.challengeDifficulty.selectedView?.let {
            val item = it as TextView
            TextViewCompat.setTextAppearance(item, R.style.TextAppearance_AppCompat_Subhead)
            item.setTextColor(colorRes(R.color.md_light_text_100))
            item.setPadding(0, 0, 0, 0)
        }
    }

    private val EditChallengeViewState.iicon: IIcon
        get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist

    private val EditChallengeViewState.tagViewModels: List<TagAdapter.TagViewModel>
        get() {
            val selectedTagIds = selectedTags.map { it.id }.toSet()
            return tags.map {

                val isSelected = selectedTagIds.contains(it.id)

                TagAdapter.TagViewModel(
                    name = it.name,
                    icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                    tag = it,
                    iconColor = if (isSelected) it.color.androidColor.color500 else R.color.md_light_text_70,
                    background = if (isSelected) R.drawable.circle_white else R.drawable.bordered_circle_white_background,
                    isSelected = isSelected,
                    canBeAdded = !maxTagsReached
                )
            }

        }
}