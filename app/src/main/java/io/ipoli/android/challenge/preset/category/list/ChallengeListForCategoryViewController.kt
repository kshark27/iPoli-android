package io.ipoli.android.challenge.preset.category.list

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.util.FixedPreloadSizeProvider
import com.google.firebase.auth.FirebaseAuth
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import io.ipoli.android.GlideApp
import io.ipoli.android.GlideRequest
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.category.list.ChallengeListForCategoryViewState.StateType.DATA_CHANGED
import io.ipoli.android.challenge.preset.category.list.ChallengeListForCategoryViewState.StateType.NO_INTERNET
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.player.inventory.InventoryViewController
import kotlinx.android.synthetic.main.controller_challenge_list_for_category.view.*
import kotlinx.android.synthetic.main.item_preset_challenge.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_inventory_toolbar.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/30/17.
 */
class ChallengeListForCategoryViewController :
    ReduxViewController<ChallengeListForCategoryAction, ChallengeListForCategoryViewState, ChallengeListForCategoryReducer> {

    override val reducer = ChallengeListForCategoryReducer

    private var category: PresetChallenge.Category = PresetChallenge.Category.HEALTH

    constructor(category: PresetChallenge.Category) : this() {
        this.category = category
    }

    constructor(args: Bundle? = null) : super(args)

    companion object {
        const val IMAGE_WIDTH = 800
        const val IMAGE_HEIGHT = 400
    }

    private lateinit var preLoader: RecyclerViewPreloader<ChallengeViewModel>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_challenge_list_for_category)
        setToolbar(view.toolbar)

        view.toolbarTitle.text = category.name.toLowerCase().capitalize()

        view.challengeList.layoutManager = LinearLayoutManager(container.context)
        val adapter = ChallengeAdapter()
        view.challengeList.adapter = adapter

        preLoader = RecyclerViewPreloader<ChallengeViewModel>(
            GlideApp.with(activity!!),
            adapter,
            FixedPreloadSizeProvider(IMAGE_WIDTH, IMAGE_HEIGHT),
            2
        )
        view.challengeList.addOnScrollListener(preLoader)

        setChildController(view.playerGems, InventoryViewController())


        view.addChallenge.onDebounceClick {
            if (FirebaseAuth.getInstance().currentUser == null) {
                showLongToast(R.string.add_preset_challenge_sign_in_required)
                return@onDebounceClick
            }
            navigateFromRoot().toAddPresetChallenge(category, category.color)
        }

        return view
    }

    override fun onDestroyView(view: View) {
        view.challengeList.removeOnScrollListener(preLoader)
        super.onDestroyView(view)
    }

    override fun onCreateLoadAction() =
        ChallengeListForCategoryAction.Load(category)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAttach(view: View) {
        showBackButton()
        super.onAttach(view)
        colorLayout(view)
    }

    private fun colorLayout(
        view: View
    ) {
        val ac = category.androidCategory
        view.appbar.setBackgroundColor(colorRes(ac.lightColor))
        view.toolbar.setBackgroundColor(colorRes(ac.lightColor))
        activity!!.window!!.navigationBarColor = colorRes(ac.lightColor)
        activity!!.window!!.statusBarColor = colorRes(ac.darkColor)
    }

    override fun render(state: ChallengeListForCategoryViewState, view: View) {
        when (state.type) {
            DATA_CHANGED -> {
                val viewModels = state.viewModels

                (view.challengeList.adapter as ChallengeAdapter).updateAll(viewModels)
                view.loader.gone()
                view.emptyContainer.gone()
                view.challengeList.visible()
            }

            NO_INTERNET -> {
                view.loader.gone()
                view.challengeList.gone()
                view.emptyContainer.visible()
                view.emptyAnimation.gone()
                view.emptyTitle.text = stringRes(R.string.error_no_internet_title)
                view.emptyText.text = stringRes(R.string.preset_challenges_no_internet_text)
            }

            else -> {
            }
        }
    }

    data class ChallengeViewModel(
        override val id: String,
        val name: String,
        val description: String,
        val image: Uri,
        val duration: String,
        val busyness: String,
        val difficulty: String,
        val level: String?,
        @DrawableRes val levelIcon: Int?,
        val participantCount: Int,
        val challenge: PresetChallenge
    ) : RecyclerViewViewModel

    inner class ChallengeAdapter :
        BaseRecyclerViewAdapter<ChallengeViewModel>(R.layout.item_preset_challenge),
        ListPreloader.PreloadModelProvider<ChallengeViewModel> {

        override fun getPreloadItems(position: Int) = mutableListOf(items[position])

        override fun getPreloadRequestBuilder(item: ChallengeViewModel) = glideRequest(item)

        private fun glideRequest(item: ChallengeViewModel): GlideRequest<Drawable> {
            return GlideApp.with(activity!!).load(item.image)
                .override(IMAGE_WIDTH, IMAGE_HEIGHT)
                .apply(
                    RequestOptions().transform(
                        RoundedCorners(
                            ViewUtils.dpToPx(
                                8f,
                                activity!!
                            ).toInt()
                        )
                    )
                )
        }

        override fun onBindViewModel(vm: ChallengeViewModel, view: View, holder: SimpleViewHolder) {
            glideRequest(vm).into(view.challengeBackgroundImage)

            view.challengeName.text = vm.name
            view.challengeDescription.text = vm.description
            view.challengeDuration.text = vm.duration
            view.challengeBusyness.text = vm.busyness
            view.challengeDifficulty.text = vm.difficulty
            if (vm.level != null) {
                view.challengeLevel.visible()
                view.challengeLevel.text = vm.level
                view.challengeLevel.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(view.context, vm.levelIcon!!),
                    null,
                    null,
                    null
                )
            } else {
                view.challengeLevel.gone()
            }

            if (vm.participantCount > 0) {
                view.challengeParticipants.visible()
                view.challengeParticipants.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    listItemIcon(GoogleMaterial.Icon.gmd_group).colorRes(R.color.md_white),
                    null,
                    null,
                    null
                )

                view.challengeParticipants.text = vm.participantCount.toString()
            } else {
                view.challengeParticipants.gone()
            }

            view.onDebounceClick {
                navigateFromRoot().toPresetChallenge(vm.challenge)
            }
        }
    }

    private val ChallengeListForCategoryViewState.viewModels: List<ChallengeViewModel>
        get() = challenges!!.map {
            ChallengeViewModel(
                id = it.id,
                name = it.name,
                description = it.shortDescription,
                image = Uri.parse(it.imageUrl),
                duration = "${it.duration.intValue} days",
                busyness = DurationFormatter.format(activity!!, it.busynessPerWeek.intValue),
                difficulty = it.difficulty.name.toLowerCase().capitalize(),
                level = if (it.level != null) it.level.toString() else null,
                levelIcon = if (it.level != null) {
                    when (it.level) {
                        1 -> R.drawable.ic_challenge_level1_text_secondary_24dp
                        2 -> R.drawable.ic_challenge_level2_text_secondary_24dp
                        else -> R.drawable.ic_challenge_level3_text_secondary_24dp
                    }
                } else null,
                participantCount = it.participantCount,
                challenge = it
            )
        }

    private val PresetChallenge.Category.androidCategory: AndroidPresetCategory
        get() = AndroidPresetCategory.valueOf(name)

    enum class AndroidPresetCategory(@StringRes val title: Int, @ColorRes val lightColor: Int, @ColorRes val darkColor: Int) {
        HEALTH(
            R.string.challenge_category_health_name,
            R.color.md_orange_500,
            R.color.md_orange_700
        ),
        FITNESS(
            R.string.challenge_category_fitness_name,
            R.color.md_green_500,
            R.color.md_green_700
        ),
        LEARNING(
            R.string.challenge_category_learning_name,
            R.color.md_blue_500,
            R.color.md_blue_700
        ),
        ORGANIZE(
            R.string.challenge_category_organize_name,
            R.color.md_teal_500,
            R.color.md_teal_700
        ),
        ADVENTURE(
            R.string.challenge_category_adventure_name,
            R.color.md_purple_500,
            R.color.md_purple_700
        )
    }
}