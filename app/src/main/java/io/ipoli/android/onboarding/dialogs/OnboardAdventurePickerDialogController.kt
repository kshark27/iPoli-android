package io.ipoli.android.onboarding.dialogs

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.R
import io.ipoli.android.common.view.BaseDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.stringRes
import kotlinx.android.synthetic.main.dialog_onboard_pick_avatar.view.*
import kotlinx.android.synthetic.main.item_onboard_adventure.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

class OnboardAdventurePickerDialogController(args: Bundle? = null) : BaseDialogController(args) {

    private val selectedAdventures = mutableSetOf<Adventure>()

    private var listener: (List<Adventure>) -> Unit = {}

    constructor(listener: (List<Adventure>) -> Unit) : this() {
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View?) {
        headerView!!.dialogHeaderTitle.setText(R.string.choose_adventure)
        headerView.dialogHeaderIcon.setImageResource(R.drawable.logo)
        val background = headerView.dialogHeaderIcon.background as GradientDrawable
        background.setColor(colorRes(R.color.md_light_text_100))
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_onboard_pick_avatar, null)
        view.avatarList.layoutManager = LinearLayoutManager(view.context)
        val adapter = AdventureAdapter()
        view.avatarList.adapter = adapter

        adapter.updateAll(
            listOf(
                AdventureViewModel(
                    stringRes(R.string.challenge_category_health_name),
                    stringRes(R.string.challenge_category_health_description),
                    Adventure.HEALTH
                ),
                AdventureViewModel(
                    stringRes(R.string.adventure_fitness),
                    stringRes(R.string.adventure_fitness_description),
                    Adventure.FITNESS
                ),
                AdventureViewModel(
                    stringRes(R.string.adventure_learning),
                    stringRes(R.string.adventure_learning_description),
                    Adventure.LEARNING
                ),
                AdventureViewModel(
                    stringRes(R.string.adventure_work),
                    stringRes(R.string.adventure_work_description),
                    Adventure.WORK
                ),
                AdventureViewModel(
                    stringRes(R.string.adventure_chores),
                    stringRes(R.string.adventure_chores_description),
                    Adventure.CHORES
                ),
                AdventureViewModel(
                    stringRes(R.string.adventure_family_time),
                    stringRes(R.string.adventure_family_time_description),
                    Adventure.FAMILY_TIME
                )
            )
        )
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog = dialogBuilder
        .setPositiveButton("Done") { _, _ ->
            listener(selectedAdventures.toList())
        }
        .create()

    data class AdventureViewModel(
        val name: String,
        val shortDescription: String,
        val type: Adventure
    ) : RecyclerViewViewModel {
        override val id: String
            get() = name
    }

    inner class AdventureAdapter :
        BaseRecyclerViewAdapter<AdventureViewModel>(R.layout.item_onboard_adventure) {

        override fun onBindViewModel(vm: AdventureViewModel, view: View, holder: SimpleViewHolder) {
            view.adventureName.text = vm.name
            view.adventureShortDescription.text = vm.shortDescription
            view.adventureCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedAdventures.add(vm.type) else selectedAdventures.remove(vm.type)
            }

            view.setOnClickListener {
                view.adventureCheckBox.setOnCheckedChangeListener(null)
                val newChecked = !view.adventureCheckBox.isChecked
                view.adventureCheckBox.isChecked = newChecked
                if (newChecked) selectedAdventures.add(vm.type) else selectedAdventures.remove(vm.type)
                view.adventureCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedAdventures.add(vm.type) else selectedAdventures.remove(vm.type)
                }
            }
        }
    }

    enum class Adventure {
        HEALTH, FITNESS, LEARNING, WORK, CHORES, FAMILY_TIME
    }
}