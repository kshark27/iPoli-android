package io.ipoli.android.player.auth

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.crashlytics.android.Crashlytics
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.LoaderDialogController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.onboarding.OnboardData
import io.ipoli.android.player.auth.AuthViewState.StateType.*
import io.ipoli.android.player.auth.error.SignInError
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import kotlinx.android.synthetic.main.controller_auth.view.*
import kotlinx.android.synthetic.main.item_auth_avatar.view.*
import space.traversal.kapsule.required


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/5/18.
 */
class AuthViewController(args: Bundle? = null) :
    ReduxViewController<AuthAction, AuthViewState, AuthReducer>(args) {

    private val RC_SIGN_IN = 123

    override val reducer = AuthReducer

    private val sharedPreferences by required { sharedPreferences }

    private var loader: LoaderDialogController? = null

    private val usernameWatcher: TextWatcher = object : TextWatcher {

        override fun afterTextChanged(s: Editable) {
            renderUsernameLengthHint(s.length)
            dispatch(AuthAction.ValidateUsername(s.toString()))
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

        }

    }

    private var onboardData: OnboardData? = null

    constructor(onboardData: OnboardData?) : this() {
        this.onboardData = onboardData
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_auth, container, false)

        registerForActivityResult(RC_SIGN_IN)

        view.avatarList.layoutManager =
            LinearLayoutManager(activity!!, LinearLayoutManager.HORIZONTAL, false)
        view.avatarList.adapter = AvatarAdapter()

        view.username.setCompoundDrawablesRelativeWithIntrinsicBounds(
            usernameIcon, null, null, null
        )

        return view
    }

    private val usernameIcon
        get() = IconicsDrawable(activity)
            .normalIcon(
                GoogleMaterial.Icon.gmd_person,
                R.color.md_dark_text_54
            ).respectFontBounds(true)

    private val errorIcon
        get() = IconicsDrawable(activity)
            .normalIcon(
                GoogleMaterial.Icon.gmd_error,
                R.color.md_red_500
            ).respectFontBounds(true)

    private val validIcon
        get() = IconicsDrawable(activity)
            .normalIcon(
                GoogleMaterial.Icon.gmd_verified_user,
                R.color.md_green_500
            ).respectFontBounds(true)

    private fun createLoader() =
        LoaderDialogController(
            R.string.sign_in_loading_dialog_title,
            R.string.sign_in_loading_dialog_message
        )

    private fun renderUsernameLengthHint(length: Int) {
        @SuppressLint("SetTextI18n")
        view!!.usernameLengthHint.text = "$length/${Constants.USERNAME_MAX_LENGTH}"
    }

    override fun render(state: AuthViewState, view: View) {

        view.startJourney.setOnClickListener {
            if (state.type != USERNAME_VALIDATION_ERROR) {
                view.startJourney.isClickable = false
                dispatch(
                    AuthAction.CompleteSetup(
                        view.username.text.toString(),
                        state.playerAvatar
                    )
                )
                showLoader()
            }
        }

        when (state.type) {

            LOADING -> {
                // show loading
            }

            SHOW_LOGIN -> {
                renderLoginViews(view, state)

                view.googleSignIn.onDebounceClick {
                    showLoader()
                    startActivityForResult(
                        startSignUpForProvider(AuthUI.IdpConfig.GoogleBuilder().build()),
                        RC_SIGN_IN
                    )
                }
                view.facebookSignIn.onDebounceClick {
                    showLoader()
                    startActivityForResult(
                        startSignUpForProvider(AuthUI.IdpConfig.FacebookBuilder().build()),
                        RC_SIGN_IN
                    )
                }

                view.anonymousSignUp.onDebounceClick {
                    dispatch(AuthAction.ContinueAsGuest)
                    showShortToast(R.string.welcome_hero)
                }
            }

            SHOW_SETUP -> {
                view.username.setText(state.username)
                view.username.setSelection(state.username.length)
                view.loginContainer.invisible()
                view.setupContainer.visible()
                renderPlayerAvatars(view, state)
                view.signUpHeadline.setText(R.string.choose_your_avatar)
            }

            SWITCH_TO_SETUP -> {
                view.username.setText(state.username)
                view.username.setSelection(state.username.length)
                hideLoader()
                renderPlayerAvatars(view, state)
                playShowLoginAnimation(view) {
                    view.loginContainer.invisible()
                    view.loginPet.invisible()
                    view.signUpHeadline.setText(R.string.choose_your_avatar)
                }
            }

            AVATAR_CHANGED -> {
                renderPlayerAvatars(view, state)
            }

            USERNAME_VALIDATION_ERROR -> {
                view.startJourney.isClickable = true
                view.usernameValidationHint.text = state.usernameErrorMessage(activity!!)
                view.username.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    usernameIcon, null, errorIcon, null
                )
            }

            USERNAME_VALID -> {
                view.startJourney.isClickable = true
                view.usernameValidationHint.text = stringRes(R.string.valid_username)
                view.username.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    usernameIcon, null, validIcon, null
                )
            }

            PLAYER_SETUP_COMPLETED -> {
                hideLoader()
                if (state.isGuest) {
                    router.handleBack()
                    sharedPreferences.edit().remove(Constants.KEY_INVITE_PLAYER_ID).apply()
                } else {
                    showShortToast(R.string.welcome_hero)
                    startHomeViewController()
                    maybeShowInviteDialog()
                }
            }

            GUEST_CREATED -> {
                hideLoader()
                startHomeViewController()
                sharedPreferences.edit().remove(Constants.KEY_INVITE_PLAYER_ID).apply()
            }

            EXISTING_PLAYER_LOGGED_IN -> {
                hideLoader()
                showShortToast(R.string.welcome_back_hero)
                startHomeViewController()
                maybeShowInviteDialog()
            }

            EXISTING_PLAYER_LOGGED_IN_FROM_GUEST -> {
                hideLoader()
                showShortToast(R.string.welcome_back_hero)
                router.handleBack()
                maybeShowInviteDialog()
            }

            SHOW_IMPORT_ERROR -> {
                showLongToast(R.string.import_data_error_message)
                activity?.finish()
            }

            else -> {
            }
        }
    }

    private fun maybeShowInviteDialog() {
        sharedPreferences.getString(Constants.KEY_INVITE_PLAYER_ID, null)?.let {
            navigate().toAcceptFriendship(it)
            sharedPreferences.edit().remove(Constants.KEY_INVITE_PLAYER_ID).apply()
        }
    }

    private fun renderPlayerAvatars(
        view: View,
        state: AuthViewState
    ) {
        Glide.with(view.context).load(state.playerAvatarImage)
            .apply(RequestOptions.circleCropTransform())
            .into(view.playerAvatar)
        (view.avatarList.adapter as AvatarAdapter).updateAll(state.avatarViewModels)
    }

    private fun hideLoader() {
        loader?.dismiss()
        loader = null
    }

    private fun showLoader() {
        loader = createLoader()
        loader?.show(router, "loader")
    }

    private fun renderLoginViews(view: View, state: AuthViewState) {
        view.setupContainer.visibility = View.INVISIBLE
        if (state.isGuest)
            view.guestGroup.goneViews()
        else
            view.guestGroup.showViews()
        view.loginPet.visible = true
        view.signUpHeadline.setText(R.string.welcome_hero)
    }

    private fun playShowLoginAnimation(view: View, animationEnd: () -> Unit) {
        val setupContainer = view.setupContainer
        val loginContainer = view.loginContainer

        val set = AnimatorSet()
        set.playTogether(
            createContainerRevealAnimation(setupContainer),
            createContainerRevealAnimation(loginContainer, true)
        )
        set.interpolator = AccelerateDecelerateInterpolator()
        set.duration = longAnimTime

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                setupContainer.visible()
                view.setupContent.views().forEach {
                    it.alpha = 0f
                }
            }

            override fun onAnimationEnd(animation: Animator?) {
                view.setupContent.views().forEach {
                    it.animate().alpha(1f).start()
                }
                animationEnd()
            }
        })

        set.start()
    }

    private fun createContainerRevealAnimation(
        container: ViewGroup,
        reverse: Boolean = false
    ): Animator? {
        val halfWidth = container.width / 2
        val halfHeight = container.height / 2
        val radius = Math.max(halfWidth, halfHeight).toFloat()
        return ViewAnimationUtils
            .createCircularReveal(
                container,
                halfWidth,
                halfHeight,
                if (reverse) radius else 0f,
                if (reverse) 0f else radius
            )
    }

    private fun startHomeViewController() {
        navigateFromRoot().replaceWithHome(SimpleSwapChangeHandler())
    }

    private fun startSignUpForProvider(provider: AuthUI.IdpConfig): Intent {
        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(listOf(provider))
            .build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                dispatch(
                    AuthAction.UserAuthenticated(
                        FirebaseAuth.getInstance().currentUser!!,
                        response!!.isNewUser
                    )
                )
                return
            } else {
                hideLoader()
                val hasUserPressedBack = response == null
                if (hasUserPressedBack) {
                    return
                }

                val errorCode = response!!.error!!.errorCode
                if (errorCode == ErrorCodes.NO_NETWORK) {
                    showShortToast(R.string.sign_in_internet)
                    Crashlytics.logException(SignInError("Attempt to login without internet"))
                    return
                }

                if (errorCode == ErrorCodes.UNKNOWN_ERROR) {
                    showShortToast(R.string.something_went_wrong)
                    Crashlytics.logException(SignInError("Unknown login error with code $errorCode"))
                    return
                }
            }

        }
    }

    override fun onCreateLoadAction() = AuthAction.Load(onboardData)

    override fun onAttach(view: View) {
        super.onAttach(view)
        view.username.addTextChangedListener(usernameWatcher)
        renderUsernameLengthHint(0)
        enterFullScreen()
    }

    override fun onDetach(view: View) {
        exitFullScreen()
        view.username.removeTextChangedListener(usernameWatcher)
        super.onDetach(view)
    }

    data class AvatarViewModel(
        val name: String,
        @DrawableRes val image: Int,
        @ColorRes val backgroundColor: Int,
        val isSelected: Boolean,
        val avatar: Avatar
    ) : RecyclerViewViewModel {
        override val id: String
            get() = avatar.name

    }

    inner class AvatarAdapter :
        BaseRecyclerViewAdapter<AvatarViewModel>(R.layout.item_auth_avatar) {

        override fun onBindViewModel(vm: AvatarViewModel, view: View, holder: SimpleViewHolder) {
            view.avatarName.text = vm.name
            view.avatarImage.setBackgroundResource(vm.backgroundColor)
            view.avatarImage.setImageResource(vm.image)
            if (!vm.isSelected) {
                view.avatarImage.dispatchOnClick { AuthAction.ChangeAvatar(vm.avatar) }
                view.avatarImageSelected.gone()
            } else {
                view.avatarImage.setOnClickListener(null)
                view.avatarImageSelected.visible()
            }
        }

    }

    private val AuthViewState.playerAvatarImage: Int
        get() = AndroidAvatar.valueOf(playerAvatar.name).image

    private val AuthViewState.avatarViewModels: List<AvatarViewModel>
        get() = avatars.map {
            val androidAvatar = AndroidAvatar.valueOf(it.name)
            AvatarViewModel(
                name = stringRes(androidAvatar.avatarName),
                image = androidAvatar.image,
                backgroundColor = androidAvatar.backgroundColor,
                isSelected = it == playerAvatar,
                avatar = it
            )
        }

}