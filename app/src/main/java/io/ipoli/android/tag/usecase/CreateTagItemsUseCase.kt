package io.ipoli.android.tag.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.Quest
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/06/2018.
 */
class CreateTagItemsUseCase :
    UseCase<CreateTagItemsUseCase.Params, List<CreateTagItemsUseCase.TagItem>> {

    override fun execute(parameters: Params): List<TagItem> {

        val qs = parameters.quests.sortedWith(
            compareByDescending<Quest> { it.completedAtDate }
                .thenBy { it.completedAtTime?.toMinuteOfDay() }
                .thenBy { it.scheduledDate }
                .thenBy { it.startTime?.toMinuteOfDay() }
        )

        val (completed, incomplete) = qs.partition { it.isCompleted }
        val (today, otherDays) = incomplete.partition { it.scheduledDate == parameters.currentDate }
        val tomorrowDate = parameters.currentDate.plusDays(1)
        val (tomorrow, otherDays1) = otherDays.partition { it.scheduledDate == tomorrowDate }

        val (unscheduled, scheduled) = otherDays1.partition { it.scheduledDate == null }

        val (upcoming, past) = scheduled.partition { it.scheduledDate!!.isAfter(tomorrowDate) }

        return createSectionWithQuests(TagItem.Unscheduled, unscheduled) +
            createSectionWithQuests(TagItem.Today, today) +
            createSectionWithQuests(TagItem.Tomorrow, tomorrow) +
            createSectionWithQuests(TagItem.Upcoming, upcoming) +
            createSectionWithQuests(TagItem.Previous, past) +
            createSectionWithQuests(TagItem.Completed, completed)
    }

    private fun createSectionWithQuests(
        sectionItem: TagItem,
        quests: List<Quest>
    ): List<TagItem> {
        if (quests.isEmpty()) {
            return emptyList()
        }
        val items = mutableListOf(sectionItem)
        items.addAll(quests.map { TagItem.QuestItem(it) })
        return items
    }

    data class Params(val quests: List<Quest>, val currentDate: LocalDate)

    sealed class TagItem {
        data class QuestItem(val quest: Quest) : TagItem()
        object Unscheduled : TagItem()
        object Today : TagItem()
        object Tomorrow : TagItem()
        object Upcoming : TagItem()
        object Completed : TagItem()
        object Previous : TagItem()
    }
}