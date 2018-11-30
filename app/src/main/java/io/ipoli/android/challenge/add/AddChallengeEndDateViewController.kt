package io.ipoli.android.challenge.add

import android.app.DatePickerDialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipoli.android.R
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.stringRes
import kotlinx.android.synthetic.main.controller_add_challenge_end_date.view.*
import kotlinx.android.synthetic.main.item_add_challenge_end_date.view.*
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/9/18.
 */
class AddChallengeEndDateViewController(args: Bundle? = null) :
    BaseViewController<EditChallengeAction, EditChallengeViewState>(
        args
    ) {
    override val stateKey = EditChallengeReducer.stateKey

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_add_challenge_end_date, container, false)
        view.dateList.layoutManager =
            LinearLayoutManager(container.context, LinearLayoutManager.VERTICAL, false)
        val adapter = DateAdapter()
        view.dateList.adapter = adapter
        adapter.updateAll(viewModels)
        return view
    }

    override fun colorStatusBars() {}

    override fun render(state: EditChallengeViewState, view: View) {
    }

    data class DateViewModel(
        val text: String,
        val date: LocalDate?
    ) : RecyclerViewViewModel {
        override val id: String
            get() = text
    }

    inner class DateAdapter :
        BaseRecyclerViewAdapter<DateViewModel>(R.layout.item_add_challenge_end_date) {
        override fun onBindViewModel(vm: DateViewModel, view: View, holder: SimpleViewHolder) {
            view.endDateText.text = vm.text
            if (vm.date != null) {
                view.dispatchOnClick { EditChallengeAction.SelectDate(vm.date) }
            } else {
                view.onDebounceClick {
                    val date = LocalDate.now()
                    val datePickerDialog = DatePickerDialog(
                        view.context,
                        DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                            dispatch(
                                EditChallengeAction.SelectDate(
                                    LocalDate.of(
                                        year,
                                        month + 1,
                                        dayOfMonth
                                    )
                                )
                            )
                        }, date.year, date.month.value - 1, date.dayOfMonth
                    )
                    datePickerDialog.datePicker.minDate = DateUtils.toMillis(date)
                    datePickerDialog.show()
                }
            }
        }
    }

    private val viewModels: List<DateViewModel>
        get() {
            val today = LocalDate.now()
            return listOf(
                DateViewModel(
                    stringRes(R.string.end_of_week),
                    today.with(DateUtils.lastDayOfWeek)
                ),
                DateViewModel(
                    stringRes(R.string.end_of_month),
                    today.with(TemporalAdjusters.lastDayOfMonth())
                ),
                DateViewModel(
                    stringRes(R.string.end_of_year),
                    today.with(TemporalAdjusters.lastDayOfYear())
                ),
                DateViewModel(stringRes(R.string.seven_days), today.plusDays(7)),
                DateViewModel(stringRes(R.string.ten_days), today.plusDays(10)),
                DateViewModel(stringRes(R.string.thirty_days), today.plusDays(30)),
                DateViewModel(stringRes(R.string.exact_date), null)
            )
        }
}