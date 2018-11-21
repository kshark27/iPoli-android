package io.ipoli.android.onboarding

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.di.UIModule
import io.ipoli.android.common.navigation.Navigator
import io.ipoli.android.common.view.Debounce
import io.ipoli.android.common.view.inflate
import io.ipoli.android.common.view.intRes
import io.ipoli.android.common.view.pager.BasePagerAdapter
import io.ipoli.android.common.view.rootRouter
import kotlinx.android.synthetic.main.controller_app_tour.view.*
import kotlinx.android.synthetic.main.item_app_tour_page.view.*
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

class AppTourViewController(args: Bundle? = null) : RestoreViewOnCreateController(
    args
), Injects<UIModule> {

    private val eventLogger by required { eventLogger }

    companion object {
        const val PAGE_COUNT = 5
    }

    private val onPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
        }

        override fun onPageSelected(position: Int) {
            eventLogger.logEvent("app_tour_change_page", mapOf("position" to position))
            view!!.appTourProgress.animateProgress((position + 1) * 20)
            view!!.appTourNext.setText(
                if (position + 1 == PAGE_COUNT) R.string.start
                else R.string.next
            )
        }
    }

    override fun onContextAvailable(context: Context) {
        inject(MyPoliApp.uiModule(context))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = container.inflate(R.layout.controller_app_tour)
        val adapter = PageAdapter()
        view.appTourPager.adapter = adapter
        adapter.updateAll(
            listOf(
                PageViewModel(
                    title = "Why myPoli?",
                    message = "myPoli helps you achieve your Goals by motivating you to complete your most important ToDos, Events & Habits. Easily plan your day and get more done!",
                    image = R.drawable.onboard_screen_1
                ),
                PageViewModel(
                    title = "Turn your life into a game",
                    message = "Figh procrastination & stay focused on your Tasks by turning your life into an RPG-style game! Track your progress on Habits & Goals to achieve anything!",
                    image = R.drawable.onboard_screen_2
                ),
                PageViewModel(
                    title = "Level Up",
                    message = "Complete your Quests (Tasks) to unlock features & get rewarded! Stay productive to Level up your avatar, boost your intelligence, strength, focus, well-being & willpower.",
                    image = R.drawable.onboard_screen_3
                ),
                PageViewModel(
                    title = "Take care of your new Pet",
                    message = "Always get reminded on time by your new pet. Take good care of it to get additional rewards & bonuses! Customize your pet with awesome items & make it unique!",
                    image = R.drawable.onboard_screen_4
                ),
                PageViewModel(
                    title = "Challenge Yourself",
                    message = "Complete step-by-step challenges for workout routines, nutrition plans, de-stress your life, learning new skills or just having fun. Let's do this!",
                    image = R.drawable.onboard_screen_5
                )
            )
        )

        view.appTourPager.addOnPageChangeListener(onPageChangeListener)

        view.appTourProgress.max = PAGE_COUNT * 20
        view.appTourProgress.progress = 20

        view.appTourPager.setPageTransformer(
            true
        ) { page, position ->
            val pageWidth = page.width

            if (0 <= position && position < 1) {
                page.translationX = pageWidth * -position
            }
            if (-1 < position && position < 0) {
                page.translationX = pageWidth * -position
            }

            if (position > -1.0f && position < 1.0f && position != 0.0f) {
                val translateX = pageWidth / 2 * position
                page.appTourImage.translationX = translateX
                page.appTourTitle.translationX = translateX
                page.appTourMessage.translationX = translateX
                val alpha = 1.0f - Math.abs(position)
                page.appTourImage.alpha = alpha
                page.appTourImage.scaleX = Math.max(alpha, 0.85f)
                page.appTourImage.scaleY = Math.max(alpha, 0.85f)
                page.appTourTitle.alpha = alpha
                page.appTourMessage.alpha = alpha
            }
        }

        view.appTourExistingPlayer.setOnClickListener(Debounce.clickListener {
            eventLogger.logEvent("app_tour_existing_player")
            Navigator(rootRouter).toAuth(isSigningUp = false)
        })

        view.appTourSkip.setOnClickListener(Debounce.clickListener {
            eventLogger.logEvent("app_tour_skip")
            Navigator(rootRouter).toOnboard()
        })

        view.appTourNext.setOnClickListener(Debounce.clickListener {
            val currentItem = view.appTourPager.currentItem
            if (currentItem + 1 == PAGE_COUNT) {
                eventLogger.logEvent("app_tour_done")
                Navigator(rootRouter).toOnboard()
            } else {
                view.appTourPager.setCurrentItem(currentItem + 1, true)
            }
        })

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        activity?.let {
            eventLogger.logCurrentScreen(it, "AppTour")
        }
    }

    override fun onDestroyView(view: View) {
        view.appTourPager.removeOnPageChangeListener(onPageChangeListener)
        super.onDestroyView(view)
    }

    data class PageViewModel(
        val title: String,
        val message: String,
        @DrawableRes val image: Int
    )

    inner class PageAdapter : BasePagerAdapter<PageViewModel>() {

        override fun layoutResourceFor(item: PageViewModel) = R.layout.item_app_tour_page

        override fun bindItem(item: PageViewModel, view: View) {
            view.appTourTitle.text = item.title
            view.appTourMessage.text = item.message
            view.appTourImage.setImageResource(item.image)
        }

    }

    private fun ProgressBar.animateProgress(to: Int) {
        val animator = ObjectAnimator.ofInt(this, "progress", progress, to)
        animator.duration = intRes(android.R.integer.config_shortAnimTime).toLong()
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }
}