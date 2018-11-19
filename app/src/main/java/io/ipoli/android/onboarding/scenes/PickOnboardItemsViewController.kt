package io.ipoli.android.onboarding.scenes

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.habit.predefined.PredefinedHabit
import io.ipoli.android.onboarding.OnboardData
import io.ipoli.android.onboarding.OnboardData.Tag
import io.ipoli.android.onboarding.scenes.PickOnboardItemsViewState.StateType.*
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import kotlinx.android.synthetic.main.controller_onboard_pick_start_items.view.*
import kotlinx.android.synthetic.main.item_onboard_repeating_quest.view.*
import kotlinx.android.synthetic.main.view_default_toolbar.view.*
import org.threeten.bp.DayOfWeek

sealed class PickOnboardItemsAction : Action {

    object Done : PickOnboardItemsAction()
    object Skip : PickOnboardItemsAction()

    data class Load(
        val repeatingQuests: Set<Pair<RepeatingQuest, OnboardData.Tag?>>,
        val habits: Set<Pair<PredefinedHabit, OnboardData.Tag?>>
    ) : PickOnboardItemsAction()

    data class SelectRepeatingQuest(
        val repeatingQuest: RepeatingQuest,
        val tag: OnboardData.Tag?
    ) :
        PickOnboardItemsAction() {
        override fun toMap() = mapOf(
            "repeatingQuest" to repeatingQuest,
            "tag" to tag
        )
    }

    data class DeselectRepeatingQuest(val repeatingQuest: RepeatingQuest) :
        PickOnboardItemsAction() {
        override fun toMap() = mapOf("repeatingQuest" to repeatingQuest)
    }

    data class SelectHabit(
        val habit: PredefinedHabit,
        val tag: OnboardData.Tag?
    ) : PickOnboardItemsAction() {

        override fun toMap() = mapOf(
            "habit" to habit,
            "tag" to tag
        )
    }

    data class DeselectHabit(val habit: PredefinedHabit) : PickOnboardItemsAction() {
        override fun toMap() = mapOf("habit" to habit)
    }
}

object PickOnboardItemsReducer : BaseViewStateReducer<PickOnboardItemsViewState>() {

    override fun reduce(
        state: AppState,
        subState: PickOnboardItemsViewState,
        action: Action
    ) =
        when (action) {

            is PickOnboardItemsAction.Load ->
                subState.copy(
                    type = PRESET_ITEMS_LOADED,
                    repeatingQuests = action.repeatingQuests,
                    habits = action.habits
                )

            is PickOnboardItemsAction.SelectRepeatingQuest ->
                subState.copy(
                    type = PRESET_ITEMS_LOADED,
                    repeatingQuests = subState.repeatingQuests +
                        Pair(action.repeatingQuest, action.tag)
                )

            is PickOnboardItemsAction.DeselectRepeatingQuest -> {
                val pair = subState.repeatingQuests.find { it.first == action.repeatingQuest }

                subState.copy(
                    type = PRESET_ITEMS_LOADED,
                    repeatingQuests = subState.repeatingQuests - pair!!
                )
            }

            is PickOnboardItemsAction.SelectHabit ->
                subState.copy(
                    type = PRESET_ITEMS_LOADED,
                    habits = subState.habits +
                        Pair(action.habit, action.tag)
                )

            is PickOnboardItemsAction.DeselectHabit -> {
                val pair = subState.habits.find { it.first == action.habit }

                subState.copy(
                    type = PRESET_ITEMS_LOADED,
                    habits = subState.habits - pair!!
                )
            }

            is PickOnboardItemsAction.Done ->
                subState.copy(
                    type = DONE
                )

            is PickOnboardItemsAction.Skip ->
                subState.copy(
                    type = DONE
                )

            else -> subState
        }

    override fun defaultState() =
        PickOnboardItemsViewState(
            INITIAL,
            repeatingQuests = emptySet(),
            habits = emptySet()
        )

    override val stateKey = key<PickOnboardItemsViewState>()
}

data class PickOnboardItemsViewState(
    val type: StateType,
    val repeatingQuests: Set<Pair<RepeatingQuest, OnboardData.Tag?>>,
    val habits: Set<Pair<PredefinedHabit, OnboardData.Tag?>>
) : BaseViewState() {
    enum class StateType {
        INITIAL,
        DONE,
        PRESET_ITEMS_LOADED
    }
}

class PickOnboardItemsViewController(args: Bundle? = null) :
    ReduxViewController<PickOnboardItemsAction, PickOnboardItemsViewState, PickOnboardItemsReducer>(
        args
    ) {
    override val reducer = PickOnboardItemsReducer

    private val presetItemViewModels = mutableListOf<PresetItemViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_onboard_pick_start_items)

        setToolbar(view.toolbar)
        toolbarTitle = stringRes(R.string.onboard_pick_repeating_quests_title)

        presetItemViewModels.addAll(createViewModels())

        view.onboardRepeatingQuests.layoutManager =
            LinearLayoutManager(
                container.context,
                LinearLayoutManager.VERTICAL,
                false
            )
        val adapter = PresetItemAdapter()
        view.onboardRepeatingQuests.adapter = adapter
        adapter.updateAll(presetItemViewModels)

        return view
    }

    private fun createViewModels() = listOf(
        PresetItemViewModel.HabitItem(
            habit = PredefinedHabit(
                name = "Floss",
                color = Color.GREEN,
                icon = Icon.TOOTH,
                isGood = true,
                timesADay = 1,
                days = DayOfWeek.values().toSet()
            ),
            isSelected = true,
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.HabitItem(
            habit = PredefinedHabit(
                name = "Drink a glass of water",
                color = Color.BLUE,
                icon = Icon.GLASS_WATER,
                isGood = true,
                timesADay = 8,
                days = DayOfWeek.values().toSet()
            ),
            isSelected = true,
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_workout),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_workout),
                icon = Icon.FITNESS,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    3
                )
            ),
            isSelected = true,
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_meditate),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_meditate),
                icon = Icon.TREE,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 20,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    3
                )
            ),
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_email),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_email),
                icon = Icon.MAIL,
                color = Color.RED,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    5
                )
            ),
            tag = Tag.WORK
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_read),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_read),
                icon = Icon.BOOK,
                color = Color.BLUE,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    5
                )
            ),
            isSelected = true
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_bike),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_bike),
                icon = Icon.BIKE,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    3
                )
            ),
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_family_dinner),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_family_dinner),
                icon = Icon.RESTAURANT,
                color = Color.PURPLE,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    3
                )
            ),
            tag = Tag.PERSONAL
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_call_friend),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_call_friend),
                icon = Icon.PHONE,
                color = Color.ORANGE,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    2
                )
            ),
            isSelected = true,
            tag = Tag.PERSONAL
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_date_night),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_date_night),
                icon = Icon.HEART,
                color = Color.PINK,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 90,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    1
                )
            ),
            tag = Tag.PERSONAL
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_learn_new_language),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_learn_new_language),
                icon = Icon.ACADEMIC,
                color = Color.BLUE,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    5
                )
            )
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_call_parent),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_call_parent),
                icon = Icon.PHONE,
                color = Color.ORANGE,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    2
                )
            ),
            tag = Tag.PERSONAL
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_walk_the_dog),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_walk_the_dog),
                icon = Icon.PAW,
                color = Color.TEAL,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Daily()
            )
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_take_walk),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_take_walk),
                icon = Icon.TREE,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    3
                )
            ),
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_run),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_run),
                icon = Icon.RUN,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 60,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    2
                )
            ),
            tag = Tag.WELLNESS
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_play_with_cat),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_play_with_cat),
                icon = Icon.PAW,
                color = Color.TEAL,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Daily()
            )
        ),
        PresetItemViewModel.RepeatingQuestItem(
            name = stringRes(R.string.predefined_rq_stretch),
            repeatingQuest = RepeatingQuest(
                name = stringRes(R.string.predefined_rq_stretch),
                icon = Icon.FLOWER,
                color = Color.GREEN,
                reminders = listOf(Reminder.Relative("", 0)),
                duration = 30,
                repeatPattern = RepeatPattern.Flexible.Weekly(
                    2
                )
            ),
            tag = Tag.WELLNESS
        )
    )

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.onboard_pick_repeating_quests_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionDone) {
            dispatch(PickOnboardItemsAction.Done)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction(): PickOnboardItemsAction? {
        val rqs = mutableSetOf<Pair<RepeatingQuest, OnboardData.Tag?>>()
        val hs = mutableSetOf<Pair<PredefinedHabit, OnboardData.Tag?>>()
        presetItemViewModels.forEach { vm ->
            when (vm) {
                is PresetItemViewModel.RepeatingQuestItem -> {
                    if (vm.isSelected) {
                        rqs.add(vm.repeatingQuest to vm.tag)
                    }
                }

                is PresetItemViewModel.HabitItem -> {
                    if (vm.isSelected) {
                        hs.add(vm.habit to vm.tag)
                    }
                }
            }
        }

        return PickOnboardItemsAction.Load(rqs, hs)
    }

    override fun render(state: PickOnboardItemsViewState, view: View) {
        if (state.type == PRESET_ITEMS_LOADED) {
            (view.onboardRepeatingQuests.adapter as PresetItemAdapter).updateAll(state.itemViewModels)
        } else if (state.type == DONE) {
            val onboardData = OnboardData(
                repeatingQuests = state.repeatingQuests,
                habits = state.habits
            )

            navigate().setAuth(onboardData = onboardData, changeHandler = HorizontalChangeHandler())
        }
    }

    sealed class PresetItemViewModel(override val id: String) : RecyclerViewViewModel {

        data class RepeatingQuestItem(
            val name: String,
            val repeatingQuest: RepeatingQuest,
            val isSelected: Boolean = false,
            val tag: OnboardData.Tag? = null
        ) : PresetItemViewModel(name)

        data class HabitItem(
            val habit: PredefinedHabit,
            val isSelected: Boolean = false,
            val tag: OnboardData.Tag? = null
        ) : PresetItemViewModel(habit.name)
    }

    enum class ViewType(val value: Int) {
        REPEATING_QUEST(0),
        HABIT(1)
    }

    inner class PresetItemAdapter : MultiViewRecyclerViewAdapter<PresetItemViewModel>() {

        override fun onRegisterItemBinders() {

            registerBinder<PresetItemViewModel.RepeatingQuestItem>(
                ViewType.REPEATING_QUEST.value,
                R.layout.item_onboard_repeating_quest
            ) { vm, view, _ ->
                view.rqName.text = vm.name
                view.rqRepeatPattern.text = "${vm.repeatingQuest.repeatPattern.countInPeriod} x week"

                view.rqRepeatPattern.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    IconicsDrawable(view.context)
                        .icon(GoogleMaterial.Icon.gmd_autorenew)
                        .colorRes(R.color.md_dark_text_54)
                        .sizeDp(14),
                    null, null, null
                )

                view.rqIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.repeatingQuest.color.androidColor.color500))
                view.rqIcon.setImageDrawable(listItemIcon(vm.repeatingQuest.icon!!.androidIcon.icon))

                view.rqCheck.setOnCheckedChangeListener(null)
                view.rqCheck.isChecked = vm.isSelected

                view.setOnClickListener {
                    view.rqCheck.toggle()
                }

                view.rqCheck.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        dispatch(
                            PickOnboardItemsAction.SelectRepeatingQuest(
                                vm.repeatingQuest,
                                vm.tag
                            )
                        )
                    } else {
                        dispatch(PickOnboardItemsAction.DeselectRepeatingQuest(vm.repeatingQuest))
                    }
                }
            }

            registerBinder<PresetItemViewModel.HabitItem>(
                ViewType.HABIT.value,
                R.layout.item_onboard_repeating_quest
            ) { vm, view, _ ->

                val h = vm.habit

                view.rqName.text = h.name

                view.rqRepeatPattern.text =
                    if (h.timesADay == 1) "Once per day" else "${h.timesADay} x day"

                view.rqRepeatPattern.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    IconicsDrawable(view.context)
                        .icon(GoogleMaterial.Icon.gmd_favorite)
                        .colorRes(R.color.md_red_500)
                        .sizeDp(12),
                    null, null, null
                )

                view.rqIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(h.color.androidColor.color500))
                view.rqIcon.setImageDrawable(listItemIcon(h.icon.androidIcon.icon))

                view.rqCheck.setOnCheckedChangeListener(null)
                view.rqCheck.isChecked = vm.isSelected

                view.setOnClickListener {
                    view.rqCheck.toggle()
                }

                view.rqCheck.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        dispatch(
                            PickOnboardItemsAction.SelectHabit(
                                h,
                                vm.tag
                            )
                        )
                    } else {
                        dispatch(PickOnboardItemsAction.DeselectHabit(h))
                    }
                }
            }
        }
    }

    private val PickOnboardItemsViewState.itemViewModels
        get() = presetItemViewModels.map {
            when (it) {
                is PresetItemViewModel.HabitItem -> {
                    it.copy(isSelected = habits.map { p -> p.first }.contains(it.habit))
                }

                is PresetItemViewModel.RepeatingQuestItem -> {
                    it.copy(isSelected = repeatingQuests.map { p -> p.first }.contains(it.repeatingQuest))
                }
            }
        }


}