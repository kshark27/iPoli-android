package io.ipoli.android.repeatingquest.edit.picker

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.transition.ChangeBounds
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.repeatingquest.edit.picker.RepeatPatternViewState.StateType.*
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.entity.RepeatType
import kotlinx.android.synthetic.main.dialog_repeat_pattern_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.format.TextStyle


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/16/18.
 */
class RepeatPatternPickerDialogController :
    ReduxDialogController<RepeatPatternAction, RepeatPatternViewState, RepeatPatternReducer> {

    override val reducer = RepeatPatternReducer

    private var repeatPattern: RepeatPattern? = null
    private lateinit var resultListener: (RepeatPattern) -> Unit
    private var cancelListener: (() -> Unit)? = null

    constructor(args: Bundle? = null) : super(args)

    constructor(
        repeatPattern: RepeatPattern? = null,
        resultListener: (RepeatPattern) -> Unit,
        cancelListener: (() -> Unit)? = null
    ) : this() {
        this.repeatPattern = repeatPattern
        this.resultListener = resultListener
        this.cancelListener = cancelListener
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_repeat_pattern_picker, null)

        view.rpWeekDayList.layoutManager =
            LinearLayoutManager(activity!!, LinearLayoutManager.HORIZONTAL, false)
        view.rpWeekDayList.adapter = WeekDayAdapter()

        view.rpMonthDayList.layoutManager = GridLayoutManager(activity, 7)
        view.rpMonthDayList.setHasFixedSize(true)
        view.rpMonthDayList.adapter = MonthDayAdapter()

        return view
    }

    override fun onCreateLoadAction() =
        RepeatPatternAction.LoadData(repeatPattern)

    override fun render(state: RepeatPatternViewState, view: View) {
        when (state.type) {

            DATA_LOADED -> {
                val petHead = AndroidPetAvatar.valueOf(state.petAvatar.name).headImage
                changeIcon(petHead)
                renderStartDate(view, state)
                initStartDateListener(view, state)

                renderEndDate(view, state)
                initEndDateListener(view, state)

                renderForRepeatType(state, view)

                view.rpPetSchedulingHint.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(view.context, petHead),
                    null,
                    null,
                    null
                )
            }

            REPEAT_TYPE_CHANGED -> {
                TransitionManager.beginDelayedTransition(
                    view.contentLayout as ViewGroup,
                    ChangeBounds()
                )
                renderForRepeatType(state, view)
                renderMessage(view, state)
            }

            WEEK_DAYS_CHANGED -> {
                renderWeekDays(view, state)
                renderMessage(view, state)
            }

            COUNT_CHANGED -> {
                renderMessage(view, state)
            }

            MONTH_DAYS_CHANGED -> {
                renderMonthDays(view, state)
                renderMessage(view, state)
            }

            YEAR_DAY_CHANGED -> {
                renderDayOfYear(view, state)
            }

            START_DATE_CHANGED -> {
                renderStartDate(view, state)
            }

            END_DATE_CHANGED -> {
                renderEndDate(view, state)
            }

            PATTERN_CREATED -> {
                resultListener(state.resultPattern!!)
                dismiss()
            }

            else -> {
            }
        }
    }

    private fun renderSkipInterval(view: View, valueLabels: List<String>, selectedIndex: Int) {

        view.skipEveryText.visible()
        view.skipEvery.visible()

        view.skipEvery.adapter = ArrayAdapter(
            view.context,
            R.layout.item_dropdown_number_spinner,
            valueLabels
        )
        view.skipEvery.onItemSelectedListener = null
        view.skipEvery.setSelection(selectedIndex)
        view.skipEvery.post {
            view.skipEvery.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    dispatch(RepeatPatternAction.ChangeSkipInterval(position))
                }

            }
        }
    }

    private fun renderDayOfYear(
        view: View,
        state: RepeatPatternViewState
    ) {
        view.rpDayOfYear.text = state.formattedDayOfYear
    }

    private fun initEndDateListener(
        view: View,
        state: RepeatPatternViewState
    ) {
        view.rpEnd.onDebounceClick {
            val date = state.pickerEndDate
            val datePickerDialog = DatePickerDialog(
                view.context,
                DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    dispatch(
                        RepeatPatternAction.ChangeEndDate(
                            LocalDate.of(year, month + 1, dayOfMonth)
                        )
                    )
                }, date.year, date.month.value - 1, date.dayOfMonth
            )
            datePickerDialog.datePicker.minDate = DateUtils.toMillis(state.startDate.plusDays(1))
            datePickerDialog.show()
        }
    }

    private fun renderEndDate(
        view: View,
        state: RepeatPatternViewState
    ) {
        view.rpEnd.text = state.formattedEndDate
    }

    private fun initStartDateListener(
        view: View,
        state: RepeatPatternViewState
    ) {
        view.rpStart.onDebounceClick {
            val date = state.startDate
            val datePickerDialog = DatePickerDialog(
                view.context,
                DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    dispatch(
                        RepeatPatternAction.ChangeStartDate(
                            LocalDate.of(year, month + 1, dayOfMonth)
                        )
                    )
                }, date.year, date.month.value - 1, date.dayOfMonth
            )
            datePickerDialog.datePicker.minDate = DateUtils.toMillis(LocalDate.now())
            if (state.endDate != null) {
                datePickerDialog.datePicker.maxDate = DateUtils.toMillis(state.endDate.minusDays(1))
            }
            datePickerDialog.show()
        }
    }

    private fun renderStartDate(
        view: View,
        state: RepeatPatternViewState
    ) {
        view.rpStart.text = state.formattedStartDate
    }

    private fun renderForRepeatType(
        state: RepeatPatternViewState,
        view: View
    ) {
        when (state.repeatType) {
            RepeatType.DAILY -> renderDaily(view, state)
            RepeatType.WEEKLY -> renderWeekly(view, state)
            RepeatType.MONTHLY -> renderMonthly(view, state)
            RepeatType.YEARLY -> renderYearly(view, state)
            RepeatType.MANUAL -> renderManual(view)
        }
    }

    private fun renderManual(view: View) {
        ViewUtils.goneViews(
            view.rpWeekDayList,
            view.rpMonthDayList,
            view.countGroup,
            view.yearlyPatternGroup,
            view.skipEvery,
            view.skipEveryText,
            view.startEndGroup
        )
    }

    private fun renderYearly(
        view: View,
        state: RepeatPatternViewState
    ) {
        ViewUtils.goneViews(
            view.rpWeekDayList,
            view.rpMonthDayList,
            view.countGroup
        )
        ViewUtils.showViews(
            view.yearlyPatternGroup,
            view.startEndGroup
        )

        renderFrequencies(view, state)
        renderMessage(view, state)

        renderDayOfYear(view, state)

        view.rpDayOfYear.onDebounceClick {
            val date = state.dayOfYear
            val datePickerDialog = DatePickerDialog(
                view.context,
                DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    dispatch(
                        RepeatPatternAction.ChangeDayOfYear(
                            LocalDate.of(year, month + 1, dayOfMonth)
                        )
                    )
                }, date.year, date.month.value - 1, date.dayOfMonth
            )
            datePickerDialog.show()
        }

        view.skipEveryText.gone()
        view.skipEvery.gone()
    }

    private fun renderMonthly(
        view: View,
        state: RepeatPatternViewState
    ) {
        ViewUtils.goneViews(
            view.rpWeekDayList,
            view.yearlyPatternGroup
        )
        ViewUtils.showViews(
            view.rpMonthDayList,
            view.countGroup,
            view.startEndGroup
        )

        renderFrequencies(view, state)
        renderMonthDaysCount(view, state)
        renderMonthDays(view, state)
        renderMessage(view, state)

        renderSkipInterval(view, state.monthSkipLabels, state.everyXMonthsCountIndex)
    }

    private fun renderWeekly(
        view: View,
        state: RepeatPatternViewState
    ) {
        ViewUtils.goneViews(
            view.rpMonthDayList,
            view.yearlyPatternGroup
        )
        ViewUtils.showViews(
            view.rpWeekDayList,
            view.countGroup,
            view.startEndGroup
        )

        renderFrequencies(view, state)
        renderWeekDaysCount(view, state)
        renderWeekDays(view, state)
        renderMessage(view, state)

        renderSkipInterval(view, state.weekSkipLabels, state.everyXWeeksCountIndex)
    }

    private fun renderDaily(
        view: View,
        state: RepeatPatternViewState
    ) {
        ViewUtils.goneViews(
            view.rpWeekDayList,
            view.rpMonthDayList,
            view.yearlyPatternGroup,
            view.countGroup
        )
        view.startEndGroup.visible()
        renderFrequencies(view, state)
        renderMessage(view, state)

        renderSkipInterval(view, state.daySkipLabels, state.everyXDaysCountIndex)
    }

    private fun renderMonthDays(
        view: View,
        state: RepeatPatternViewState
    ) {
        (view.rpMonthDayList.adapter as MonthDayAdapter).updateAll(
            state.monthDaysViewModels()
        )
    }

    private fun renderMessage(
        view: View,
        state: RepeatPatternViewState
    ) {
        if (state.petSchedulingHintText == null) {
            ViewUtils.goneViews(view.rpPetSchedulingHint)

        } else {
            ViewUtils.showViews(view.rpPetSchedulingHint)
            view.rpPetSchedulingHint.text = state.petSchedulingHintText
        }
    }

    private fun renderWeekDaysCount(
        view: View,
        state: RepeatPatternViewState
    ) {
        val count = view.rpCount
        count.adapter = ArrayAdapter(
            view.context,
            R.layout.item_dropdown_number_spinner,
            state.weekCountValues
        )
        count.onItemSelectedListener = null
        count.setSelection(state.weekDaysCountIndex)
        count.post {
            count.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    dispatch(RepeatPatternAction.ChangeWeekDayCount(position))
                }

            }
        }
    }

    private fun renderMonthDaysCount(
        view: View,
        state: RepeatPatternViewState
    ) {
        val count = view.rpCount
        count.adapter = ArrayAdapter(
            view.context,
            R.layout.item_dropdown_number_spinner,
            state.monthCountValues
        )
        count.onItemSelectedListener = null
        count.setSelection(state.monthDaysCountIndex)
        count.post {
            count.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    dispatch(RepeatPatternAction.ChangeMonthDayCount(position))
                }
            }
        }
    }

    private fun renderWeekDays(
        view: View,
        state: RepeatPatternViewState
    ) {
        (view.rpWeekDayList.adapter as WeekDayAdapter).updateAll(
            state.weekDaysViewModels()
        )
    }

    private fun renderFrequencies(
        view: View,
        state: RepeatPatternViewState
    ) {
        val repeatType = view.rpRepeatType
        repeatType.onItemSelectedListener = null
        repeatType.setSelection(state.repeatTypeIndex)
        repeatType.post {
            repeatType.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        dispatch(RepeatPatternAction.ChangeFrequency(position))
                    }

                }
        }

    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton("OK", null)
            .setNegativeButton(R.string.cancel, null)
            .create()


    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(RepeatPatternAction.CreatePattern)
            }
            setNegativeButtonListener {
                cancelListener?.invoke()
                dismiss()
            }
        }
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.repeat_pattern_picker_title)
    }

    data class WeekDayViewModel(
        val text: String,
        @DrawableRes val background: Int,
        @ColorInt val textColor: Int,
        val isSelected: Boolean,
        val weekDay: DayOfWeek
    )

    inner class WeekDayAdapter(private var viewModels: List<WeekDayViewModel> = listOf()) :
        RecyclerView.Adapter<ViewHolder>() {
        override fun getItemCount() = viewModels.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vm = viewModels[position]
            val button = holder.itemView as Button
            button.text = vm.text

            button.setBackgroundResource(vm.background)
            button.setTextColor(vm.textColor)
            button.dispatchOnClick { RepeatPatternAction.ToggleWeekDay(vm.weekDay) }
        }

        fun updateAll(viewModels: List<WeekDayViewModel>) {
            this.viewModels = viewModels
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_repeating_pattern_week_day,
                    parent,
                    false
                )
            )

    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    data class MonthDayViewModel(
        val text: String,
        @DrawableRes val background: Int,
        @ColorInt val textColor: Int, val isSelected: Boolean, val day: Int
    )

    inner class MonthDayAdapter(private var viewModels: List<MonthDayViewModel> = listOf()) :
        RecyclerView.Adapter<ViewHolder>() {
        override fun getItemCount() = viewModels.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vm = viewModels[position]
            val view = holder.itemView as TextView
            view.text = vm.text
            view.setTextColor(vm.textColor)
            view.setBackgroundResource(vm.background)
            view.dispatchOnClick { RepeatPatternAction.ToggleMonthDay(vm.day) }
        }

        fun updateAll(viewModels: List<MonthDayViewModel>) {
            this.viewModels = viewModels
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_repeating_pattern_month_day,
                    parent,
                    false
                )
            )
    }

    private fun RepeatPatternViewState.weekDaysViewModels() =
        DateUtils.localeDaysOfWeek.map {
            val isSelected = selectedWeekDays.contains(it)
            val (background, textColor) = if (isSelected)
                Pair(R.drawable.circle_accent, colorRes(R.color.md_white))
            else
                Pair(R.drawable.circle_normal, attrData(R.attr.colorAccent))
            DateUtils.daysOfWeekText(TextStyle.SHORT_STANDALONE)

            RepeatPatternPickerDialogController.WeekDayViewModel(
                text = DateUtils.dayOfWeekText(
                    it,
                    TextStyle.SHORT_STANDALONE
                ).first().toUpperCase().toString(),
                background = background,
                textColor = textColor,
                isSelected = isSelected,
                weekDay = it
            )
        }

    private fun RepeatPatternViewState.monthDaysViewModels() =
        (1..31).map {
            val isSelected = selectedMonthDays.contains(it)
            val background = if (isSelected)
                R.drawable.circle_accent
            else
                attrResourceId(android.R.attr.selectableItemBackgroundBorderless)

            val textColor = if (isSelected)
                R.color.md_white
            else
                colorTextSecondaryResource

            RepeatPatternPickerDialogController.MonthDayViewModel(
                text = it.toString(),
                background = background,
                isSelected = isSelected,
                textColor = colorRes(textColor),
                day = it
            )
        }

    private val RepeatPatternViewState.petSchedulingHintText: String?
        get() {

            if (repeatType == RepeatType.MANUAL) {
                return stringRes(R.string.manual_schedule_hint)
            }

            if (!isFlexible) {
                return null
            }

            if (repeatType == RepeatType.WEEKLY) {
                return if (selectedWeekDays.isEmpty()) {
                    stringRes(R.string.pattern_picker_flexible_weekly_no_selected_days)
                } else {
                    stringRes(R.string.pattern_picker_flexible_weekly_selected_days)
                }
            } else if (repeatType == RepeatType.MONTHLY) {
                return if (selectedMonthDays.isEmpty()) {
                    stringRes(R.string.pattern_picker_flexible_monthly_no_selected_days)
                } else {
                    stringRes(R.string.pattern_picker_flexible_monthly_selected_days)
                }
            }

            throw IllegalArgumentException("Unknown flexible repeat type $repeatType")
        }

    private val RepeatPatternViewState.formattedDayOfYear
        get() = DateFormatter.formatDayWithWeek(dayOfYear)

    private val RepeatPatternViewState.formattedStartDate
        get() = DateFormatter.format(view!!.context, startDate)

    private val RepeatPatternViewState.formattedEndDate
        get() =
            if (endDate == null)
                stringRes(R.string.end_of_time)
            else
                DateFormatter.format(view!!.context, endDate)

    private val RepeatPatternViewState.daySkipLabels
        get() = everyXDaysValues.map {
            when (it) {
                0 -> "Day"
                1 -> "Other day"
                else -> "$it days"
            }
        }

    private val RepeatPatternViewState.weekSkipLabels
        get() = everyXWeeksValues.map {
            when (it) {
                0 -> "Week"
                1 -> "Other week"
                else -> "$it weeks"
            }
        }

    private val RepeatPatternViewState.monthSkipLabels
        get() = everyXMonthsValues.map {
            when (it) {
                0 -> "Month"
                1 -> "Other month"
                else -> "$it months"
            }
        }
}