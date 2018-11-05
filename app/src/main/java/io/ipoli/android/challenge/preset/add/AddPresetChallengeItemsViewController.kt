package io.ipoli.android.challenge.preset.add

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mikepenz.iconics.typeface.IIcon
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.add.AddPresetChallengeViewState.StateType.*
import io.ipoli.android.common.datetime.Day
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import kotlinx.android.synthetic.main.controller_add_preset_challenge_items.view.*
import kotlinx.android.synthetic.main.item_add_preset_challenge_habit.view.*
import kotlinx.android.synthetic.main.item_add_preset_challenge_quest.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 11/1/18.
 */
class AddPresetChallengeItemsViewController(args: Bundle? = null) :
    BaseViewController<AddPresetChallengeAction, AddPresetChallengeViewState>(args) {

    override val stateKey = AddPresetChallengeReducer.stateKey

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_add_preset_challenge_items)

        view.questList.layoutManager = LinearLayoutManager(view.context)
        view.questList.adapter = QuestAdapter()

        view.habitList.layoutManager = LinearLayoutManager(view.context)
        view.habitList.adapter = HabitAdapter()

        return view
    }

    override fun onCreateLoadAction() =
        AddPresetChallengeAction.LoadItems

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.add_preset_challenge_items_title)
    }

    override fun render(state: AddPresetChallengeViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                (view.questList.adapter as QuestAdapter).updateAll(state.questViewModels)
                (view.habitList.adapter as HabitAdapter).updateAll(state.habitViewModels)

                view.addQuest.onDebounceClick {
                    navigate().toQuestForPresetChallengePicker(
                        challengeDuration = state.duration,
                        listener = { quest, isRepeating ->
                            dispatch(AddPresetChallengeAction.AddQuest(quest, isRepeating))
                        })
                }

                view.addHabit.onDebounceClick {
                    navigate().toHabitForPresetChallengePicker(listener = { habit ->
                        dispatch(AddPresetChallengeAction.AddHabit(habit))
                    })
                }
            }

            QUESTS_CHANGED ->
                (view.questList.adapter as QuestAdapter).updateAll(state.questViewModels)

            HABITS_CHANGED ->
                (view.habitList.adapter as HabitAdapter).updateAll(state.habitViewModels)

            ERROR_ITEMS ->
                showShortToast(R.string.error_preset_challenge_empty_items)

            else -> {
            }
        }
    }

    data class QuestViewModel(
        override val id: String,
        val name: String,
        @ColorRes val color: Int,
        val icon: IIcon,
        val duration: String,
        val isRepeating: Boolean,
        val day: Int,
        val quest: PresetChallenge.Quest,
        val challengeDuration: Duration<Day>
    ) : RecyclerViewViewModel

    data class HabitViewModel(
        override val id: String,
        val name: String,
        @ColorRes val color: Int,
        val icon: IIcon,
        val habit: PresetChallenge.Habit
    ) : RecyclerViewViewModel

    inner class QuestAdapter :
        BaseRecyclerViewAdapter<QuestViewModel>(R.layout.item_add_preset_challenge_quest) {

        override fun onBindViewModel(vm: QuestViewModel, view: View, holder: SimpleViewHolder) {
            view.questName.text = vm.name
            view.questIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))
            view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))
            view.questDuration.text = vm.duration
            if (vm.isRepeating) {
                view.questRepeatIndicator.visible()
                view.questDay.gone()
            } else {
                view.questRepeatIndicator.gone()
                view.questDay.text = "Day ${vm.day}"
                view.questDay.visible()
            }

            view.removeQuest.onDebounceClick {
                dispatch(AddPresetChallengeAction.RemoveQuest(holder.adapterPosition))
            }

            view.onDebounceClick {
                navigate().toQuestForPresetChallengePicker(
                    challengeDuration = vm.challengeDuration,
                    quest = vm.quest,
                    isRepeating = vm.isRepeating,
                    listener = { quest, isRepeating ->
                        dispatch(
                            AddPresetChallengeAction.UpdateQuest(
                                holder.adapterPosition,
                                quest,
                                isRepeating
                            )
                        )
                    }
                )
            }
        }

    }

    inner class HabitAdapter :
        BaseRecyclerViewAdapter<HabitViewModel>(R.layout.item_add_preset_challenge_habit) {
        override fun onBindViewModel(vm: HabitViewModel, view: View, holder: SimpleViewHolder) {
            view.habitName.text = vm.name
            view.habitIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))
            view.habitIcon.setImageDrawable(smallListItemIcon(vm.icon))
            view.removeHabit.onDebounceClick {
                dispatch(AddPresetChallengeAction.RemoveHabit(holder.adapterPosition))
            }

            view.onDebounceClick {
                navigate().toHabitForPresetChallengePicker(
                    habit = vm.habit,
                    listener = { habit ->
                        dispatch(
                            AddPresetChallengeAction.UpdateHabit(
                                holder.adapterPosition,
                                habit
                            )
                        )
                    }
                )
            }
        }
    }

    private val AddPresetChallengeViewState.questViewModels
        get() = quests.map {
            val q = it.first
            QuestViewModel(
                id = q.toString(),
                name = q.name,
                color = q.color.androidColor.color500,
                icon = q.icon.androidIcon.icon,
                duration = "for ${DurationFormatter.formatShort(q.duration.intValue)}",
                day = q.day,
                isRepeating = it.second,
                quest = q,
                challengeDuration = duration
            )
        }

    private val AddPresetChallengeViewState.habitViewModels
        get() = habits.map {
            HabitViewModel(
                id = it.toString(),
                name = it.name,
                color = it.color.androidColor.color500,
                icon = it.icon.androidIcon.icon,
                habit = it
            )
        }
}