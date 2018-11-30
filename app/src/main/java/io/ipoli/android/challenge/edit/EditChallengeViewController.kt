package io.ipoli.android.challenge.edit

import android.app.DatePickerDialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.AdapterView
import android.widget.TextView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.Constants.Companion.DECIMAL_FORMATTER
import io.ipoli.android.R
import io.ipoli.android.challenge.add.AddChallengeTrackedValueViewController
import io.ipoli.android.challenge.add.EditChallengeAction
import io.ipoli.android.challenge.add.EditChallengeReducer
import io.ipoli.android.challenge.add.EditChallengeViewState
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_add_challenge_summary.view.*
import kotlinx.android.synthetic.main.controller_edit_challenge.view.*
import kotlinx.android.synthetic.main.item_challenge_summary_tracked_value.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/12/18.
 */
open class EditChallengeViewController(args: Bundle? = null) :
    ReduxViewController<EditChallengeAction, EditChallengeViewState, EditChallengeReducer>(args) {

    override val reducer = EditChallengeReducer

    private lateinit var challengeId: String

    constructor(
        challengeId: String
    ) : this() {
        this.challengeId = challengeId
    }

    override fun onCreateLoadAction() =
        EditChallengeAction.Load(challengeId)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_edit_challenge)
        setToolbar(view.toolbar)
        toolbarTitle = stringRes(R.string.title_edit_challenge)

        listOf(
            view.questsTopDivider,
            view.questsTitle,
            view.questsContainer,
            view.questsBottomDivider
        ).forEach { it.gone() }

        view.challengeTagList.layoutManager = LinearLayoutManager(activity!!)
        view.challengeTagList.adapter = EditItemTagAdapter(removeTagCallback = {
            dispatch(EditChallengeAction.RemoveTag(it))
        })

        view.challengeTargetValueList.layoutManager = LinearLayoutManager(view.context)
        view.challengeTargetValueList.adapter = TrackedValueAdapter()

        view.challengeAverageValueList.layoutManager = LinearLayoutManager(view.context)
        view.challengeAverageValueList.adapter = TrackedValueAdapter()

        view.addChallengeTargetValue.onDebounceClick {
            navigate().toTargetValuePicker(targetValueSelectedListener = { t ->
                dispatch(
                    EditChallengeAction.AddTargetTrackedValue(t)
                )
            })
        }

        view.addChallengeAverageValue.onDebounceClick {
            navigate().toMaintainAverageValuePicker(trackedValueSelectedListener = { t ->
                dispatch(
                    EditChallengeAction.AddAverageTrackedValue(t)
                )
            })
        }

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_challenge_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                router.handleBack()
            }
            R.id.actionSave -> {
                dispatch(
                    EditChallengeAction.ValidateName(
                        view!!.challengeName.text.toString()
                    )
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun colorStatusBars() {

    }

    override fun render(state: EditChallengeViewState, view: View) {
        when (state.type) {
            DATA_CHANGED -> {
                view.challengeName.setText(state.name)
                renderTags(view, state)
                renderMotivations(view, state)
                renderTrackedValues(view, state)
                renderEndDate(view, state)
                renderDifficulty(view, state)
                renderIcon(view, state)
                renderColor(view, state)
                renderNote(view, state)
            }

            TAGS_CHANGED -> {
                renderTags(view, state)
            }

            ICON_CHANGED -> {
                renderIcon(view, state)
            }

            COLOR_CHANGED -> {
                renderColor(view, state)
            }

            END_DATE_CHANGED -> {
                renderEndDate(view, state)
            }

            MOTIVATIONS_CHANGED -> {
                renderMotivations(view, state)
            }

            NOTE_CHANGED -> {
                renderNote(view, state)
            }

            VALIDATION_ERROR_EMPTY_NAME -> {
                view.challengeName.error = stringRes(R.string.think_of_a_name)
            }

            VALIDATION_NAME_SUCCESSFUL -> {
                dispatch(EditChallengeAction.Save)
                router.popCurrentController()
            }

            TRACKED_VALUES_CHANGED -> {
                renderTrackedValues(view, state)
            }

            else -> {
            }
        }
    }

    private fun renderTags(
        view: View,
        state: EditChallengeViewState
    ) {
        (view.challengeTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
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

    private fun renderTrackedValues(
        view: View,
        state: EditChallengeViewState
    ) {
        (view.challengeTargetValueList.adapter as TrackedValueAdapter).updateAll(state.trackTargetViewModels)
        (view.challengeAverageValueList.adapter as TrackedValueAdapter).updateAll(state.trackAverageViewModels)
    }

    private fun renderNote(view: View, state: EditChallengeViewState) {
        view.challengeNote.text = state.noteText
        view.challengeNote.onDebounceClick {
            navigate()
                .toNotePicker(
                    state.note
                ) { note ->
                    dispatch(EditChallengeAction.ChangeNote(note))
                }
        }
    }

    private fun renderColor(
        view: View,
        state: EditChallengeViewState
    ) {
        colorLayout(view, state)
        view.challengeColor.onDebounceClick { _ ->
            navigate()
                .toColorPicker(
                    {
                        dispatch(EditChallengeAction.ChangeColor(it))
                    }, state.color
                )
        }
    }

    private fun renderIcon(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeSelectedIcon.setImageDrawable(state.iconDrawable)
        view.challengeIcon.onDebounceClick {
            navigate()
                .toIconPicker({ icon ->
                    dispatch(EditChallengeAction.ChangeIcon(icon))
                }, state.icon)
        }
    }

    private fun renderDifficulty(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeDifficulty.setSelection(state.difficultyIndex)
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
                    dispatch(EditChallengeAction.ChangeDifficulty(position))
                    styleSelectedDifficulty(view)
                }
            }
    }

    private fun styleSelectedDifficulty(view: View) {
        val item = view.challengeDifficulty.selectedView as TextView
        TextViewCompat.setTextAppearance(item, R.style.TextAppearance_AppCompat_Subhead)
        item.setTextColor(colorRes(R.color.md_light_text_100))
        item.setPadding(0, 0, 0, 0)
    }

    private fun renderEndDate(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeEndDate.text = state.endDateText
        val date = state.end
        view.challengeEndDate.onDebounceClick {
            DatePickerDialog(
                view.context,
                DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    dispatch(
                        EditChallengeAction.ChangeEndDate(
                            LocalDate.of(year, month + 1, dayOfMonth)
                        )
                    )
                }, date.year, date.month.value - 1, date.dayOfMonth
            ).show()
        }
    }

    private fun renderMotivations(
        view: View,
        state: EditChallengeViewState
    ) {
        if (state.motivation1.isNotEmpty()) {
            view.challengeMotivation1.visibility = View.VISIBLE
            view.challengeMotivation1.text = state.motivation1
        } else {
            view.challengeMotivation1.visibility = View.GONE
        }
        if (state.motivation2.isNotEmpty()) {
            view.challengeMotivation2.visibility = View.VISIBLE
            view.challengeMotivation2.text = state.motivation2
        } else {
            view.challengeMotivation2.visibility = View.GONE
        }
        if (state.motivation3.isNotEmpty()) {
            view.challengeMotivation3.visibility = View.VISIBLE
            view.challengeMotivation3.text = state.motivation3
        } else {
            view.challengeMotivation3.visibility = View.GONE
        }

        view.challengeMotivations.onDebounceClick {
            navigate().toChallengeMotivations(
                motivation1 = state.motivation1,
                motivation2 = state.motivation2,
                motivation3 = state.motivation3,
                listener = { m1, m2, m3 ->
                    dispatch(EditChallengeAction.ChangeMotivations(m1, m2, m3))
                }
            )
        }
    }

    private fun colorLayout(
        view: View,
        state: EditChallengeViewState
    ) {
        val color500 = colorRes(state.color500)
        val color700 = colorRes(state.color700)
        view.appbar.setBackgroundColor(color500)
        view.toolbar.setBackgroundColor(color500)
        view.rootContainer.setBackgroundColor(color500)
        activity?.window?.navigationBarColor = color500
        activity?.window?.statusBarColor = color700
    }

    inner class TrackedValueAdapter :
        BaseRecyclerViewAdapter<AddChallengeTrackedValueViewController.TrackedValueViewModel>(R.layout.item_challenge_summary_tracked_value) {

        override fun onBindViewModel(
            vm: AddChallengeTrackedValueViewController.TrackedValueViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.expectedResultRemove.setImageDrawable(
                IconicsDrawable(view.context).normalIcon(
                    GoogleMaterial.Icon.gmd_close,
                    R.color.md_light_text_70
                ).respectFontBounds(true)
            )
            view.expectedResultRemove.onDebounceClick {
                navigate().toConfirmation(
                    stringRes(R.string.dialog_confirmation_title),
                    stringRes(R.string.dialog_remove_tracked_value_message)
                ) {
                    dispatch(EditChallengeAction.RemoveTrackedValue(vm.id))
                }
            }
            view.expectedResultText.text = vm.text

            when (vm.trackedValue) {

                is Challenge.TrackedValue.Target ->
                    view.onDebounceClick {
                        navigate().toTargetValuePicker(
                            targetValueSelectedListener = { t ->
                                dispatch(EditChallengeAction.UpdateTrackedValue(t))
                            },
                            trackedValue = vm.trackedValue
                        )
                    }

                is Challenge.TrackedValue.Average ->
                    view.onDebounceClick {
                        navigate().toMaintainAverageValuePicker(
                            trackedValueSelectedListener = { t ->
                                dispatch(EditChallengeAction.UpdateTrackedValue(t))
                            },
                            trackedValue = vm.trackedValue
                        )
                    }

                else -> view.setOnClickListener(null)
            }
        }
    }

    private val EditChallengeViewState.trackTargetViewModels: List<AddChallengeTrackedValueViewController.TrackedValueViewModel>
        get() = trackedValues
            .filterIsInstance(Challenge.TrackedValue.Target::class.java)
            .map {
                AddChallengeTrackedValueViewController.TrackedValueViewModel(
                    id = it.id,
                    text = "Reach ${DECIMAL_FORMATTER.format(it.targetValue)} ${it.units} ${it.name}",
                    trackedValue = it
                )
            }

    private val EditChallengeViewState.trackAverageViewModels: List<AddChallengeTrackedValueViewController.TrackedValueViewModel>
        get() = trackedValues
            .filterIsInstance(Challenge.TrackedValue.Average::class.java)
            .map {
                AddChallengeTrackedValueViewController.TrackedValueViewModel(
                    id = it.id,
                    text = "Maintain ${DECIMAL_FORMATTER.format(it.targetValue)} ${it.units} ${it.name}",
                    trackedValue = it
                )
            }

    private val EditChallengeViewState.color500: Int
        get() = color.androidColor.color500

    private val EditChallengeViewState.color700: Int
        get() = color.androidColor.color700

    private val EditChallengeViewState.endDateText: String
        get() = stringRes(R.string.ends_at_date, DateFormatter.format(view!!.context, end))

    private val EditChallengeViewState.difficultyIndex: Int
        get() = difficulty.ordinal

    private val EditChallengeViewState.iconDrawable: Drawable
        get() =
            if (icon == null) {
                ContextCompat.getDrawable(view!!.context, R.drawable.ic_icon_white_24dp)!!
            } else {
                val androidIcon = icon.androidIcon
                IconicsDrawable(view!!.context)
                    .largeIcon(androidIcon.icon)
            }

    private val EditChallengeViewState.noteText: String
        get() = if (note.isBlank()) stringRes(R.string.tap_to_add_note) else note

    private val EditChallengeViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
        get() = tags.map {
            EditItemTagAdapter.TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it
            )
        }
}