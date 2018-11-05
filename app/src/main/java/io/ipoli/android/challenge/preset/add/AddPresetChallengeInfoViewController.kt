package io.ipoli.android.challenge.preset.add

import android.os.Bundle
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.R
import io.ipoli.android.challenge.add.TextWatcherAdapter
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.subquest.view.ReadOnlySubQuestAdapter
import kotlinx.android.synthetic.main.controller_add_preset_challenge_info.view.*
import kotlinx.android.synthetic.main.item_edit_repeating_quest_sub_quest.view.*
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 11/1/18.
 */
class AddPresetChallengeInfoViewController(args: Bundle? = null) :
    BaseViewController<AddPresetChallengeAction, AddPresetChallengeViewState>(args) {

    override val stateKey = AddPresetChallengeReducer.stateKey

    private val nameWatcher = TextWatcherAdapter { e ->
        view?.let {
            val text = if (e.isBlank())
                stringRes(R.string.name_hint)
            else
                stringRes(R.string.name_hint) + " (${e.length}/50)"
            it.challengeNameLayout.hint = text
        }
    }

    private val shortDescriptionWatcher = TextWatcherAdapter { e ->
        view?.let {
            val text = if (e.isBlank())
                stringRes(R.string.short_desc_hint)
            else
                stringRes(R.string.short_desc_hint) + " (${e.length}/80)"
            it.challengeShortDescriptionLayout.hint = text
        }
    }

    private val newExpectedResultWatcher = TextWatcherAdapter { e ->
        if (e.isBlank()) {
            view?.challengeAddExpectedResult?.invisible()
        } else {
            view?.challengeAddExpectedResult?.visible()
        }
    }
    private val newRequirementsWatcher = TextWatcherAdapter { e ->
        if (e.isBlank()) {
            view?.challengeAddRequirement?.invisible()
        } else {
            view?.challengeAddRequirement?.visible()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_add_preset_challenge_info)
        initExpectedResults(view)
        initRequirements(view)

        view.challengeName.addTextChangedListener(nameWatcher)
        view.challengeShortDescription.addTextChangedListener(shortDescriptionWatcher)

        return view
    }

    override fun onCreateLoadAction() = AddPresetChallengeAction.LoadInfo

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.add_preset_challenge_info_title)
    }

    private fun initExpectedResults(view: View) {
        val adapter =
            ReadOnlySubQuestAdapter(view.challengeExpectedResultList, useLightTheme = true)
        view.challengeExpectedResultList.layoutManager = LinearLayoutManager(view.context)
        view.challengeExpectedResultList.adapter = adapter

        view.expectedResultName.addTextChangedListener(newExpectedResultWatcher)

        view.challengeAddExpectedResult.onDebounceClick {
            val res = view.expectedResultName.text.toString()
            addExpectedResult(res, view)
        }

        view.expectedResultName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val res = view.expectedResultName.text.toString()
                addExpectedResult(res, view)
            }
            true
        }
    }

    private fun addExpectedResult(result: String, view: View) {
        (view.challengeExpectedResultList.adapter as ReadOnlySubQuestAdapter).add(
            ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(UUID.randomUUID().toString(), result)
        )
        view.expectedResultName.setText("")
        view.expectedResultName.requestFocus()
        view.challengeAddExpectedResult.invisible()
    }

    private fun initRequirements(view: View) {
        val adapter = ReadOnlySubQuestAdapter(view.challengeRequirementList, useLightTheme = true)
        view.challengeRequirementList.layoutManager = LinearLayoutManager(view.context)
        view.challengeRequirementList.adapter = adapter

        view.requirementName.addTextChangedListener(newRequirementsWatcher)

        view.challengeAddRequirement.onDebounceClick {
            val req = view.requirementName.text.toString()
            addRequirement(req, view)
        }

        view.requirementName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val req = view.requirementName.text.toString()
                addRequirement(req, view)
            }
            true
        }
    }

    private fun addRequirement(requirement: String, view: View) {
        (view.challengeRequirementList.adapter as ReadOnlySubQuestAdapter).add(
            ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                UUID.randomUUID().toString(),
                requirement
            )
        )
        view.requirementName.setText("")
        view.requirementName.requestFocus()
        view.challengeAddRequirement.invisible()
    }

    override fun onDestroyView(view: View) {
        view.challengeName.removeTextChangedListener(nameWatcher)
        view.challengeShortDescription.removeTextChangedListener(shortDescriptionWatcher)
        view.expectedResultName.removeTextChangedListener(newExpectedResultWatcher)
        view.requirementName.removeTextChangedListener(newRequirementsWatcher)
        super.onDestroyView(view)
    }

    override fun render(state: AddPresetChallengeViewState, view: View) {
        when (state.type) {
            AddPresetChallengeViewState.StateType.DATA_LOADED -> {

                if (state.name.isNotBlank()) {
                    view.challengeName.setText(state.name)
                }

                if (state.shortDescription.isNotBlank()) {
                    view.challengeShortDescription.setText(state.shortDescription)
                }

                renderColor(view, state)
                renderIcon(view, state)

                renderCategory(view, state)

                renderDuration(view, state)
                renderDifficulty(view, state)
                renderDescription(view, state)
                renderExpectedResults(view, state)
                renderRequirements(view, state)

            }

            AddPresetChallengeViewState.StateType.COLOR_CHANGED -> {
                renderColor(view, state)
            }

            AddPresetChallengeViewState.StateType.ICON_CHANGED -> {
                renderIcon(view, state)
            }

            AddPresetChallengeViewState.StateType.DESCRIPTION_CHANGED ->
                renderDescription(view, state)

            AddPresetChallengeViewState.StateType.VALIDATE_TEXTS -> {

                val expectedResults = view.challengeExpectedResultList.children.map {
                    it.editSubQuestName.text.toString()
                }.toList()

                val requirements = view.challengeRequirementList.children.map {
                    it.editSubQuestName.text.toString()
                }.toList()

                dispatch(
                    AddPresetChallengeAction.ValidateInfo(
                        view.challengeName.text.toString(),
                        view.challengeShortDescription.text.toString(),
                        expectedResults,
                        requirements
                    )
                )
            }

            AddPresetChallengeViewState.StateType.ERROR_INFO -> {
                if (state.infoErrors.contains(AddPresetChallengeViewState.InfoError.EMPTY_NAME)) {
                    view.challengeName.error = stringRes(R.string.think_of_a_name)
                }

                if (state.infoErrors.contains(AddPresetChallengeViewState.InfoError.EMPTY_SHORT_DESCRIPTION)) {
                    view.challengeShortDescription.error =
                        stringRes(R.string.error_empty_short_description)
                }
                if (state.infoErrors.contains(AddPresetChallengeViewState.InfoError.EMPTY_DESCRIPTION)) {
                    showShortToast(R.string.error_empty_challenge_description)
                }

                if (state.infoErrors.contains(AddPresetChallengeViewState.InfoError.EMPTY_EXPECTED_RESULTS)) {
                    showShortToast(R.string.error_empty_challenge_expected_results)
                }
            }

            else -> {
            }
        }
    }

    private fun renderIcon(
        view: View,
        state: AddPresetChallengeViewState
    ) {
        view.challengeSelectedIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .largeIcon(state.icon.androidIcon.icon)
        )

        view.challengeIcon.onDebounceClick {
            navigate().toIconPicker({ ic ->
                dispatch(AddPresetChallengeAction.ChangeIcon(ic ?: state.icon))
            }, state.icon)
        }
    }

    private fun renderColor(
        view: View,
        state: AddPresetChallengeViewState
    ) {
        view.challengeColor.onDebounceClick {
            navigate().toColorPicker({ c ->
                dispatch(
                    AddPresetChallengeAction.ChangeColor(c)
                )
            }, state.color)
        }
    }

    private fun renderExpectedResults(view: View, state: AddPresetChallengeViewState) {
        (view.challengeExpectedResultList.adapter as ReadOnlySubQuestAdapter).updateAll(state.expectedResultsViewModels)
    }

    private fun renderRequirements(view: View, state: AddPresetChallengeViewState) {
        (view.challengeRequirementList.adapter as ReadOnlySubQuestAdapter).updateAll(state.requirementViewModels)
    }

    private fun renderDuration(
        view: View,
        state: AddPresetChallengeViewState
    ) {

        view.challengeDuration.onItemSelectedListener = null

        val durations = listOf(7, 15, 30)

        view.challengeDuration.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_dropdown_number_spinner,
            durations.map { it.toString() }
        )

        view.challengeDuration.setSelection(durations.indexOf(state.duration.intValue))

        view.challengeDuration.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    styleSelectedSpinnerItem(view.challengeDuration)
                    dispatch(AddPresetChallengeAction.ChangeDuration(durations[position]))
                }

            }
    }

    private fun renderCategory(
        view: View,
        state: AddPresetChallengeViewState
    ) {

        view.challengeCategory.onItemSelectedListener = null
        val categories = PresetChallenge.Category.values()
        view.challengeCategory.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_dropdown_number_spinner,
            categories.map {
                it.name.toLowerCase().capitalize()
            }
        )

        view.challengeCategory.setSelection(categories.indexOf(state.category))

        view.challengeCategory.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    styleSelectedSpinnerItem(view.challengeCategory)
                    dispatch(AddPresetChallengeAction.ChangeCategory(categories[position]))
                }

            }
    }

    private fun renderDifficulty(
        view: View,
        state: AddPresetChallengeViewState
    ) {

        view.challengeDifficulty.onItemSelectedListener = null
        val difficulties = Challenge.Difficulty.values().toList()

        view.challengeDifficulty.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_dropdown_number_spinner,
            view.resources.getStringArray(R.array.difficulties)
        )
        view.challengeDifficulty.setSelection(difficulties.indexOf(state.difficulty))

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
                    styleSelectedSpinnerItem(view.challengeDifficulty)
                    dispatch(AddPresetChallengeAction.ChangeDifficulty(difficulties[position]))
                }

            }
    }

    private fun renderDescription(view: View, state: AddPresetChallengeViewState) {
        view.challengeDescription.text = state.descriptionText
        view.challengeDescription.onDebounceClick {
            navigate()
                .toNotePicker(
                    note = state.description,
                    resultListener = { d ->
                        dispatch(AddPresetChallengeAction.ChangeDescription(d))
                    }
                )
        }
    }

    private fun styleSelectedSpinnerItem(view: Spinner) {
        if (view.selectedView == null) {
            return
        }
        val item = view.selectedView as TextView
        TextViewCompat.setTextAppearance(item, R.style.TextAppearance_AppCompat_Subhead)
        item.setTextColor(colorRes(R.color.md_light_text_100))
        item.setPadding(0, 0, 0, 0)
    }

    private val AddPresetChallengeViewState.descriptionText: String
        get() = if (description.isBlank()) stringRes(R.string.preset_challenge_description_hint) else description

    private val AddPresetChallengeViewState.expectedResultsViewModels: List<ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel>
        get() = expectedResults.map {
            ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                id = it,
                name = it
            )
        }

    private val AddPresetChallengeViewState.requirementViewModels: List<ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel>
        get() = requirements.map {
            ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                id = it,
                name = it
            )
        }

}