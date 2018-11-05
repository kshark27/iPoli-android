package io.ipoli.android.challenge.preset.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import com.google.firebase.auth.FirebaseAuth
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.navigation.Navigator
import io.ipoli.android.common.view.*
import kotlinx.android.synthetic.main.controller_challenge_category_list.view.*
import kotlinx.android.synthetic.main.view_default_toolbar.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/29/17.
 */
class ChallengeCategoryListViewController(args: Bundle? = null) :
    RestoreViewOnCreateController(args) {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_challenge_category_list, container, false)
        setToolbar(view.toolbar)
        toolbarTitle = stringRes(R.string.predefined_challenges_title)

        view.health.setOnClickListener { showChallengeList(PresetChallenge.Category.HEALTH) }
        view.fitness.setOnClickListener { showChallengeList(PresetChallenge.Category.FITNESS) }
        view.learning.setOnClickListener { showChallengeList(PresetChallenge.Category.LEARNING) }
        view.adventure.setOnClickListener { showChallengeList(PresetChallenge.Category.ADVENTURE) }
        view.organizeLife.setOnClickListener { showChallengeList(PresetChallenge.Category.ORGANIZE) }

        view.addChallenge.setOnClickListener(Debounce.clickListener {
            if(FirebaseAuth.getInstance().currentUser == null) {
                showLongToast(R.string.add_preset_challenge_sign_in_required)
                return@clickListener
            }
            Navigator(rootRouter).toAddPresetChallenge()
        })

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        colorLayout()
    }

    private fun colorLayout() {
        activity?.window?.statusBarColor = attrData(io.ipoli.android.R.attr.colorPrimaryDark)
        activity?.window?.navigationBarColor = attrData(io.ipoli.android.R.attr.colorPrimary)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showChallengeList(category: PresetChallenge.Category) {
        Navigator(rootRouter).toChallengeListForCategory(category)
    }
}