package io.ipoli.android.pet.usecase

import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.habit.usecase.CalculateHabitStreakUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import org.amshove.kluent.`should be equal to`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 30.11.17.
 */
class ApplyDamageToPlayerUseCaseSpek : Spek({
    describe("ApplyDamageToPlayerUseCase") {

        val today = LocalDate.now()

        val quest = Quest(
            name = "",
            color = Color.BLUE,
            scheduledDate = today,
            duration = 60
        )

        fun executeUseCase(
            questRepository: QuestRepository,
            player: Player = TestUtil.player,
            date: LocalDate = today
        ) =
            ApplyDamageToPlayerUseCase(
                questRepository,
                TestUtil.playerRepoMock(player),
                TestUtil.habitRepoMock(null),
                mock(),
                CalculateHabitStreakUseCase(),
                randomSeed = 42
            ).execute(
                ApplyDamageToPlayerUseCase.Params(date)
            )

        it("should not apply damage when nothing to do") {
            val r = executeUseCase(questRepository = TestUtil.questRepoMock())
            r.totalDamage.`should be equal to`(0)
        }

        it("should apply damage for single Quest") {
            val r = executeUseCase(questRepository = mock { _ ->
                on { findScheduledAt(today) } doAnswer {
                    listOf(quest)
                }
            })
            r.totalDamage.`should be equal to`(ApplyDamageToPlayerUseCase.QUEST_BASE_DAMAGE)
        }

        it("should apply damage for Quest from Challenge") {
            val r = executeUseCase(questRepository = mock { _ ->
                on { findScheduledAt(today) } doAnswer {
                    listOf(quest.copy(challengeId = "123"))
                }
            })
            val expectedDamage =
                ApplyDamageToPlayerUseCase.QUEST_BASE_DAMAGE + ApplyDamageToPlayerUseCase.CHALLENGE_DAMAGE
            r.totalDamage.`should be equal to`(expectedDamage)
        }

        it("should not apply damage when all Quests are complete") {
            val r = executeUseCase(questRepository = mock { _ ->
                on { findScheduledAt(today) } doAnswer {
                    listOf(quest.copy(completedAtDate = today))
                }
            })
            r.totalDamage.`should be equal to`(0)
        }

        it("should apply damage from previous day Quest") {
            val questRepo = mock<QuestRepository> { _ ->
                on { findScheduledAt(today.minusDays(1)) } doAnswer {
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(20)
                        )
                    )
                }
            }

            val p = TestUtil.player

            val r = executeUseCase(
                questRepository = questRepo,
                player = p.copy(
                    preferences = p.preferences.copy(resetDayTime = Time.atHours(18))
                )
            )
            r.totalDamage.`should be equal to`(ApplyDamageToPlayerUseCase.QUEST_BASE_DAMAGE)
        }

        it("should apply damage from previous day Quest only in the interval") {
            val questRepo = mock<QuestRepository> { _ ->
                on { findScheduledAt(today.minusDays(1)) } doAnswer {
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(20)
                        ),
                        quest.copy(
                            startTime = Time.atHours(16)
                        )
                    )
                }
            }

            val p = TestUtil.player

            val r = executeUseCase(
                questRepository = questRepo,
                player = p.copy(
                    preferences = p.preferences.copy(resetDayTime = Time.atHours(18))
                )
            )
            r.totalDamage.`should be equal to`(ApplyDamageToPlayerUseCase.QUEST_BASE_DAMAGE)
        }

        it("should not apply damage from previous day unscheduled Quest") {
            val questRepo = mock<QuestRepository> { _ ->
                on { findScheduledAt(today.minusDays(1)) } doAnswer {
                    listOf(quest)
                }
            }

            val p = TestUtil.player

            val r = executeUseCase(
                questRepository = questRepo,
                player = p.copy(
                    preferences = p.preferences.copy(resetDayTime = Time.atHours(18))
                )
            )
            r.totalDamage.`should be equal to`(0)
        }

        it("should apply damage from previous day unscheduled Quest") {
            val questRepo = mock<QuestRepository> { _ ->
                on { findScheduledAt(today) } doAnswer {
                    listOf(quest)
                }
            }

            val p = TestUtil.player

            val r = executeUseCase(
                questRepository = questRepo,
                player = p.copy(
                    preferences = p.preferences.copy(resetDayTime = Time.atHours(18))
                )
            )
            r.totalDamage.`should be equal to`(ApplyDamageToPlayerUseCase.QUEST_BASE_DAMAGE)
        }
    }
})