package io.ipoli.android.habit.list

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v7.widget.GridLayoutManager
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import io.ipoli.android.R
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.list.HabitListViewState.StateType.*
import io.ipoli.android.habit.usecase.CreateHabitItemsUseCase
import kotlinx.android.synthetic.main.controller_habit_list.view.*
import kotlinx.android.synthetic.main.item_habit_list.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/13/18.
 */
class HabitListViewController(args: Bundle? = null) :
    ReduxViewController<HabitListAction, HabitListViewState, HabitListReducer>(args) {

    override val reducer = HabitListReducer

    override var helpConfig: HelpConfig? =
        HelpConfig(
            R.string.help_dialog_habit_list_title,
            R.string.help_dialog_habit_list_message
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_habit_list)

        val gridLayoutManager = GridLayoutManager(view.context, 2)
        view.habitList.layoutManager = gridLayoutManager
        val adapter = HabitListAdapter()
        view.habitList.adapter = adapter

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                when (adapter.getItemViewType(position)) {
                    ViewType.SECTION.value -> 2
                    else -> 1
                }
        }

        view.addHabit.dispatchOnClick { HabitListAction.Add }

        view.emptyAnimation.setAnimation("empty_habit_list.json")

        return view
    }

    override fun onCreateLoadAction() = HabitListAction.Load

    override fun onAttach(view: View) {
        super.onAttach(view)
        enableToolbarTitle()
        toolbarTitle = stringRes(R.string.habits)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.habit_list_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.actionPredefinedHabits -> {
                dispatch(HabitListAction.AddPreset)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: HabitListViewState, view: View) {

        when (state.type) {
            DATA_CHANGED -> {
                view.loader.gone()

                if (state.showEmptyView) {
                    view.emptyContainer.visible()
                    view.emptyAnimation.playAnimation()
                    view.emptyTitle.setText(R.string.empty_habits_title)
                    view.emptyText.setText(R.string.empty_habits_text)
                } else {
                    view.emptyContainer.invisible()
                    view.emptyAnimation.pauseAnimation()
                }

                (view.habitList.adapter as HabitListAdapter).updateAll(state.viewModels)
            }

            SHOW_ADD ->
                navigateFromRoot().toAddHabit()

            SHOW_ADD_PRESET ->
                navigateFromRoot().toPredefinedHabits()

            else -> {

            }
        }
    }

    sealed class ItemViewModel(override val id: String) : RecyclerViewViewModel {
        data class SectionItem(val text: String) : ItemViewModel(text)

        data class TodayItem(
            override val id: String,
            val name: String,
            val icon: IIcon,
            @ColorRes val color: Int,
            @ColorRes val secondaryColor: Int,
            val streak: Int,
            val isBestStreak: Boolean,
            val timesADay: Int,
            val progress: Int,
            val maxProgress: Int,
            val isCompleted: Boolean,
            val canBeCompletedMoreTimes: Boolean,
            val isGood: Boolean,
            val completedCount: Int,
            val showCompletedCount: Boolean,
            val habit: Habit
        ) : ItemViewModel(id)

        data class OtherDayItem(
            override val id: String,
            val name: String,
            val icon: IIcon,
            @ColorRes val color: Int,
            val streak: Int,
            val isBestStreak: Boolean,
            val isGood: Boolean,
            val habit: Habit
        ) : ItemViewModel(id)
    }

    enum class ViewType(val value: Int) {
        SECTION(0),
        TODAY(1),
        ANY_OTHER_DAY(2)
    }

    inner class HabitListAdapter :
        MultiViewRecyclerViewAdapter<ItemViewModel>() {

        override fun onRegisterItemBinders() {

            registerBinder<ItemViewModel.SectionItem>(
                ViewType.SECTION.value,
                R.layout.item_list_section
            ) { vm, view, _ ->
                (view as TextView).text = vm.text
            }

            registerBinder<ItemViewModel.TodayItem>(
                ViewType.TODAY.value,
                R.layout.item_habit_list
            ) { vm, view, _ ->

                renderName(view, vm.name, vm.isGood)
                renderIcon(view, vm.icon, if (vm.isCompleted) R.color.md_white else vm.color)

                if (vm.showCompletedCount) {
                    view.habitCompletedToday.visible()
                    view.habitCompletedToday.text = "x${vm.completedCount}"
                } else {
                    view.habitCompletedToday.gone()
                }

                renderStreak(
                    view = view,
                    streak = vm.streak,
                    isBestStreak = vm.isBestStreak,
                    color = if (vm.isCompleted) R.color.md_white else vm.color,
                    textColor = if (vm.isCompleted) R.color.md_white else colorTextPrimaryResource
                )
                renderCompletedBackground(view, vm.color)

                view.habitProgress.setProgressStartColor(colorRes(vm.color))
                view.habitProgress.setProgressEndColor(colorRes(vm.color))
                view.habitProgress.setProgressBackgroundColor(colorRes(vm.secondaryColor))
                view.habitProgress.setProgressFormatter(null)
                renderProgress(view, vm.progress, vm.maxProgress)

                if (vm.timesADay > 1) {
                    view.habitTimesADayProgress.visible()
                    view.habitTimesADayProgress.setProgressStartColor(attrData(android.R.attr.colorBackground))
                    view.habitTimesADayProgress.setProgressEndColor(attrData(android.R.attr.colorBackground))
                    view.habitTimesADayProgress.setProgressFormatter(null)
                    renderTimesADayProgress(view, vm.progress, vm.maxProgress)
                } else {
                    view.habitTimesADayProgress.gone()
                }

                val habitCompleteBackground = view.habitCompletedBackground
                if (vm.isCompleted) {
                    view.habitProgress.invisible()
                    habitCompleteBackground.visible()
                    view.habitCompletedBackground.setOnLongClickListener {
                        navigateFromRoot().toHabit(vm.habit.id)
                        return@setOnLongClickListener true
                    }
                    view.habitProgress.setOnLongClickListener(null)
                } else {
                    view.habitProgress.visible()
                    habitCompleteBackground.invisible()
                    view.habitProgress.setOnLongClickListener {
                        navigateFromRoot().toHabit(vm.habit.id)
                        return@setOnLongClickListener true
                    }
                    view.habitCompletedBackground.setOnLongClickListener(null)
                }

                view.habitProgress.onDebounceClick {
                    val isLastProgress = vm.maxProgress - vm.progress == 1
                    if (isLastProgress) {
                        startCompleteAnimation(view, vm)
                    } else {
                        dispatch(
                            if (vm.canBeCompletedMoreTimes) HabitListAction.CompleteHabit(vm.id)
                            else HabitListAction.UndoCompleteHabit(vm.id)
                        )
                    }
                }

                view.habitCompletedBackground.onDebounceClick {
                    if (vm.canBeCompletedMoreTimes) {
                        dispatch(HabitListAction.CompleteHabit(vm.id))
                    } else {
                        startUndoCompleteAnimation(view, vm)
                    }
                }
            }

            registerBinder<ItemViewModel.OtherDayItem>(
                ViewType.ANY_OTHER_DAY.value,
                R.layout.item_habit_list
            ) { vm, view, _ ->

                renderName(view, vm.name, vm.isGood)
                renderIcon(view, vm.icon, R.color.md_white)
                renderStreak(view, vm.streak, vm.isBestStreak, R.color.md_white, R.color.md_white)
                renderCompletedBackground(view, vm.color)

                view.habitCompletedBackground.setOnLongClickListener {
                    navigateFromRoot().toHabit(vm.habit.id)
                    return@setOnLongClickListener true
                }
            }

        }

        private fun renderCompletedBackground(
            view: View,
            color: Int
        ): View? {
            val habitCompleteBackground = view.habitCompletedBackground
            val b = habitCompleteBackground.background as GradientDrawable
            b.setColor(colorRes(color))
            return habitCompleteBackground
        }

        private fun renderStreak(
            view: View,
            streak: Int,
            isBestStreak: Boolean,
            textColor: Int,
            color: Int
        ) {
            view.habitStreak.text = streak.toString()
            view.habitStreak.setTextColor(colorRes(textColor))
            if (isBestStreak) {
                view.habitBestProgressIndicator.visible()
                view.habitBestProgressIndicator.setImageDrawable(
                    IconicsDrawable(view.context).normalIcon(
                        GoogleMaterial.Icon.gmd_star,
                        color
                    )
                )
            } else {
                view.habitBestProgressIndicator.gone()
            }
        }

        private fun renderIcon(
            view: View,
            icon: IIcon,
            color: Int
        ) {
            view.habitIcon.setImageDrawable(
                IconicsDrawable(view.context).normalIcon(icon, color)
            )
        }

        private fun renderName(
            view: View,
            name: String,
            isGood: Boolean
        ) {

            view.habitName.text = if (isGood) name else "\u2205 $name"
        }

        private fun renderProgress(
            view: View,
            progress: Int,
            maxProgress: Int
        ) {
            view.habitProgress.max = maxProgress
            view.habitProgress.progress = progress
        }

        private fun renderTimesADayProgress(
            view: View,
            progress: Int,
            maxProgress: Int
        ) {
            view.habitTimesADayProgress.max = maxProgress
            view.habitTimesADayProgress.setLineCount(maxProgress)
            view.habitTimesADayProgress.progress = progress
        }

        private fun startUndoCompleteAnimation(
            view: View,
            vm: ItemViewModel.TodayItem
        ) {
            val hcb = view.habitCompletedBackground
            val half = hcb.width / 2
            val completeAnim = ViewAnimationUtils.createCircularReveal(
                hcb,
                half, half,
                half.toFloat(), 0f
            )
            completeAnim.duration = shortAnimTime
            completeAnim.interpolator = AccelerateDecelerateInterpolator()
            completeAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    view.habitProgress.visible()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    hcb.invisible()
                    view.habitIcon.setImageDrawable(
                        IconicsDrawable(view.context).normalIcon(vm.icon, vm.color)
                    )
                    view.habitStreak.setTextColor(colorRes(colorTextPrimaryResource))
                    renderProgress(view, vm.progress - 1, vm.maxProgress)
                    renderTimesADayProgress(view, vm.progress - 1, vm.maxProgress)

                    dispatch(
                        if (vm.canBeCompletedMoreTimes)
                            HabitListAction.CompleteHabit(vm.id)
                        else
                            HabitListAction.UndoCompleteHabit(vm.id)
                    )
                }
            })
            completeAnim.start()
        }

        private fun startCompleteAnimation(
            view: View,
            vm: ItemViewModel.TodayItem
        ) {
            val hcb = view.habitCompletedBackground
            val half = hcb.width / 2
            val completeAnim = ViewAnimationUtils.createCircularReveal(
                hcb,
                half, half,
                0f, half.toFloat()
            )
            completeAnim.duration = shortAnimTime
            completeAnim.interpolator = AccelerateDecelerateInterpolator()
            completeAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    hcb.visible()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    view.habitIcon.setImageDrawable(
                        IconicsDrawable(view.context).normalIcon(vm.icon, R.color.md_white)
                    )
                    view.habitStreak.setTextColor(colorRes(R.color.md_white))
                    dispatch(
                        if (vm.canBeCompletedMoreTimes) HabitListAction.CompleteHabit(vm.id)
                        else HabitListAction.UndoCompleteHabit(vm.id)
                    )
                }
            })
            completeAnim.start()
        }
    }

    private val HabitListViewState.viewModels: List<ItemViewModel>
        get() =
            habitItems!!.map {
                when (it) {
                    is CreateHabitItemsUseCase.HabitItem.TodaySection ->
                        ItemViewModel.SectionItem(stringRes(R.string.today))

                    is CreateHabitItemsUseCase.HabitItem.AnyOtherDaySection ->
                        ItemViewModel.SectionItem(stringRes(R.string.any_other_day))

                    is CreateHabitItemsUseCase.HabitItem.Today -> {
                        val habit = it.habit
                        ItemViewModel.TodayItem(
                            id = habit.id,
                            name = habit.name,
                            color = habit.color.androidColor.color500,
                            secondaryColor = habit.color.androidColor.color100,
                            icon = habit.icon.androidIcon.icon,
                            timesADay = habit.timesADay,
                            isCompleted = it.isCompleted,
                            isGood = habit.isGood,
                            canBeCompletedMoreTimes = it.canBeCompletedMoreTimes,
                            streak = habit.streak.current,
                            isBestStreak = it.isBestStreak,
                            progress = it.completedCount,
                            maxProgress = habit.timesADay,
                            completedCount = it.completedCount,
                            showCompletedCount = it.habit.isUnlimited && it.completedCount > 1,
                            habit = habit
                        )
                    }

                    is CreateHabitItemsUseCase.HabitItem.OtherDay -> {
                        val habit = it.habit
                        ItemViewModel.OtherDayItem(
                            id = habit.id,
                            name = habit.name,
                            color = habit.color.androidColor.color500,
                            icon = habit.icon.androidIcon.icon,
                            isGood = habit.isGood,
                            streak = habit.streak.current,
                            isBestStreak = it.isBestStreak,
                            habit = habit
                        )
                    }
                }
            }
}