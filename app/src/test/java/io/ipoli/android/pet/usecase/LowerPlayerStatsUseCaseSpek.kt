package io.ipoli.android.pet.usecase

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.Constants
import io.ipoli.android.TestUtil
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.pet.Pet
import io.ipoli.android.player.AttributePointsForLevelGenerator
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.any
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 30.11.17.
 */
class LowerPlayerStatsUseCaseSpek : Spek({
    describe("LowerPlayerStatsUseCase") {

        val quest = Quest(
            name = "",
            color = Color.BLUE,
            scheduledDate = LocalDate.now(),
            duration = 60
        )

        val player = TestUtil.player.copy(
            pet = TestUtil.player.pet.copy(
                healthPoints = 40,
                moodPoints = Pet.AWESOME_MIN_MOOD_POINTS - 1
            ),
            health = Player.Health(100, Constants.DEFAULT_PLAYER_MAX_HP)
        )

        val playerRepo = TestUtil.playerRepoMock(player)

        val now = LocalDateTime.now().with(DayOfWeek.TUESDAY)
        val yesterday = now.minusDays(1).toLocalDate()

        fun executeUseCase(
            questRepository: QuestRepository,
            playerRepository: PlayerRepository = playerRepo,
            date: LocalDateTime = now
        ) =
            LowerPlayerStatsUseCase(
                questRepository,
                playerRepository,
                randomSeed = 42
            ).execute(
                LowerPlayerStatsUseCase.Params(date)
            )

        describe("pet") {

            it("should lower MAX HP & MP when no quests are done") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf<Quest>()
                }

                val newPlayer = executeUseCase(questRepo)
                newPlayer.pet.healthPoints.`should be equal to`(4)
                newPlayer.pet.moodPoints.`should be equal to`(34)
                newPlayer.health.current.`should be equal to`(player.health.current - 27)
            }

            it("should lower randomly HP & MP when all time is scheduled & quest(s) is complete") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf(
                        quest.copy(
                            duration = LowerPlayerStatsUseCase.PLAN_DAY_HIGH_PRODUCTIVE_HOURS * Time.MINUTES_IN_AN_HOUR,
                            startTime = player.preferences.resetDayTime,
                            completedAtDate = yesterday
                        )
                    )
                }

                val newPlayer = executeUseCase(questRepo)
                newPlayer.pet.healthPoints.`should be equal to`(34)
                newPlayer.pet.moodPoints.`should be equal to`(83)
                newPlayer.health.current.`should be equal to`(player.health.current - 5)
            }

            it("should lower stats with low penalty when medium < duration < high productive hours") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf(
                        quest.copy(
                            duration = LowerPlayerStatsUseCase.PLAN_DAY_MEDIUM_PRODUCTIVE_HOURS * Time.MINUTES_IN_AN_HOUR,
                            startTime = player.preferences.resetDayTime,
                            completedAtDate = yesterday
                        )
                    )
                }

                val newPlayer = executeUseCase(questRepo)
                newPlayer.pet.healthPoints.`should be equal to`(30)
                newPlayer.pet.moodPoints.`should be equal to`(79)
                newPlayer.health.current.`should be equal to`(player.health.current - 7)
            }

            it("should lower stats with medium penalty when low < duration < medium productive hours") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf(
                        quest.copy(
                            duration = LowerPlayerStatsUseCase.PLAN_DAY_LOW_PRODUCTIVE_HOURS * Time.MINUTES_IN_AN_HOUR,
                            startTime = player.preferences.resetDayTime,
                            completedAtDate = yesterday
                        )
                    )
                }

                val newPlayer = executeUseCase(questRepo)
                newPlayer.pet.healthPoints.`should be equal to`(19)
                newPlayer.pet.moodPoints.`should be equal to`(34)
                newPlayer.health.current.`should be equal to`(player.health.current - 10)
            }

            it("should lower stats with high penalty when duration < low productive hours") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf(
                        quest.copy(
                            duration = (LowerPlayerStatsUseCase.PLAN_DAY_LOW_PRODUCTIVE_HOURS * Time.MINUTES_IN_AN_HOUR) - 1,
                            startTime = player.preferences.resetDayTime,
                            completedAtDate = yesterday
                        )
                    )
                }

                val newPlayer = executeUseCase(questRepo)
                newPlayer.pet.healthPoints.`should be equal to`(9)
                newPlayer.pet.moodPoints.`should be equal to`(34)
                newPlayer.health.current.`should be equal to`(player.health.current - 21)
            }

            it("should add additional damage to adept player") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf<Quest>()
                }

                val playerRepository = TestUtil.playerRepoMock(
                    player.copy(
                        level = 20,
                        attributes = mapOf(
                            Player.AttributeType.STRENGTH to Player.Attribute(
                                type = Player.AttributeType.STRENGTH,
                                points = 0,
                                level = 20,
                                pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
                                tags = emptyList()
                            )
                        ),
                        rank = Player.Rank.ADEPT
                    )
                )

                val newPlayer = executeUseCase(questRepo, playerRepository)
                newPlayer.pet.healthPoints.`should be equal to`(4)
                newPlayer.pet.moodPoints.`should be equal to`(34)
                newPlayer.health.current.`should be equal to`(player.health.current - 35)
            }

            it("should add additional damage to player > adept") {

                val questRepo = mock<QuestRepository> {
                    on { findCompletedForDate(any()) } doReturn listOf<Quest>()
                }

                val playerRepository = TestUtil.playerRepoMock(
                    player.copy(
                        level = 30,
                        attributes = mapOf(
                            Player.AttributeType.STRENGTH to Player.Attribute(
                                type = Player.AttributeType.STRENGTH,
                                points = 0,
                                level = 30,
                                pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
                                tags = emptyList()
                            )
                        ),
                        rank = Player.Rank.SPECIALIST
                    )
                )

                val newPlayer = executeUseCase(questRepo, playerRepository)
                newPlayer.pet.healthPoints.`should be equal to`(4)
                newPlayer.pet.moodPoints.`should be equal to`(34)
                newPlayer.health.current.`should be equal to`(player.health.current - 35)
            }
        }

        describe("filterQuestsInInterval") {

            it("should not include quest before startDate") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now(),
                    null,
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(9),
                            duration = 60,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(0)
            }

            it("should not include quest after end") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now().minusDays(1),
                    LocalDate.now(),
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(10),
                            duration = 60,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(0)
            }

            it("should include quest at interval startDate") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now(),
                    null,
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(10),
                            duration = 60,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(60)
            }

            it("should include quest at interval end") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now().minusDays(1),
                    LocalDate.now(),
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.at(8, 59),
                            duration = 60,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(60)
            }

            it("should include quest starting before interval") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now(),
                    null,
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.at(9, 59),
                            duration = 61,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(60)
            }

            it("should include quest ending after interval") {

                val duration = LowerPlayerStatsUseCase.findQuestsDurationInInterval(
                    LocalDate.now().minusDays(1),
                    LocalDate.now(),
                    Time.atHours(10),
                    listOf(
                        quest.copy(
                            startTime = Time.atHours(9),
                            duration = 61,
                            completedAtDate = LocalDate.now()
                        )
                    )
                )
                duration.`should be equal to`(60)
            }
        }
    }
})