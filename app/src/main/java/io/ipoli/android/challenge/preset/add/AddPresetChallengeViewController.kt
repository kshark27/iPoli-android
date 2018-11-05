package io.ipoli.android.challenge.preset.add

import android.os.Bundle
import android.view.*
import com.bluelinelabs.conductor.RouterTransaction
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.add.AddPresetChallengeViewState.StateType.*
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.Color
import kotlinx.android.synthetic.main.controller_add_preset_challenge.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 11/1/18.
 */
class AddPresetChallengeViewController(args: Bundle? = null) :
    ReduxViewController<AddPresetChallengeAction, AddPresetChallengeViewState, AddPresetChallengeReducer>(
        args
    ) {
    override val reducer = AddPresetChallengeReducer

    private var category: PresetChallenge.Category? = null
    private var color: Color? = null

    private var isLastPage = false
    private var isLoading = false

    constructor(category: PresetChallenge.Category?, color: Color?) : this() {
        this.category = category
        this.color = color
    }

    override fun onCreateLoadAction() =
        AddPresetChallengeAction.Load(category, color)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_add_preset_challenge)
        setToolbar(view.toolbar)

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.add_challenge_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val nextItem = menu.findItem(R.id.actionNext)
        nextItem.title = if (isLastPage) stringRes(R.string.save) else stringRes(R.string.next)
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            android.R.id.home -> {
                if (!isLoading) {
                    dispatch(AddPresetChallengeAction.Back)
                }
                true
            }

            R.id.actionNext -> {
                if (!isLoading) {
                    dispatch(AddPresetChallengeAction.NextPage)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun handleBack(): Boolean {
        dispatch(AddPresetChallengeAction.Back)
        return true
    }

    override fun render(state: AddPresetChallengeViewState, view: View) {
        when (state.type) {

            INITIAL -> {
                colorLayout(view, state)
                val childRouter = getChildRouter(view.contentContainer)
                childRouter.setRoot(RouterTransaction.with(AddPresetChallengeInfoViewController()))
            }

            DATA_LOADED -> {
                colorLayout(view, state)
            }

            COLOR_CHANGED -> {
                colorLayout(view, state)
            }

            PAGE_CHANGED -> {
                if (state.page == AddPresetChallengeViewState.Page.ITEMS) {
                    isLastPage = true
                    val childRouter = getChildRouter(view.contentContainer)
                    childRouter.setRoot(RouterTransaction.with(AddPresetChallengeItemsViewController()))
                    activity?.invalidateOptionsMenu()
                } else {
                    isLastPage = false
                    val childRouter = getChildRouter(view.contentContainer)
                    childRouter.setRoot(
                        RouterTransaction.with(
                            AddPresetChallengeInfoViewController()
                        )
                    )
                    activity?.invalidateOptionsMenu()
                }
            }

            EXIT ->
                router.handleBack()

            SAVING -> {
                isLoading = true
                view.contentContainer.gone()
                view.loader.visible()

                dispatch(
                    AddPresetChallengeAction.Save(
                        name = state.name,
                        shortDescription = state.shortDescription,
                        description = state.description,
                        icon = state.icon,
                        color = state.color,
                        duration = state.duration,
                        difficulty = state.difficulty,
                        category = state.category,
                        expectedResults = state.expectedResults,
                        requirements = state.requirements,
                        quests = state.quests,
                        habits = state.habits
                    )
                )
            }

            SAVE_ERROR -> {
                isLoading = false
                view.contentContainer.visible()
                view.loader.gone()
                showShortToast(R.string.add_preset_challenge_save_error)
            }

            DONE -> {
                showLongToast(R.string.add_preset_challenge_done)
                router.handleBack()
            }

            else -> {
            }
        }
    }

    private fun colorLayout(
        view: View,
        state: AddPresetChallengeViewState
    ) {
        val color500 = colorRes(state.color.androidColor.color500)
        val color700 = colorRes(state.color.androidColor.color700)
        view.appbar.setBackgroundColor(color500)
        view.toolbar.setBackgroundColor(color500)
        view.contentContainer.setBackgroundColor(color500)
        activity?.window?.navigationBarColor = color500
        activity?.window?.statusBarColor = color700
    }

}