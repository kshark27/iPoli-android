package io.ipoli.android.challenge.preset

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.transition.ChangeBounds
import android.support.transition.TransitionManager
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.ListPopupWindow
import android.view.*
import android.widget.ArrayAdapter
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallengeViewState.StateType.*
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.controller_preset_challenge.view.*
import kotlinx.android.synthetic.main.item_preset_challenge_day.view.*
import kotlinx.android.synthetic.main.item_preset_challenge_habit.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 9/29/18.
 */
class PresetChallengeViewController(args: Bundle? = null) :
    ReduxViewController<PresetChallengeAction, PresetChallengeViewState, PresetChallengeReducer>(
        args
    ) {
    override val reducer = PresetChallengeReducer

    private lateinit var challenge: PresetChallenge

    constructor(challenge: PresetChallenge) : this() {
        this.challenge = challenge
    }

    override fun onCreateLoadAction() =
        PresetChallengeAction.Load(challenge)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_preset_challenge)

        val color500 = colorRes(challenge.color.androidColor.color500)
        view.presetAppbar.setBackgroundColor(color500)
        view.collapsingToolbarContainer.setContentScrimColor(color500)
        val color700 = colorRes(challenge.color.androidColor.color700)
        view.collapsingToolbarContainer.setStatusBarScrimColor(color700)

        setToolbar(view.toolbar)

        view.presetHabits.layoutManager = LinearLayoutManager(view.context)
        view.presetHabits.adapter = HabitAdapter()
        view.presetHabits.isNestedScrollingEnabled = false

        view.presetQuests.layoutManager = GridLayoutManager(view.context, 3)
        view.presetQuests.adapter = DayAdapter()

        view.presetQuests.isNestedScrollingEnabled = false

        return view
    }

    override fun onAttach(view: View) {
        showBackButton()
        super.onAttach(view)
        colorStatusBar(android.R.color.transparent)
    }

    private fun colorStatusBar(@ColorRes color: Int) {
        activity?.window?.let {
            it.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.statusBarColor = colorRes(color)
            it.navigationBarColor = colorRes(challenge.color.androidColor.color500)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            android.R.id.home ->
                router.handleBack()

            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: PresetChallengeViewState, view: View) {
        when (state.type) {

            DATA_CHANGED -> {
                view.presetAppbar.visible()
                view.presetContainer.visible()
                view.presetPrice.text = state.gemPrice!!.toString()

                Glide.with(view.context).load(Uri.parse(state.challenge!!.imageUrl))
                    .into(view.presetBackgroundImage)

                view.collapsingToolbarContainer.title = state.name

                renderSummary(view, state)
                renderDescription(view, state)
                renderRequirements(state, view)
                renderExpectedResults(state, view)

                if (state.isUnlocked!!) {
                    view.presetBuyContainer.gone()
                    view.acceptChallenge.visible()
                    view.presetPreferencesContainer.visible()
                    view.presetHabitsContainer.visible()
                    view.presetQuestContainer.visible()
                    renderTags(state, view)
                    renderStartTime(state, view)
                    renderHabits(view, state)
                    renderQuests(view, state)
                    view.acceptChallenge.dispatchOnClick {
                        PresetChallengeAction.Validate
                    }
                } else {
                    view.presetBuyContainer.visible()
                    view.acceptChallenge.gone()
                    view.presetPreferencesContainer.gone()
                    view.presetHabitsContainer.gone()
                    view.presetQuestContainer.gone()
                    view.presetBuy.dispatchOnClick {
                        PresetChallengeAction.Unlock(state.challenge)
                    }
                }

                renderAuthor(state, view)
            }

            START_TIME_CHANGED ->
                renderStartTime(state, view)

            TAGS_CHANGED ->
                renderTags(state, view)

            HABITS_CHANGED ->
                renderHabits(view, state)

            CHALLENGE_VALID -> {
                dispatch(
                    PresetChallengeAction.Accept(
                        state.challenge!!,
                        state.challengeTags!!,
                        state.startTime,
                        state.schedule!!,
                        state.physicalCharacteristics
                    )
                )
                showShortToast(R.string.challenge_accepted)
            }

            EMPTY_TAGS -> {
                showLongToast(R.string.preset_challenge_empty_tags_message)
                view.presetContainerLayout.post {
                    view.presetContainerLayout.smoothScrollTo(
                        view.presetPreferencesContainer.x.toInt(),
                        view.presetPreferencesContainer.y.toInt()
                    )
                }
            }

            EMPTY_SCHEDULE ->
                showLongToast(R.string.preset_challenge_empty_schedule_message)

            UNLOCKED ->
                showShortToast(R.string.challenge_unlocked)

            TOO_EXPENSIVE ->
                showShortToast(R.string.challenge_too_expensive)

            SHOW_CHARACTERISTICS_PICKER ->
                navigateFromRoot().toPhysicalCharacteristicsPicker { c ->
                    dispatch(PresetChallengeAction.PhysicalCharacteristicsPicked(c))
                }

            ALREADY_ACCEPTED ->
                showLongToast(R.string.preset_challenge_already_accepted)

            ACCEPTED -> {
                navigateFromRoot().toChallenge(state.challengeId!!)
                navigateFromRoot().toAddPost(
                    challengeId = state.challengeId,
                    questId = null,
                    habitId = null
                )
            }

            else -> {
            }
        }
    }

    private fun renderAuthor(
        state: PresetChallengeViewState,
        view: View
    ) {
        val author = state.author

        if (author != null) {
            view.authorContainer.visible()
            view.defaultAuthorContainer.gone()

            view.authorName.text = author.displayName
            val androidAvatar = AndroidAvatar.valueOf(author.avatar.name)
            Glide.with(view.context).load(androidAvatar.image)
                .apply(RequestOptions.circleCropTransform())
                .into(view.authorAvatar)

            val gradientDrawable = view.authorAvatar.background as GradientDrawable
            gradientDrawable.mutate()
            gradientDrawable.setColor(view.context.colorRes(androidAvatar.backgroundColor))

            view.authorUsername.text = "@${author.username}"
            view.authorLevel.text = "Level ${author.level}"

            view.authorContainer.onDebounceClick {
                navigateFromRoot().toProfile(author.id)
            }
        } else {
            view.authorContainer.gone()
            view.defaultAuthorContainer.visible()
        }
    }

    private fun renderStartTime(
        state: PresetChallengeViewState,
        view: View
    ) {
        if (state.showStartTime!!) {
            view.startTimeGroup.visible()
            view.startTime.text = state.startTimeText
            view.startTime.onDebounceClick {
                createTimePickerDialog(
                    startTime = state.startTime ?: Time.now(),
                    onTimePicked = { time ->
                        dispatch(PresetChallengeAction.ChangeStartTime(time))
                    }
                ).show(router)
            }
        } else {
            view.startTimeGroup.gone()
        }
    }

    private fun renderTags(
        state: PresetChallengeViewState,
        view: View
    ) {
        val challengeTags = state.challengeTags!!
        val tagCount = challengeTags.size
        if (tagCount >= 1) {
            view.tag1.visible()
            view.tag1.text = challengeTags[0].name
            renderTagIndicatorColor(view.tag1, challengeTags[0])
        } else view.tag1.gone()

        if (tagCount >= 2) {
            view.tag2.visible()
            view.tag2.text = challengeTags[1].name
            renderTagIndicatorColor(view.tag2, challengeTags[1])
        } else view.tag2.gone()

        if (tagCount == 3) {
            view.tag3.visible()
            view.tag3.text = challengeTags[2].name
            renderTagIndicatorColor(view.tag3, challengeTags[2])
        } else view.tag3.gone()

        view.tag1.onDebounceClick {
            dispatch(
                PresetChallengeAction.RemoveTag(
                    challengeTags[0]
                )
            )
        }
        view.tag2.onDebounceClick {
            dispatch(
                PresetChallengeAction.RemoveTag(
                    challengeTags[1]
                )
            )
        }
        view.tag3.onDebounceClick {
            dispatch(
                PresetChallengeAction.RemoveTag(
                    challengeTags[2]
                )
            )
        }

        if (state.showAddTag!!) {
            view.addTag.visible()
            val availableTags = state.availableTags

            val popupWindow = ListPopupWindow(activity!!)
            popupWindow.anchorView = view.addTag
            popupWindow.width = ViewUtils.dpToPx(200f, activity!!).toInt()
            popupWindow.setAdapter(TagAdapter(availableTags))
            popupWindow.setOnItemClickListener { _, _, position, _ ->
                dispatch(PresetChallengeAction.AddTag(availableTags[position]))
                popupWindow.dismiss()
            }


            view.addTag.onDebounceClick {
                popupWindow.show()
            }
        } else view.addTag.gone()
    }

    private fun renderTagIndicatorColor(view: TextView, tag: Tag) {
        val indicator = view.compoundDrawablesRelative[0] as GradientDrawable
        indicator.mutate()
        indicator.setColor(colorRes(AndroidColor.valueOf(tag.color.name).color500))
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(
            indicator,
            null,
            view.compoundDrawablesRelative[2],
            null
        )
    }

    private fun renderDescription(
        view: View,
        state: PresetChallengeViewState
    ) {
        val description = state.description
        val shortDesc = if (description.contains("\n")) {
            description.split("\n").first()
        } else {
            description.substring(0, Math.min(200, description.length))
        }

        view.presetDescription.setMarkdown(shortDesc)

        view.readMore.onDebounceClick {
            TransitionManager.beginDelayedTransition(
                view.presetDescriptionContainer,
                ChangeBounds()
            )
            view.presetDescription.setMarkdown(description)
            view.readMore.gone()
        }
    }

    private fun renderSummary(
        view: View,
        state: PresetChallengeViewState
    ) {
        view.presetDurationText.text = state.duration.intValue.toString()
        view.presetBusynessText.text =
            DurationFormatter.format(view.context, state.busynessPerWeek.intValue)
        view.presetDifficultyText.text = state.difficulty.name.toLowerCase().capitalize()
        if (state.level != null) {
            view.levelGroup.visible()
            view.presetLevelText.text = state.level.toString()
        } else {
            view.levelGroup.gone()
        }

        if (state.participantCount > 0) {
            view.presetParticipantCount.visible()
            view.presetParticipantCount.setCompoundDrawablesRelativeWithIntrinsicBounds(
                listItemIcon(GoogleMaterial.Icon.gmd_group).colorRes(colorTextSecondaryResource),
                null,
                null,
                null
            )

            view.presetParticipantCount.text =
                stringRes(R.string.preset_challenge_participants, state.participantCount)
        } else {
            view.presetParticipantCount.gone()
        }
    }

    private fun renderQuests(
        view: View,
        state: PresetChallengeViewState
    ) {
        (view.presetQuests.adapter as DayAdapter).updateAll(state.dayViewModels)
        if (state.schedule!!.quests.isEmpty()) {
            view.presetQuestContainer.gone()
        } else {
            view.presetQuestContainer.visible()
        }
    }

    private fun renderHabits(
        view: View,
        state: PresetChallengeViewState
    ) {
        (view.presetHabits.adapter as HabitAdapter).updateAll(state.habitViewModels)
        if (state.schedule!!.habits.isEmpty()) {
            view.presetHabitsContainer.gone()
        } else {
            view.presetHabitsContainer.visible()
        }
    }

    private fun renderExpectedResults(
        state: PresetChallengeViewState,
        view: View
    ) {
        if (state.expectedResults.isEmpty()) {
            view.presetExpectedResultsContainer.gone()
        } else {
            view.presetExpectedResultsContainer.visible()
            view.presetExpectedResults.setMarkdown(toMarkdownList(state.expectedResults))
        }
    }

    private fun renderRequirements(
        state: PresetChallengeViewState,
        view: View
    ) {
        if (state.requirements.isEmpty()) {
            view.presetRequirementsContainer.gone()
        } else {
            view.presetRequirementsContainer.visible()
            view.presetRequirements.setMarkdown(toMarkdownList(state.requirements))
        }
    }

    private fun toMarkdownList(stringList: List<String>) =
        stringList.joinToString(separator = "\n") {
            " - $it"
        }

    private val PresetChallengeViewState.dayViewModels: List<DayViewModel>
        get() {
            val scheduledDays = schedule!!.quests.map { it.day }.toSet()
            val allDays = (1..duration.intValue).toSet()
            val scheduled = schedule.quests.asSequence().map {
                DayViewModel(
                    id = it.day.toString(),
                    day = it.day,
                    text = if (it.subQuests.isNotEmpty()) toMarkdownList(it.subQuests) else it.name,
                    textColor = colorRes(colorTextPrimaryResource)
                )
            }.toMutableList()
            val restDays = allDays.minus(scheduledDays)
            restDays.forEach {
                scheduled.add(it - 1, DayViewModel(it.toString(), it, "REST DAY", colorAccent))
            }
            return scheduled
        }

    private val PresetChallengeViewState.habitViewModels: List<HabitViewModel>
        get() = schedule!!.habits.map {
            HabitViewModel(
                id = it.name,
                name = it.name,
                icon = it.icon.androidIcon.icon,
                color = it.color.androidColor.color500,
                isSelected = it.isSelected
            )
        }

    data class DayViewModel(
        override val id: String,
        val day: Int,
        val text: String,
        @ColorInt val textColor: Int
    ) : RecyclerViewViewModel

    inner class DayAdapter :
        BaseRecyclerViewAdapter<DayViewModel>(R.layout.item_preset_challenge_day) {

        override fun onBindViewModel(vm: DayViewModel, view: View, holder: SimpleViewHolder) {

            val bgColor =
                if (holder.adapterPosition % 2 == 0)
                    colorRes(colorSurfaceResource)
                else
                    colorRes(colorDividerResource)

            view.setBackgroundColor(bgColor)

            view.dayText.text = vm.day.toString()
            view.dayText.setTextColor(vm.textColor)

            view.dayContent.setMarkdown(vm.text)
            view.dayContent.setTextColor(vm.textColor)

        }
    }

    data class HabitViewModel(
        override val id: String,
        val name: String,
        val icon: IIcon,
        @ColorRes val color: Int,
        val isSelected: Boolean
    ) : RecyclerViewViewModel

    inner class HabitAdapter :
        BaseRecyclerViewAdapter<HabitViewModel>(R.layout.item_preset_challenge_habit) {

        override fun onBindViewModel(vm: HabitViewModel, view: View, holder: SimpleViewHolder) {
            view.habitName.text = vm.name
            view.habitIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))

            val icon = IconicsDrawable(view.context).listItemIcon(vm.icon)

            view.habitIcon.setImageDrawable(icon)

            view.habitCheck.setOnCheckedChangeListener(null)
            view.habitCheck.isChecked = vm.isSelected

            view.setOnClickListener {
                view.habitCheck.toggle()
            }

            view.habitCheck.setOnCheckedChangeListener { _, isChecked ->
                dispatch(PresetChallengeAction.ToggleSelectedHabit(vm.name, isChecked))
            }
        }
    }

    inner class TagAdapter(tags: List<Tag>) :
        ArrayAdapter<Tag>(
            activity!!,
            R.layout.item_tag_popup,
            tags
        ) {
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
            bindView(position, convertView, parent)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
            bindView(position, convertView, parent)

        private fun bindView(position: Int, convertView: View?, parent: ViewGroup): View {

            val view = if (convertView == null) {
                val inflater = LayoutInflater.from(context)
                inflater.inflate(R.layout.item_tag_popup, parent, false) as TextView
            } else {
                convertView as TextView
            }

            val item = getItem(position)
            view.text = item.name

            val color = AndroidColor.valueOf(item.color.name).color500
            val indicator = view.compoundDrawablesRelative[0] as GradientDrawable
            indicator.mutate()
            val size = ViewUtils.dpToPx(8f, view.context).toInt()
            indicator.setSize(size, size)
            indicator.setColor(colorRes(color))
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                indicator,
                null,
                null,
                null
            )
            return view
        }
    }

    private val PresetChallengeViewState.startTimeText
        get() = startTime?.toString(shouldUse24HourFormat) ?: stringRes(R.string.unscheduled)

    private val PresetChallengeViewState.availableTags
        get() = tags!!.filter {
            !challengeTags!!.contains(it)
        }

}