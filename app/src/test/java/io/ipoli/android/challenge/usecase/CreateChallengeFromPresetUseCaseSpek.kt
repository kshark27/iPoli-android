package io.ipoli.android.challenge.usecase

import io.ipoli.android.TestUtil
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.usecase.CreateChallengeFromPresetUseCase.PhysicalCharacteristics.Gender
import io.ipoli.android.challenge.usecase.CreateChallengeFromPresetUseCase.PhysicalCharacteristics.Units
import io.ipoli.android.common.datetime.days
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.repeatingquest.usecase.SaveQuestsForRepeatingQuestUseCase
import io.ipoli.android.repeatingquest.usecase.SaveRepeatingQuestUseCase
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be in range`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.LocalDate
import java.util.*

class CreateChallengeFromPresetUseCaseSpek : Spek({

    describe("CreateChallengeFromPresetUseCase") {

        fun createPresetChallenge(
            quests: List<PresetChallenge.Quest> = emptyList(),
            habits: List<PresetChallenge.Habit> = emptyList()
        ) =
            PresetChallenge(
                id = UUID.randomUUID().toString(),
                name = "c",
                color = Color.BLUE,
                icon = Icon.ACADEMIC,
                shortDescription = "",
                description = "",
                category = PresetChallenge.Category.ADVENTURE,
                imageUrl = "",
                duration = 30.days,
                busynessPerWeek = 120.minutes,
                difficulty = Challenge.Difficulty.EASY,
                requirements = emptyList(),
                level = 1,
                trackedValues = emptyList(),
                expectedResults = emptyList(),
                gemPrice = 0,
                note = "",
                config = PresetChallenge.Config(),
                schedule = PresetChallenge.Schedule(
                    quests = quests,
                    habits = habits
                ),
                status = Post.Status.APPROVED,
                author = null,
                participantCount = 0
            )

        fun createNutritionChallenge() =
            createPresetChallenge().copy(
                config = createPresetChallenge().config.copy(
                    nutritionMacros = PresetChallenge.NutritionMacros(
                        female = PresetChallenge.NutritionDetails(
                            caloriesPerKg = 1f,
                            proteinPerKg = 1f,
                            carbohydratesPerKg = 1f,
                            fatPerKg = 1f
                        ),
                        male = PresetChallenge.NutritionDetails(
                            caloriesPerKg = 1f,
                            proteinPerKg = 1f,
                            carbohydratesPerKg = 1f,
                            fatPerKg = 1f
                        )
                    )
                )
            )

        fun executeUseCase(params: CreateChallengeFromPresetUseCase.Params): Challenge {
            val sc = SaveChallengeUseCase(
                TestUtil.challengeRepoMock(),
                SaveQuestsForChallengeUseCase(
                    questRepository = TestUtil.questRepoMock(),
                    repeatingQuestRepository = TestUtil.repeatingQuestRepoMock(),
                    saveRepeatingQuestUseCase = SaveRepeatingQuestUseCase(
                        questRepository = TestUtil.questRepoMock(),
                        repeatingQuestRepository = TestUtil.repeatingQuestRepoMock(),
                        saveQuestsForRepeatingQuestUseCase = SaveQuestsForRepeatingQuestUseCase(
                            TestUtil.questRepoMock(),
                            mock()
                        ),
                        reminderScheduler = mock()
                    )
                ),
                TestUtil.habitRepoMock(null)
            )

            return CreateChallengeFromPresetUseCase(sc, mock()).execute(params)
        }

        it("should create Challenge with Quests") {
            val pc = createPresetChallenge(
                quests = listOf(
                    PresetChallenge.Quest(
                        name = "q1",
                        color = Color.BLUE,
                        icon = Icon.ACADEMIC,
                        day = 1,
                        duration = 30.minutes,
                        subQuests = emptyList(),
                        note = ""
                    )
                )
            )

            val c = executeUseCase(
                CreateChallengeFromPresetUseCase.Params(
                    preset = pc,
                    schedule = pc.schedule
                )
            )

            c.quests.size.`should be equal to`(1)
            c.quests.first().scheduledDate.`should equal`(LocalDate.now())
        }

        it("should create Challenge with Habits") {
            val pc = createPresetChallenge(
                habits = listOf(
                    PresetChallenge.Habit(
                        name = "q1",
                        color = Color.BLUE,
                        icon = Icon.ACADEMIC,
                        isGood = true,
                        timesADay = 3,
                        isSelected = true
                    )
                )
            )

            val c = executeUseCase(
                CreateChallengeFromPresetUseCase.Params(
                    preset = pc,
                    schedule = pc.schedule
                )
            )

            c.habits.size.`should be equal to`(1)
            c.habits.first().timesADay.`should be equal to`(3)
        }

        it("should create Challenge that tracks weight") {
            val pc = createNutritionChallenge()

            val c = executeUseCase(
                CreateChallengeFromPresetUseCase.Params(
                    preset = pc,
                    schedule = pc.schedule,
                    playerPhysicalCharacteristics = CreateChallengeFromPresetUseCase.PhysicalCharacteristics(
                        units = Units.METRIC,
                        gender = Gender.FEMALE,
                        weight = 60,
                        targetWeight = 50
                    )
                )
            )

            val t =
                c.trackedValues
                    .asSequence()
                    .filterIsInstance(Challenge.TrackedValue.Target::class.java)
                    .first()
            t.startValue.`should be in range`(59.99, 60.01)
            t.targetValue.`should be in range`(49.99, 50.01)
        }

        it("should create Challenge that tracks macros") {
            val pc = createNutritionChallenge()

            val c = executeUseCase(
                CreateChallengeFromPresetUseCase.Params(
                    preset = pc,
                    schedule = pc.schedule,
                    playerPhysicalCharacteristics = CreateChallengeFromPresetUseCase.PhysicalCharacteristics(
                        units = Units.METRIC,
                        gender = Gender.FEMALE,
                        weight = 60,
                        targetWeight = 50
                    )
                )
            )

            val atvs =
                c.trackedValues
                    .filterIsInstance(Challenge.TrackedValue.Average::class.java)

            atvs.size.`should be equal to`(4)

            atvs.forEach {
                it.targetValue.`should be in range`(59.99, 60.01)
            }
        }
    }
})

