package io.ipoli.android.quest.show

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.transition.ChangeBounds
import android.support.transition.TransitionManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.ReorderItemHelper
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import kotlinx.android.synthetic.main.controller_quest.view.*
import kotlinx.android.synthetic.main.item_quest_sub_quest.view.*
import kotlinx.android.synthetic.main.item_timer_progress.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

class QuestViewController : ReduxViewController<QuestAction, QuestViewState, QuestReducer> {

    override val reducer = QuestReducer

    override var helpConfig: HelpConfig? =
        HelpConfig(R.string.help_dialog_quest_title, R.string.help_dialog_quest_message)

    private var questId = ""
    private var showBackButton = true
    private var isTimerStopped = true

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var touchHelper: ItemTouchHelper

    private val appBarOffsetListener = object :
        AppBarStateChangeListener() {
        override fun onStateChanged(appBarLayout: AppBarLayout, state: State) {

            appBarLayout.post {
                if (state == State.EXPANDED) {
                    (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(
                        false
                    )
                    view?.clearFocus()
                } else if (state == State.COLLAPSED) {
                    (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(true)
                }
            }
        }
    }

    @Suppress("unused")
    constructor(args: Bundle? = null) : super(args)

    constructor(questId: String, showBackButton: Boolean) : super() {
        this.questId = questId
        this.showBackButton = showBackButton
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_quest)

        setToolbar(view.toolbar)
        view.collapsingToolbarContainer.isTitleEnabled = false

        view.appbar.addOnOffsetChangedListener(appBarOffsetListener)

        view.questStartTimer.onDebounceClick {
            dispatch(QuestAction.Start)
        }

        view.questSubQuestList.layoutManager = LinearLayoutManager(activity!!)
        view.questSubQuestList.adapter = SubQuestAdapter()

        val dragHelper =
            ReorderItemHelper(
                onItemMoved = { oldPosition, newPosition ->
                    (view.questSubQuestList.adapter as SubQuestAdapter).move(
                        oldPosition,
                        newPosition
                    )
                },
                onItemReordered = { oldPosition, newPosition ->
                    dispatch(QuestAction.ReorderSubQuest(oldPosition, newPosition))
                }
            )

        touchHelper = ItemTouchHelper(dragHelper)
        touchHelper.attachToRecyclerView(view.questSubQuestList)

        view.newSubQuestName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                disableEditForAllSubQuests()
            }
        }

        val icon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_android_done)
            .colorRes(R.color.md_light_text_70)
            .sizeDp(22)

        view.complete.setImageDrawable(icon)

        val minusIcon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_minus)
            .colorRes(R.color.md_light_text_70)
            .sizeDp(26)
            .paddingDp(6)

        view.removePomodoro.setImageDrawable(minusIcon)
        view.removePomodoroInBreak.setImageDrawable(minusIcon)

        val addIcon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_plus)
            .colorRes(R.color.md_light_text_70)
            .sizeDp(26)
            .paddingDp(6)

        view.addPomodoro.setImageDrawable(addIcon)
        view.addPomodoroInBreak.setImageDrawable(addIcon)

        view.timerProgressCircle.setProgressFormatter(null)

        view.subQuestsHintIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_info_outline)
                .color(attrData(R.attr.colorAccent))
                .sizeDp(24)
        )

        view.questDetailsContainer.setOnClickListener {
            view.questContainer.requestFocus()
        }

        view.addPomodoro.dispatchOnClick { QuestAction.AddPomodoro }
        view.addPomodoroInBreak.dispatchOnClick { QuestAction.AddPomodoro }

        view.removePomodoro.dispatchOnClick { QuestAction.RemovePomodoro }
        view.removePomodoroInBreak.dispatchOnClick { QuestAction.RemovePomodoro }

        view.timerTypeCountdownButton.onDebounceClick {
            onSwipeRight(view)
        }

        view.timerTypePomodoroButton.isClickable = false
        view.timerTypePomodoroButton.setBackgroundResource(R.drawable.bordered_rectangle_white_background)
        view.timerTypeCountdownButton.isClickable = true
        view.timerTypeCountdownButton.setBackgroundResource(attrResourceId(android.R.attr.selectableItemBackground))

        return view
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.actionMarkDone).isVisible = isTimerStopped
        menu.findItem(R.id.actionEdit).isVisible = isTimerStopped
        menu.findItem(R.id.actionRemove).isVisible = isTimerStopped
        super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.quest_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home ->
                return router.handleBack()

            R.id.actionMarkDone ->
                dispatch(QuestAction.CompleteQuest)

            R.id.actionEdit ->
                navigateFromRoot().toEditQuest(questId, changeHandler = HorizontalChangeHandler())

            R.id.actionRemove -> {
                dispatch(QuestAction.Remove(questId))
                activity?.let {
                    PetMessagePopup(
                        stringRes(R.string.remove_quest_undo_message),
                        { dispatch(QuestAction.UndoRemove(questId)) },
                        stringRes(R.string.undo)
                    ).show(it)
                }
                router.handleBack()
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction() = QuestAction.Load(questId)

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (showBackButton) {
            showBackButton()
        }
        view.questContainer.requestFocus()
        val showTitle =
            appBarOffsetListener.currentState != AppBarStateChangeListener.State.EXPANDED
        (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(showTitle)
    }

    override fun onDetach(view: View) {
        view.appbar.setOnTouchListener(null)
        handler.removeCallbacksAndMessages(null)
        cancelAnimations(view)
        super.onDetach(view)
    }

    override fun onDestroyView(view: View) {
        view.appbar.removeOnOffsetChangedListener(appBarOffsetListener)
        super.onDestroyView(view)
    }

    private fun colorLayout(
        state: QuestViewState,
        view: View
    ) {
        view.timerProgressCircle.setProgressBackgroundColor(colorRes(state.color700))
        view.appbar.setBackgroundColor(colorRes(state.color500))
        view.toolbar.setBackgroundColor(colorRes(state.color500))
        view.collapsingToolbarContainer.setContentScrimColor(colorRes(state.color500))
        activity?.window?.navigationBarColor = colorRes(state.color500)
        activity?.window?.statusBarColor = colorRes(state.color700)
    }

    override fun render(state: QuestViewState, view: View) {

        if (
            state.type != QuestViewState.StateType.TIMER_TICK &&
            state.type != QuestViewState.StateType.QUEST_COMPLETED
        ) {
            renderSubQuests(state, view)
            renderNote(state, view)
        }

        when (state.type) {

            QuestViewState.StateType.LOADING -> {
                view.questContainer.gone()
                view.loader.visible()
            }

            QuestViewState.StateType.SHOW_POMODORO -> {

                showDataContainer(view)

                showInfoAppBar(view)

                view.complete.gone()

                colorLayout(state, view)

                view.questName.text = state.questName
                view.questNameInfo.text = state.questName
                toolbarTitle = state.questName

                view.questStartTimer.visible()
                view.questPomodoroCountText.text = state.pomodoroTimerText
                view.questCountdownTime.text = state.countdownTimerText

                view.timerPreviewGroup.visible()
                view.timerRunningGroup.gone()
                view.timerProgressScroll.gone()

                isTimerStopped = true
                activity?.invalidateOptionsMenu()

                renderTimerSwitching(state, view) {
                    if (view.timerType.displayedChild == 1) {
                        view.timerType.showPrevious()
                    }
                }
            }

            QuestViewState.StateType.SHOW_COUNTDOWN -> {

                showDataContainer(view)

                showInfoAppBar(view)

                view.complete.gone()
                view.questStartTimer.visible()

                colorLayout(state, view)

                view.questName.text = state.questName
                view.questNameInfo.text = state.questName
                toolbarTitle = state.questName

                renderTimerProgress(view, state)
                view.startStop.dispatchOnClick { QuestAction.Start }

                view.questStartTimer.visible()
                view.questPomodoroCountText.text = state.pomodoroTimerText
                view.questCountdownTime.text = state.countdownTimerText

                view.timerPreviewGroup.visible()
                view.timerRunningGroup.gone()
                view.timerProgressScroll.gone()

                isTimerStopped = true
                activity?.invalidateOptionsMenu()
                renderTimerSwitching(state, view) {
                    if (view.timerType.displayedChild == 0) {
                        view.timerType.showNext()
                    }
                }
            }

            QuestViewState.StateType.RESUMED -> {

                showDataContainer(view)

                view.appbar.setOnTouchListener(null)
                view.questStartTimer.gone()

                showTimerAppBar(view)

                view.timerLabel.text = state.timerLabel
                colorLayout(state, view)
                view.questName.text = state.questName
                view.questNameInfo.text = state.questName
                toolbarTitle = state.questName
                startTimer(view, state)

                isTimerStopped = false
                activity?.invalidateOptionsMenu()

                renderPomodoroTimerControls(state, view)

            }

            QuestViewState.StateType.TIMER_TICK -> {
                view.timerLabel.text = state.timerLabel
                renderTimerProgress(view, state)

                if (state.showCompleteButton) {
                    renderTimerButton(view.startStop, TimerButton.DONE)
                    view.startStop.dispatchOnClick { QuestAction.CompletePomodoro }
                }

                renderPomodoroTimerControls(state, view)
            }

            QuestViewState.StateType.TIMER_REPLACED ->
                showShortToast(R.string.timer_replaced)

            QuestViewState.StateType.TIMER_STOPPED -> {
                showInfoAppBar(view)
                handler.removeCallbacksAndMessages(null)
                cancelAnimations(view)

                renderTimerButton(view.startStop, TimerButton.START)
                view.startStop.dispatchOnClick { QuestAction.Start }
                view.complete.visibility = View.GONE

                view.addPomodoroInBreak.gone()
                view.removePomodoroInBreak.gone()
            }

            QuestViewState.StateType.SUB_QUEST_ADDED ->
                view.newSubQuestName.setText("")

            QuestViewState.StateType.QUEST_COMPLETED ->
                navigateFromRoot().replaceWithCompletedQuest(questId, HorizontalChangeHandler())

            else -> {
            }
        }
    }

    private fun renderNote(state: QuestViewState, view: View) {
        view.questNote.setMarkdown(state.noteText)
        view.questNote.onDebounceClick {
            val noteText = if (state.note == null || state.note.isBlank()) "" else state.note
            navigateFromRoot().toNotePicker(noteText) { newNote ->
                dispatch(QuestAction.SaveNote(newNote))
            }
        }
    }

    private fun showTimerAppBar(view: View) {
        TransitionManager.beginDelayedTransition(view.appbar, ChangeBounds())
        val p = view.appbar.layoutParams
        p.height = CoordinatorLayout.LayoutParams.MATCH_PARENT
        view.appbar.layoutParams = p
    }

    private fun showInfoAppBar(view: View) {
        TransitionManager.beginDelayedTransition(view.appbar, ChangeBounds())
        val p = view.appbar.layoutParams
        p.height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
        view.appbar.layoutParams = p
    }

    private fun renderPomodoroTimerControls(
        state: QuestViewState,
        view: View
    ) {
        if (state.isInBreak) {
            view.addPomodoroInBreak.visible()
            view.removePomodoroInBreak.visible()
        } else {
            view.addPomodoroInBreak.invisible()
            view.removePomodoroInBreak.invisible()
        }
    }

    private fun showDataContainer(view: View) {
        view.questContainer.visible()
        view.loader.gone()
    }

    private fun renderTimerSwitching(
        state: QuestViewState,
        view: View,
        onNotShow: () -> Unit
    ) {
        if (state.showTimerTypeSwitch) {
            view.timerTypePomodoroButton.visible()
            view.timerTypeCountdownButton.visible()
        } else {
            view.appbar.setOnTouchListener(null)
            view.timerTypePomodoroButton.invisible()
            view.timerTypeCountdownButton.invisible()
            onNotShow()
        }
    }

    private fun onSwipeRight(view: View) {
        view.timerType.setInAnimation(view.context, R.anim.left_in)
        view.timerType.setOutAnimation(view.context, R.anim.left_out)

        view.timerType.showNext()

        view.timerTypePomodoroButton.isClickable = true
        view.timerTypePomodoroButton.setBackgroundResource(attrResourceId(android.R.attr.selectableItemBackground))
        view.timerTypePomodoroButton.setTextColor(colorRes(R.color.md_light_text_50))

        view.timerTypeCountdownButton.isClickable = false
        view.timerTypeCountdownButton.setBackgroundResource(R.drawable.bordered_rectangle_white_background)
        view.timerTypeCountdownButton.setTextColor(colorRes(R.color.md_white))

        view.timerTypePomodoroButton.onDebounceClick {
            onSwipeLeft(view)
        }
        view.timerTypeCountdownButton.setOnClickListener(null)

        dispatch(QuestAction.ShowCountDownTimer)
    }

    private fun onSwipeLeft(view: View) {
        view.timerType.setInAnimation(view.context, R.anim.right_in)
        view.timerType.setOutAnimation(view.context, R.anim.right_out)

        view.timerType.showPrevious()

        view.timerTypePomodoroButton.isClickable = false
        view.timerTypePomodoroButton.setBackgroundResource(R.drawable.bordered_rectangle_white_background)
        view.timerTypePomodoroButton.setTextColor(colorRes(R.color.md_white))

        view.timerTypeCountdownButton.isClickable = true
        view.timerTypeCountdownButton.setBackgroundResource(attrResourceId(android.R.attr.selectableItemBackground))
        view.timerTypeCountdownButton.setTextColor(colorRes(R.color.md_light_text_50))


        view.timerTypePomodoroButton.setOnClickListener(null)
        view.timerTypeCountdownButton.onDebounceClick {
            onSwipeRight(view)
        }

        dispatch(QuestAction.ShowPomodoroTimer)
    }

    private fun cancelAnimations(view: View) {
        view.subQuestListProgress.clearAnimation()
        listOf(view.timerLabel, view.timerProgressScroll).forEach {
            it.clearAnimation()
            it.alpha = 1f
        }

        view.startStop.clearAnimation()
        view.startStop.y = originalTimerButtonsY(view)

        view.complete.clearAnimation()
        view.complete.y = originalTimerButtonsY(view)

        view.timerProgressContainer.children.forEach {
            it.clearAnimation()
            it.alpha = 1f
        }
    }

    private fun originalTimerButtonsY(view: View) =
        view.timerLabel.y + view.timerLabel.height + ViewUtils.dpToPx(32f, view.context)


    private fun renderTimerButton(button: ImageView, type: TimerButton) {
        val iconImage = when (type) {
            TimerButton.START -> Ionicons.Icon.ion_play
            TimerButton.STOP -> Ionicons.Icon.ion_stop
            TimerButton.DONE -> Ionicons.Icon.ion_android_done
        }

        val icon = IconicsDrawable(button.context)
            .icon(iconImage)
            .colorRes(R.color.md_light_text_70)
            .sizeDp(22)

        button.setImageDrawable(icon)
    }

    private fun renderSubQuests(state: QuestViewState, view: View) {
        val adapter = view.questSubQuestList.adapter as SubQuestAdapter
        adapter.updateAll(state.subQuestViewModels)
        view.newSubQuestName.onDebounceClick {
            addSubQuest(view)
        }

        view.newSubQuestName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSubQuest(view)
            }
            true
        }

        view.subQuestListProgress.animateProgress(
            view.subQuestListProgress.progress,
            state.subQuestListProgressPercent,
            endListener = {
                val drawable = view.subQuestListProgress.progressDrawable as LayerDrawable
                val backgroundDrawable = drawable.getDrawable(0)
                val backgroundColor =
                    if
                        (state.allSubQuestsDone) colorRes(R.color.md_green_700)
                    else
                        attrData(R.attr.colorPrimaryDark)

                backgroundDrawable.setColorFilter(
                    backgroundColor,
                    PorterDuff.Mode.SRC_ATOP
                )

                val progressDrawable = drawable.getDrawable(1)
                val progressColor =
                    if
                        (state.allSubQuestsDone) colorRes(R.color.md_green_500)
                    else
                        attrData(R.attr.colorPrimary)
                progressDrawable.setColorFilter(
                    progressColor,
                    PorterDuff.Mode.SRC_ATOP
                )
            }
        )

        if (state.hasSubQuests) {
            @SuppressLint("SetTextI18n")
            view.subQuestListProgressLabel.text = "${state.subQuestListProgressPercent}%"
            view.subQuestListProgressLabel.visible()
            view.doneLabel.setText(R.string.done)
            view.doneLabel.setAllCaps(true)
            view.subQuestListProgress.visible()
            view.subQuestsHint.gone()
            view.subQuestsHintIcon.gone()
            view.questSubQuestList.visible()
        } else {
            view.subQuestListProgressLabel.gone()
            view.subQuestListProgress.invisible()
            view.questSubQuestList.gone()
            view.subQuestsHint.visible()
            view.subQuestsHintIcon.visible()
        }

    }

    private fun addSubQuest(view: View) {
        val name = view.newSubQuestName.text.toString()
        dispatch(QuestAction.AddSubQuest(name))
    }


    private fun startTimer(view: View, state: QuestViewState) {

        view.timerPreviewGroup.gone()
        view.timerRunningGroup.visible()
        view.timerTypePomodoroButton.invisible()
        view.timerTypeCountdownButton.invisible()

        renderTimerProgress(view, state)
        renderTimerIndicatorsProgress(view, state)

        handler.removeCallbacksAndMessages(null)
        cancelAnimations(view)

        var updateTimer = {}

        updateTimer = {
            dispatch(QuestAction.Tick)
            handler.postDelayed(updateTimer, 1000)
        }

        handler.postDelayed(updateTimer, 1000)

        renderTimerButton(view.startStop, TimerButton.STOP)
        view.startStop.dispatchOnClick { QuestAction.Stop }

        if (state.timerType == QuestViewState.TimerType.POMODORO) {
            view.timerProgressScroll.visible()
            view.complete.gone()
            playBlinkIndicatorAnimation(view.timerProgressContainer.getChildAt(state.currentProgressIndicator))
        } else {
            view.timerProgressScroll.invisible()
            view.complete.visible()
            view.complete.dispatchOnClick { QuestAction.CompleteQuest }
        }

        view.appbar.onDebounceClick {
            playShowNotImportantViewsAnimation(view)
        }
        playHideNotImportantViewsAnimation(view)
    }


    private fun playShowNotImportantViewsAnimation(view: View) {
        view.startStop
            .animate()
            .y(originalTimerButtonsY(view))
            .setDuration(shortAnimTime)
            .withEndAction {
                listOf(view.timerLabel).forEach {
                    it
                        .animate()
                        .alpha(1f)
                        .setDuration(longAnimTime)
                        .setStartDelay(0)
                        .withEndAction {
                            playHideNotImportantViewsAnimation(view)
                        }
                        .start()
                }
            }
            .start()

        view.complete
            .animate()
            .y(originalTimerButtonsY(view))
            .setDuration(shortAnimTime)
            .start()
    }

    private fun playHideNotImportantViewsAnimation(view: View) {
        listOf(view.timerLabel).forEach {
            it
                .animate()
                .alpha(0f)
                .setDuration(longAnimTime)
                .setStartDelay(3000)
                .withEndAction {
                    resources?.let {
                        val centerY =
                            view.timerProgressCircle.y + view.timerProgressCircle.height / 2
                        val y = centerY - view.startStop.height / 2
                        view.startStop
                            .animate()
                            .y(y)
                            .setDuration(shortAnimTime)
                            .start()

                        view.complete
                            .animate()
                            .y(y)
                            .setDuration(shortAnimTime)
                            .start()
                    }
                }
                .start()
        }

    }

    private fun renderTimerProgress(
        view: View,
        state: QuestViewState
    ) {
        view.timerProgressCircle.max = state.maxTimerProgress * 2
        view.timerProgressCircle.progress = state.timerProgress * 2
    }

    private fun renderTimerIndicatorsProgress(view: View, state: QuestViewState) {
        view.timerProgressContainer.removeAllViews()
        state.pomodoroProgress.forEach {
            addProgressIndicator(view, it)
        }
    }

    private fun addProgressIndicator(view: View, progress: PomodoroProgress) {
        val progressView = createProgressView(view)
        val progressDrawable = resources!!.getDrawable(
            R.drawable.timer_progress_item,
            view.context.theme
        ) as GradientDrawable

        when (progress) {
            PomodoroProgress.INCOMPLETE_WORK -> {
                progressDrawable.setColor(colorRes(R.color.md_light_text_38))
            }

            PomodoroProgress.COMPLETE_WORK -> {
                progressDrawable.setColor(colorRes(R.color.md_white))
            }

            PomodoroProgress.INCOMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_light_text_38))
                progressView.setScale(0.65f)
            }

            PomodoroProgress.COMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_white))
                progressView.setScale(0.65f)
            }

            PomodoroProgress.INCOMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_light_text_38))
                progressView.setScale(0.8f)
            }

            PomodoroProgress.COMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_white))
                progressView.setScale(0.8f)
            }
        }
        progressView.timerItemProgress.background = progressDrawable

        if (view.timerProgressContainer.childCount > 0) {
            val lp = progressView.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = ViewUtils.dpToPx(4f, view.context).toInt()
        }

        view.timerProgressContainer.addView(progressView)
    }

    private fun createProgressView(view: View) =
        LayoutInflater.from(view.context).inflate(
            R.layout.item_timer_progress,
            view.timerProgressContainer,
            false
        )

    private fun playBlinkIndicatorAnimation(
        view: View,
        reverse: Boolean = false,
        repeatCount: Int = 0
    ) {

        val newRepeat = if (!reverse) repeatCount + 1 else repeatCount

        if (newRepeat > 3) {
            return
        }

        resources?.let {
            view
                .animate()
                .alpha(if (reverse) 1f else 0f)
                .setDuration(mediumAnimTime)
                .withEndAction {
                    playBlinkIndicatorAnimation(view, !reverse, newRepeat)
                }
                .start()
        }
    }

    data class SubQuestViewModel(
        val name: String,
        val isCompleted: Boolean
    ) : RecyclerViewViewModel {
        override val id: String
            get() = name + isCompleted
    }

    inner class SubQuestAdapter :
        BaseRecyclerViewAdapter<SubQuestViewModel>(R.layout.item_quest_sub_quest) {

        override fun onBindViewModel(vm: SubQuestViewModel, view: View, holder: SimpleViewHolder) {

            view.subQuestCheckBox.setOnCheckedChangeListener(null)

            view.subQuestCheckBox.isChecked = vm.isCompleted

            view.subQuestCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    dispatch(QuestAction.CompleteSubQuest(holder.adapterPosition))
                } else {
                    dispatch(QuestAction.UndoCompletedSubQuest(holder.adapterPosition))
                }
            }

            view.editSubQuestName.setText(vm.name)

            if (vm.isCompleted) {
                view.editSubQuestName.paintFlags = view.editSubQuestName.paintFlags or
                    Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                view.editSubQuestName.paintFlags = view.editSubQuestName.paintFlags and
                    Paint.STRIKE_THRU_TEXT_FLAG.inv()

            }

            view.editSubQuestName.setOnFocusChangeListener { _, hasFocus ->

                val adapterPosition = holder.adapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return@setOnFocusChangeListener
                }

                if (hasFocus) {
                    startEdit(view)
                } else {
                    dispatch(
                        QuestAction.SaveSubQuestName(
                            view.editSubQuestName.text.toString(),
                            adapterPosition
                        )
                    )
                }

            }

            view.reorderButton.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }

            view.removeButton.setOnClickListener {
                removeAt(holder.adapterPosition)
                dispatch(QuestAction.RemoveSubQuest(holder.adapterPosition))
            }
        }

        private fun startEdit(view: View) {
            disableEditForAllSubQuests()
            view.reorderButton.gone()
            view.removeButton.visible()
            view.editSubQuestName.requestFocus()
            ViewUtils.showKeyboard(view.context, view.editSubQuestName)
            view.editSubQuestName.setSelection(view.editSubQuestName.length())
        }
    }


    private fun disableEditForAllSubQuests() {
        view!!.questSubQuestList.children.forEach {
            it.removeButton.gone()
            it.reorderButton.visible()
        }
    }

    private val QuestViewState.color500
        get() = color.androidColor.color500

    private val QuestViewState.color700
        get() = color.androidColor.color700

    private val QuestViewState.subQuestViewModels: List<SubQuestViewModel>
        get() = subQuests.map {
            SubQuestViewModel(
                name = it.name,
                isCompleted = it.completedAtDate != null
            )
        }

    private val QuestViewState.noteText: String
        get() = if (note == null || note.isBlank()) stringRes(R.string.tap_to_add_note) else note

    private val QuestViewState.pomodoroTimerText: String
        get() = if (completePomodorCount > 0) "$completePomodorCount/$pomodoroCount Pomodoros done" else "For $pomodoroCount Pomodoros"

    private val QuestViewState.countdownTimerText: String
        get() = "Countdown for ${DurationFormatter.formatReadable(activity!!, duration.intValue)}"

    enum class TimerButton {
        START, STOP, DONE
    }

    enum class PomodoroProgress {
        INCOMPLETE_SHORT_BREAK,
        COMPLETE_SHORT_BREAK,
        INCOMPLETE_LONG_BREAK,
        COMPLETE_LONG_BREAK,
        INCOMPLETE_WORK,
        COMPLETE_WORK
    }
}