package mypoli.android.common.redux

import org.amshove.kluent.`should be equal to`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 01/21/2018.
 */
object StateStoreSpek : Spek({

    describe("StateStore") {

        class TestState : State

        class TestAction : Action

        class StopMiddleware : MiddleWare<TestState> {
            override fun execute(
                state: TestState,
                dispatcher: Dispatcher,
                action: Action
            ) = MiddleWare.Result.Stop
        }

        var executeCount = 0

        beforeEachTest {
            executeCount = 0
        }

        val testReducer = object : Reducer<TestState, Action> {
            override fun reduce(state: TestState, action: Action): TestState {
                executeCount++
                return state
            }

            override fun defaultState() = TestState()
        }

        it("should call the reducer with no middleware") {
            val store = StateStore<TestState>(testReducer)
            store.dispatch(TestAction())

            executeCount.`should be equal to`(1)
        }

        it("should not call reducer with stopping middleware") {
            val store = StateStore<TestState>(testReducer, listOf(StopMiddleware()))
            store.dispatch(TestAction())

            executeCount.`should be equal to`(0)
        }
    }
})