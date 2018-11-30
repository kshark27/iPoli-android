package io.ipoli.android.challenge.add

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants.Companion.DECIMAL_FORMATTER
import io.ipoli.android.R
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.inflate
import io.ipoli.android.common.view.navigate
import io.ipoli.android.common.view.normalIcon
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import kotlinx.android.synthetic.main.controller_add_challenge_tracked_value.view.*
import kotlinx.android.synthetic.main.item_challenge_summary_tracked_value.view.*

class AddChallengeTrackedValueViewController(args: Bundle? = null) :
    BaseViewController<EditChallengeAction, EditChallengeViewState>(
        args
    ) {
    override val stateKey = EditChallengeReducer.stateKey

    private var showNext = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_add_challenge_tracked_value)

        view.resultAverageValueIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(CommunityMaterial.Icon.cmd_scale_balance)
                .colorRes(R.color.md_amber_500)
                .paddingDp(10)
                .sizeDp(40)
        )

        view.resultReachValueBackground.onDebounceClick {
            dispatch(EditChallengeAction.ShowTargetTrackedValuePicker(emptyList()))
        }

        view.resultAverageBackground.onDebounceClick {
            dispatch(EditChallengeAction.ShowAverageTrackedValuePicker(emptyList()))
        }

        view.resultReachItems.layoutManager = LinearLayoutManager(view.context)
        view.resultReachItems.adapter = TrackedValueAdapter()

        view.resultAverageItems.layoutManager = LinearLayoutManager(view.context)
        view.resultAverageItems.adapter = TrackedValueAdapter()

        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.next_wizard_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val nextItem = menu.findItem(R.id.actionNext)
        nextItem.isVisible = showNext
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.actionNext -> {
                dispatch(EditChallengeAction.ShowNext)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: EditChallengeViewState, view: View) {

        when (state.type) {

            TRACKED_VALUES_CHANGED -> {
                showNext = state.trackedValues.isNotEmpty()
                activity?.invalidateOptionsMenu()
                (view.resultReachItems.adapter as TrackedValueAdapter).updateAll(state.trackTargetViewModels)
                (view.resultAverageItems.adapter as TrackedValueAdapter).updateAll(state.trackAverageViewModels)

                view.resultReachValueBackground.onDebounceClick {
                    dispatch(EditChallengeAction.ShowTargetTrackedValuePicker(state.trackedValues))
                }

                view.resultAverageBackground.onDebounceClick {
                    dispatch(EditChallengeAction.ShowAverageTrackedValuePicker(state.trackedValues))
                }
            }

            SHOW_TARGET_TRACKED_VALUE_PICKER ->
                navigate().toTargetValuePicker(targetValueSelectedListener = { t ->
                    dispatch(
                        EditChallengeAction.AddTargetTrackedValue(t)
                    )
                })

            SHOW_AVERAGE_TRACKED_VALUE_PICKER ->
                navigate().toMaintainAverageValuePicker(trackedValueSelectedListener = { t ->
                    dispatch(
                        EditChallengeAction.AddAverageTrackedValue(t)
                    )
                })

            else -> {
            }
        }
    }

    data class TrackedValueViewModel(
        override val id: String,
        val text: String,
        val trackedValue: Challenge.TrackedValue
    ) : RecyclerViewViewModel

    inner class TrackedValueAdapter :
        BaseRecyclerViewAdapter<TrackedValueViewModel>(R.layout.item_challenge_summary_tracked_value) {

        override fun onBindViewModel(
            vm: TrackedValueViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.expectedResultRemove.setImageDrawable(
                IconicsDrawable(view.context).normalIcon(
                    GoogleMaterial.Icon.gmd_close,
                    R.color.md_dark_text_87
                ).respectFontBounds(true)
            )
            view.expectedResultRemove.dispatchOnClick { EditChallengeAction.RemoveTrackedValue(vm.id) }
            view.expectedResultText.setTextColor(colorRes(R.color.md_dark_text_87))
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

    private val EditChallengeViewState.trackTargetViewModels: List<TrackedValueViewModel>
        get() = trackedValues
            .filterIsInstance(Challenge.TrackedValue.Target::class.java)
            .map {
                TrackedValueViewModel(
                    id = it.id,
                    text = "Reach ${DECIMAL_FORMATTER.format(it.targetValue)} ${it.units} ${it.name}",
                    trackedValue = it
                )
            }

    private val EditChallengeViewState.trackAverageViewModels: List<TrackedValueViewModel>
        get() = trackedValues
            .filterIsInstance(Challenge.TrackedValue.Average::class.java)
            .map {
                TrackedValueViewModel(
                    id = it.id,
                    text = "Maintain ${DECIMAL_FORMATTER.format(it.targetValue)} ${it.units} ${it.name}",
                    trackedValue = it
                )
            }
}