package io.ipoli.android.planday

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.stringRes
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.planday.RescheduleDialogViewState.StateType.DATA_LOADED
import io.ipoli.android.planday.RescheduleDialogViewState.StateType.LOADING
import kotlinx.android.synthetic.main.dialog_reschedule.view.*
import kotlinx.android.synthetic.main.item_reschedule_date.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 5/17/18.
 */
sealed class RescheduleDialogAction : Action {
    data class Load(val includeToday: Boolean) : RescheduleDialogAction() {
        override fun toMap() = mapOf("includeToday" to includeToday)
    }
}

object RescheduleDialogReducer : BaseViewStateReducer<RescheduleDialogViewState>() {
    override val stateKey = key<RescheduleDialogViewState>()

    override fun reduce(
        state: AppState,
        subState: RescheduleDialogViewState,
        action: Action
    ) =
        when (action) {
            is RescheduleDialogAction.Load -> {
                subState.copy(
                    type = DATA_LOADED,
                    petAvatar = state.dataState.player!!.pet.avatar
                )
            }
            else -> subState
        }

    override fun defaultState() = RescheduleDialogViewState(
        type = LOADING,
        petAvatar = Constants.DEFAULT_PET_AVATAR
    )


}

data class RescheduleDialogViewState(
    val type: StateType,
    val petAvatar: PetAvatar
) : BaseViewState() {
    enum class StateType {
        LOADING,
        DATA_LOADED
    }
}

class RescheduleDialogController(args: Bundle? = null) :
    ReduxDialogController<RescheduleDialogAction, RescheduleDialogViewState, RescheduleDialogReducer>(
        args
    ) {

    override val reducer = RescheduleDialogReducer

    private var includeToday = true
    private var isNewQuest = false

    private var listener: (LocalDate?, Time?, Duration<Minute>?) -> Unit = { _, _, _ -> }

    private var cancelListener: () -> Unit = {}

    constructor(
        includeToday: Boolean,
        isNewQuest: Boolean = false,
        listener: (LocalDate?, Time?, Duration<Minute>?) -> Unit,
        cancelListener: () -> Unit = {}
    ) : this() {
        this.includeToday = includeToday
        this.isNewQuest = isNewQuest
        this.listener = listener
        this.cancelListener = cancelListener
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_reschedule, null)
        view.dateList.layoutManager = GridLayoutManager(view.context, 2)
        val adapter = DateAdapter()
        view.dateList.adapter = adapter
        adapter.updateAll(rescheduleViewModels)
        return view
    }

    override fun onHeaderViewCreated(headerView: View) {
        val titleRes = if (isNewQuest) R.string.schedule_new_quest else R.string.reschedule
        headerView.dialogHeaderTitle.setText(titleRes)
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setNegativeButton(R.string.cancel, null)
            .create()

    override fun onCreateLoadAction() = RescheduleDialogAction.Load(includeToday)

    override fun render(state: RescheduleDialogViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar.name).headImage)
            }

            else -> {
            }
        }
    }

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { _ ->
                cancelListener()
                dismiss()
            }
        }
    }

    sealed class RescheduleViewModel : RecyclerViewViewModel {
        abstract val icon: Int
        abstract val text: String

        override val id: String
            get() = text

        data class ChooseStartTime(override val icon: Int, override val text: String) :
            RescheduleViewModel()

        data class StartNow(override val icon: Int, override val text: String) :
            RescheduleViewModel()

        data class StartIn5Minutes(override val icon: Int, override val text: String) :
            RescheduleViewModel()

        data class ChooseDuration(override val icon: Int, override val text: String) :
            RescheduleViewModel()

        data class ChooseDate(override val icon: Int, override val text: String) :
            RescheduleViewModel()

        data class Bucket(override val icon: Int, override val text: String) : RescheduleViewModel()

        data class ExactDate(
            val date: LocalDate,
            override val icon: Int,
            override val text: String
        ) : RescheduleViewModel()
    }

    inner class DateAdapter :
        BaseRecyclerViewAdapter<RescheduleViewModel>(R.layout.item_reschedule_date) {

        override fun onBindViewModel(
            vm: RescheduleViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.rescheduleDate.text = vm.text
            view.rescheduleIcon.setImageResource(vm.icon)
            view.onDebounceClick {

                when (vm) {
                    is RescheduleViewModel.ChooseDate -> {
                        val date = LocalDate.now()
                        DatePickerDialog(
                            view.context,
                            DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                                listener(LocalDate.of(year, month + 1, dayOfMonth), null, null)
                                dismiss()
                            }, date.year, date.month.value - 1, date.dayOfMonth
                        ).show()
                    }

                    is RescheduleViewModel.ExactDate -> {
                        listener(vm.date, null, null)
                        dismiss()
                    }

                    is RescheduleViewModel.Bucket -> {
                        listener(null, null, null)
                        dismiss()
                    }


                    is RescheduleViewModel.StartNow -> {
                        listener(LocalDate.now(), Time.now(), null)
                        dismiss()
                    }

                    is RescheduleViewModel.StartIn5Minutes -> {
                        listener(LocalDate.now(), Time.now().plus(5), null)
                        dismiss()
                    }

                    is RescheduleViewModel.ChooseStartTime -> {
                        createTimePickerDialog(
                            startTime = Time.now(),
                            showNeutral = true,
                            onTimePicked = { t ->
                                listener(null, t, null)
                                dismiss()
                            }).show(router)
                    }

                    is RescheduleViewModel.ChooseDuration -> {
                        navigateFromRoot().toDurationPicker { d ->
                            listener(null, null, d)
                            dismiss()
                        }
                    }
                }
            }

        }

    }

    private val rescheduleViewModels: List<RescheduleViewModel>
        get() {
            val vms = mutableListOf<RescheduleViewModel>()
            val today = LocalDate.now()
            vms.addAll(
                listOf(
                    RescheduleViewModel.ExactDate(
                        today.plusDays(1),
                        R.drawable.ic_tomorrow_text_secondary_24dp,
                        stringRes(R.string.tomorrow)
                    ),
                    RescheduleViewModel.Bucket(
                        R.drawable.ic_bucket_text_secondary_24dp,
                        stringRes(R.string.to_bucket)
                    ),

                    RescheduleViewModel.ChooseDate(
                        R.drawable.ic_more_circle_text_secondary_24dp,
                        stringRes(R.string.pick_date)
                    )
                )
            )
            if (includeToday) {
                vms.add(
                    0,
                    RescheduleViewModel.ExactDate(
                        today,
                        R.drawable.ic_today_text_secondary_24dp,
                        stringRes(R.string.today)
                    )
                )
            } else {
                vms.add(
                    1,
                    RescheduleViewModel.ExactDate(
                        today.plusDays(7),
                        R.drawable.ic_next_week_text_secondary_24dp,
                        stringRes(R.string.next_week)
                    )
                )
            }

            vms.add(
                0,
                RescheduleViewModel.StartNow(
                    R.drawable.ic_clock_now_secondary_24dp,
                    stringRes(R.string.start_now)
                )
            )

            vms.add(
                1,
                RescheduleViewModel.StartIn5Minutes(
                    R.drawable.ic_clock_forward_secondary_24dp,
                    stringRes(R.string.start_in_5_minutes)
                )
            )

            if (!isNewQuest) {
                vms.add(
                    2,
                    RescheduleViewModel.ChooseStartTime(
                        R.drawable.ic_clock_text_secondary_24dp,
                        stringRes(R.string.start_at)
                    )
                )

                vms.add(
                    3,
                    RescheduleViewModel.ChooseDuration(
                        R.drawable.ic_sandclock_text_secondary_24dp,
                        stringRes(R.string.change_duration)
                    )
                )
            }

            return vms
        }
}