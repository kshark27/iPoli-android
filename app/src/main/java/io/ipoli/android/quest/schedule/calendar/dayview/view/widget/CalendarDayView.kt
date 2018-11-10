package io.ipoli.android.quest.schedule.calendar.dayview.view.widget

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.database.DataSetObserver
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.annotation.LayoutRes
import android.support.transition.AutoTransition
import android.support.transition.TransitionManager
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.view.AndroidColor
import io.ipoli.android.common.view.visible
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.util.PositionToTimeMapper
import kotlinx.android.synthetic.main.view_calendar_day.view.*
import java.lang.Math.*
import kotlin.math.max
import kotlin.reflect.KClass

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/2/17.
 */

class CalendarDayView : FrameLayout, StateChangeListener {

    class FSM(initialState: State, private val listener: StateChangeListener) {

        interface Action<in E : Event> {
            fun execute(state: State, event: E): State
        }

        private var currentState: State = initialState
        private val actions = mutableMapOf<Pair<State.Type, KClass<*>>, Action<*>>()

        init {
            listener.onStateChanged(currentState)
        }

        val state: State
            get() = currentState

        fun <E : Event> transition(
            given: State.Type,
            on: KClass<E>,
            execute: (state: State, event: E) -> State
        ) {
            val a = object : Action<E> {
                override fun execute(state: State, event: E): State {
                    val newState = execute(state, event)
//                    Timber.d("Transition given ${state.type} when $event to ${newState.type}")
                    return newState
                }
            }
            actions[Pair(given, on)] = a
        }

        fun <E : Event> fire(event: E) {
            val actionKey = Pair(currentState.type, event::class)
            if (actionKey !in actions) {
                return
//                throw ActionNotFound(actionKey)
            }
            @Suppress("UNCHECKED_CAST")
            val a = actions[actionKey] as Action<E>
            currentState = a.execute(currentState, event)
            listener.onStateChanged(currentState)
        }
    }

    sealed class Event {
        object CompleteEdit : Event()
        object CompleteEditRequest : Event()
        object CancelEdit : Event()
        object Up : Event()
        object LayoutChange : Event()
        data class StartCalendarEventEdit(val view: View, val position: Int) : Event()
        data class Scroll(val deltaY: Int) : Event()
        data class StartUnscheduledEventEdit(val view: View, val position: Int) : Event()
        data class StartCalendarEventAdd(
            val startTime: Time,
            val duration: Int,
            val name: String,
            val backgroundColor: AndroidColor
        ) : Event()

        data class Drag(val y: Float) : Event()
        data class DragTopIndicator(val y: Float) : Event()
        data class DragBottomIndicator(val y: Float) : Event()
        object StartEditName : Event()
        data class ZoomStart(val zoomDistance: Float) : Event()
        data class Zoom(val zoomDistance: Float, val focusY: Float, val scaleFactor: Float) :
            Event()

        object ZoomEnd : Event()
        object RemoveEvent : Event()
    }

    companion object {
        const val DEFAULT_VISIBLE_HOURS = 10
        const val MIN_VISIBLE_HOURS = 6
        const val MAX_VISIBLE_HOURS = 16
        const val MIN_EVENT_DURATION = 15
        val MAX_EVENT_DURATION = Time.h2Min(4)
        const val KEYBOARD_VISIBLE_THRESHOLD_DP = 100f
    }

    data class State(
        val type: Type,
        val topDragViewPosition: Float? = null,
        val topDragIndicatorPosition: Float? = null,
        val bottomDragIndicatorPosition: Float? = null,
        val eventAdapterPosition: Int? = null,
        val unscheduledEventAdapterPosition: Int? = null,
        val eventId: String = "",
        val height: Int? = null,
        val isNewEvent: Boolean = false,
        val visibleHours: Int?,
        val hourHeight: Float = 0f,
        val zoomDistance: Float? = null,
        val isScrollLocked: Boolean = false,
        val isEditing: Boolean = false,
        val isKeyboardOpen: Boolean = false,
        val keyboardHeight: Int = 0
    ) {
        enum class Type {
            VIEW, EDIT, DRAG, ZOOM
        }

        val minuteHeight = hourHeight / 60f
    }

    private val inflater by lazy {
        LayoutInflater.from(context)
    }

    private val timeChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_TIME_TICK == intent.action) {
                moveTimeLineToNow()
            }
        }
    }

    private val zoomListener: (View, MotionEvent) -> Boolean = { _, e ->
        scaleDetector.onTouchEvent(e)
        false
    }

    private var dragView: View? = null
    private var lastY: Float? = null

    private lateinit var fsm: FSM

    private lateinit var dragImage: Drawable
    private var dragImageSize: Int = toPx(16)
    private var dragImagePadding: Int = toPx(0)
    private var initialVisibleHours: Int = 0
    @LayoutRes
    private var timeLineLayout: Int = 0

    private lateinit var topDragView: View
    private lateinit var bottomDragView: View
    private lateinit var scaleDetector: ScaleGestureDetector

    private var scheduledEventsAdapter: ScheduledEventsAdapter<*>? = null
    private var unscheduledEventsAdapter: UnscheduledEventsAdapter<*>? = null

    private var listener: CalendarChangeListener? = null

    private val eventViews = mutableListOf<View>()
    private lateinit var timeLineView: View

    private val keyboardVisibleThreshold = Math.round(
        ViewUtils.dpToPx(KEYBOARD_VISIBLE_THRESHOLD_DP, context)
    )

    private val r = Rect()

    private val dataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            clearAndAddEventsFromAdapter()
        }

        override fun onInvalidated() {
            clearCalendarEvents()
        }
    }

    private fun clearCalendarEvents() {
        for (v in eventViews) {
            eventContainer.removeView(v)
        }
        eventViews.clear()
    }

    constructor(context: Context) : super(context) {
        initUi(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initUi(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initUi(attrs, defStyleAttr)
    }

    private fun initUi(attrs: AttributeSet?, defStyleAttr: Int) {
        fetchStyleAttributes(attrs, defStyleAttr)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setMainLayout()
        setupFSM()

        setupScroll()
        setupUnscheduledEvents()
        setupTimeLine()

        dragView = addEditView()
        topDragView = addDragView()
        bottomDragView = addDragView()

        scaleDetector = createScaleDetector()

        eventContainer.setOnLongClickListener {
            onAddNewEvent()
            false
        }

        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun onAddNewEvent() {
        val yPosition =
            lastY!! - topLocationOnScreen + scrollView.scrollY - unscheduledEvents.height
        val minuteHeight = fsm.state.minuteHeight
        val timeMapper = PositionToTimeMapper(minuteHeight)
        val eventStartTime = timeMapper.timeRoundedToStart(yPosition, 30)

        positionDragView(
            eventStartTime.toPosition(minuteHeight) - scrollView.scrollY + unscheduledEvents.height,
            fsm.state.hourHeight.toInt()
        )
        dragView?.post {
            fsm.fire(Event.StartCalendarEventAdd(eventStartTime, 60, "", AndroidColor.GREEN))
        }
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        fsm.fire(Event.LayoutChange)
    }

    private fun moveTimeLineToNow() =
        timeLineView.setTopPosition(Time.now().toPosition(fsm.state.minuteHeight) - (timeLineView.height / 2))

    private fun createScaleDetector(): ScaleGestureDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                fsm.fire(Event.ZoomStart(detector.currentSpan))
                return super.onScaleBegin(detector)
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                fsm.fire(Event.Zoom(detector.currentSpan, detector.focusY, detector.scaleFactor))
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector?) {
                fsm.fire(Event.ZoomEnd)
                super.onScaleEnd(detector)
            }
        })

    private fun setupTimeLine() {
        timeLineView = inflater.inflate(timeLineLayout, eventContainer, false)
        timeLineView.post {
            timeLineView.setTopPosition(Time.now().toPosition(fsm.state.minuteHeight) - (timeLineView.height / 2))
        }

        eventContainer.addView(timeLineView)
    }

    private fun hasZoomedEnough(previousDistance: Float, currentDistance: Float): Boolean {
        val distanceDelta = abs(previousDistance - currentDistance)
        return distanceDelta > toPx(24)
    }

    private fun setupFSM() {
        val hourHeight = getScreenHeight() / initialVisibleHours.toFloat()
        fsm = FSM(
            State(State.Type.VIEW, hourHeight = hourHeight, visibleHours = initialVisibleHours),
            this
        )

        listenForZoom()

        fsm.transition(State.Type.VIEW, Event.StartCalendarEventAdd::class) { s, e ->
            scrollView.setOnTouchListener(null)
            setupDragViews(dragView!!)
            setDragViewTouchListener()
            setTopDragViewListener()
            setBottomDragViewListener()

            enableScrollEditMode()

            val absPos = dragView!!.topLocationOnScreen.toFloat() - topLocationOnScreen
            val topPosition = roundPositionToMinutes(absPos)

            listener?.onStartEditNewScheduledEvent(
                e.startTime,
                e.duration
            )

            s.copy(
                type = State.Type.EDIT,
                eventId = "",
                isNewEvent = true,
                topDragViewPosition = topPosition,
                topDragIndicatorPosition = topPosition - dragImageSize / 2,
                bottomDragIndicatorPosition = topPosition + dragView!!.height - dragImageSize / 2,
                height = dragView!!.height,
                isScrollLocked = true,
                isEditing = true
            )
        }

        fsm.transition(State.Type.VIEW, Event.StartCalendarEventEdit::class) { s, e ->
            val topPosition = startDrag(e.view)

            val event = scheduledEventsAdapter!!.events[e.position]

            s.copy(
                type = State.Type.DRAG,
                eventId = event.id,
                topDragViewPosition = topPosition,
                topDragIndicatorPosition = topPosition - dragImageSize / 2,
                bottomDragIndicatorPosition = topPosition + dragView!!.height - dragImageSize / 2,
                eventAdapterPosition = e.position,
                height = dragView!!.height,
                isScrollLocked = true,
                isEditing = true
            )
        }

        fsm.transition(State.Type.EDIT, Event.Scroll::class) { s, e ->
            val absPos = dragView!!.y + e.deltaY

            val maxTopPosition = (bottom - dragView!!.height).toFloat()
            val topPosition = roundPositionToMinutes(min(max(0f, absPos), maxTopPosition))

            val (startTime: Time?, endTime: Time?) = calculateStartAndEndTime(
                topPosition,
                s.height!!,
                s.minuteHeight
            )
            listener?.onMoveEvent(startTime, endTime)
            s
        }

        fsm.transition(State.Type.VIEW, Event.StartUnscheduledEventEdit::class) { s, e ->
            val topPosition = startDrag(e.view)

            val event = unscheduledEventsAdapter!!.events[e.position]
            s.copy(
                type = State.Type.DRAG,
                eventId = event.id,
                topDragViewPosition = topPosition,
                topDragIndicatorPosition = topPosition - dragImageSize / 2,
                bottomDragIndicatorPosition = topPosition + dragView!!.height - dragImageSize / 2,
                unscheduledEventAdapterPosition = e.position,
                height = dragView!!.height,
                isScrollLocked = true,
                isEditing = true
            )
        }

        fsm.transition(State.Type.DRAG, Event.Drag::class) { s, e ->
            val topPosition = calculateTopPosition(e.y)

            val (startTime: Time?, endTime: Time?) = calculateStartAndEndTime(
                topPosition,
                s.height!!,
                s.minuteHeight
            )
            listener?.onMoveEvent(startTime, endTime)

            s.copy(
                topDragViewPosition = topPosition,
                topDragIndicatorPosition = topPosition - dragImageSize / 2,
                bottomDragIndicatorPosition = topPosition + dragView!!.height - dragImageSize / 2
            )
        }

        fsm.transition(State.Type.DRAG, Event.Up::class) { s, _ ->
            s.copy(type = State.Type.EDIT, isScrollLocked = false)
        }

        fsm.transition(State.Type.DRAG, Event.DragTopIndicator::class) { s, e ->
            val topPosition = (ceil(calculateTopPosition(e.y).toDouble())).toFloat()
            val height = (dragView!!.bottom - topPosition).toInt()
            if (!isValidHeightForEvent(height)) {
                return@transition s
            }
            val (startTime: Time?, endTime: Time?) = calculateStartAndEndTime(
                topPosition,
                height,
                s.minuteHeight
            )
            listener?.onResizeEvent(startTime, endTime, getMinutesFor(height))

            s.copy(
                topDragViewPosition = topPosition,
                topDragIndicatorPosition = topPosition - dragImageSize / 2,
                bottomDragIndicatorPosition = topPosition + height - dragImageSize / 2,
                height = height,
                isScrollLocked = false
            )
        }

        fsm.transition(State.Type.DRAG, Event.DragBottomIndicator::class) { s, e ->
            val bottomPosition = calculateBottomPosition(e.y)
            val height = (bottomPosition - dragView!!.top).toInt()
            if (!isValidHeightForEvent(height)) {
                return@transition s
            }

            val topPosition = bottomPosition - height
            val (startTime: Time?, endTime: Time?) = calculateStartAndEndTime(
                topPosition,
                height,
                s.minuteHeight
            )
            listener?.onResizeEvent(startTime, endTime, getMinutesFor(height))

            s.copy(
                bottomDragIndicatorPosition = bottomPosition - dragImageSize / 2,
                height = height,
                isScrollLocked = false
            )
        }

        fsm.transition(State.Type.EDIT, Event.Drag::class) { s, _ ->
            s.copy(type = State.Type.DRAG, isScrollLocked = false)
        }

        fsm.transition(State.Type.EDIT, Event.DragTopIndicator::class) { s, _ ->
            s.copy(type = State.Type.DRAG, isScrollLocked = false)
        }

        fsm.transition(State.Type.EDIT, Event.DragBottomIndicator::class) { s, _ ->
            s.copy(type = State.Type.DRAG, isScrollLocked = false)
        }

        fsm.transition(State.Type.EDIT, Event.Up::class) { s, _ ->
            listener?.onDragViewClick(dragView!!)
            s
        }

        fsm.transition(State.Type.EDIT, Event.LayoutChange::class) { s, _ ->
            getWindowVisibleDisplayFrame(r)
            val visibleHeight = r.height()
            val heightDiff = rootView.height - r.height()
            val isOpen = heightDiff > keyboardVisibleThreshold

            if (isOpen && !s.isKeyboardOpen && s.type == State.Type.EDIT) {
                dragView?.let {
                    val dragBottom = it.topLocationOnScreen + it.height
                    if (dragBottom > visibleHeight) {

                        val topPosition =
                            visibleHeight.toFloat() - it.height / 2 - topLocationOnScreen
                        val scrollYDelta = s.topDragViewPosition!! - topPosition
                        scrollView.scrollBy(0, scrollYDelta.toInt())
                        return@transition s.copy(
                            isKeyboardOpen = isOpen,
                            topDragViewPosition = topPosition,
                            topDragIndicatorPosition = topPosition - dragImageSize / 2,
                            bottomDragIndicatorPosition = topPosition + it.height - dragImageSize / 2
                        )
                    }

                }
            }

            s.copy(isKeyboardOpen = isOpen)
        }

        fsm.transition(State.Type.EDIT, Event.CompleteEditRequest::class) { s, _ ->
            if (s.isNewEvent) {
                listener?.onAddEvent()
            } else if (s.eventAdapterPosition != null) {
                if (shouldUnscheduleScheduledEvent(s)) {
                    listener?.onEditUnscheduledEvent()
                } else {
                    listener?.onEditCalendarEvent()
                }
            } else if (s.unscheduledEventAdapterPosition != null) {
                if (shouldScheduleUnscheduledEvent(s)) {
                    listener?.onEditUnscheduledCalendarEvent()
                } else {
                    listener?.onEditUnscheduledEvent()
                }
            }
            s
        }

        fsm.transition(State.Type.EDIT, Event.CompleteEdit::class) { s, _ ->
            prepareForViewState()
            s.copy(
                type = State.Type.VIEW,
                isScrollLocked = false,
                isEditing = false,
                isNewEvent = false,
                eventAdapterPosition = null,
                unscheduledEventAdapterPosition = null
            )
        }

        fsm.transition(State.Type.EDIT, Event.CancelEdit::class) { s, _ ->
            prepareForViewState()
            s.copy(
                type = State.Type.VIEW,
                isScrollLocked = false,
                isEditing = false,
                isNewEvent = false,
                eventAdapterPosition = null,
                unscheduledEventAdapterPosition = null
            )
        }

        fsm.transition(State.Type.EDIT, Event.RemoveEvent::class) { s, _ ->
            prepareForViewState()
            listener?.onRemoveEvent(s.eventId)
            s.copy(
                type = State.Type.VIEW,
                isScrollLocked = false,
                isEditing = false,
                isNewEvent = false,
                eventAdapterPosition = null,
                unscheduledEventAdapterPosition = null
            )
        }

        fsm.transition(State.Type.DRAG, Event.StartEditName::class) { s, _ ->
            s.copy(type = State.Type.EDIT, isScrollLocked = false)
        }

        fsm.transition(State.Type.VIEW, Event.ZoomStart::class) { s, e ->
            s.copy(type = State.Type.ZOOM, zoomDistance = e.zoomDistance)
        }

        fsm.transition(State.Type.ZOOM, Event.Zoom::class) { s, e ->
            if (!hasZoomedEnough(s.zoomDistance!!, e.zoomDistance)) {
                return@transition s
            }
            val newState = createNewZoomState(s, e)
            resizeCalendar(newState.hourHeight, newState.minuteHeight)
            scrollToFocusPosition(s.minuteHeight, newState.minuteHeight, e.focusY)

            newState
        }

        fsm.transition(State.Type.ZOOM, Event.ZoomEnd::class) { s, _ ->
            s.copy(type = State.Type.VIEW)
        }

    }

    private fun prepareForViewState() {
        dragView?.visible = false
        scrollView.scrollChangedListener = {}
        scrollView.singleTapListener = {}
        listenForZoom()
        hideViews(topDragView, bottomDragView)
    }

    private fun shouldScheduleUnscheduledEvent(s: State) =
        !isInUnscheduledEventsArea(dragView!!.topLocationOnScreen.toFloat()) && s.unscheduledEventAdapterPosition != null

    private fun shouldUnscheduleScheduledEvent(s: State) =
        isInUnscheduledEventsArea(dragView!!.topLocationOnScreen.toFloat()) && s.eventAdapterPosition != null

    private fun calculateStartAndEndTime(
        topPosition: Float,
        height: Int,
        minuteHeight: Float
    ): Pair<Time?, Time?> {
        if (isInUnscheduledEventsArea(dragView!!.topLocationOnScreen.toFloat())) {
            return Pair(null, null)
        }
        val timeMapper = PositionToTimeMapper(minuteHeight)
        val topRelativePos = topPosition - unscheduledEvents.height + scrollView.scrollY

        return Pair(
            timeMapper.timeAt(topRelativePos),
            timeMapper.timeAt(topRelativePos + height)
        )
    }

    private fun isInUnscheduledEventsArea(topPosition: Float) =
        topPosition < scrollView.topLocationOnScreen

    private fun startDrag(adapterView: View): Float {
        scrollView.setOnTouchListener(null)
        setupDragViews(dragView!!)
        setAdapterViewTouchListener(adapterView)
        enableScrollEditMode()
        val absPos = dragView!!.topLocationOnScreen.toFloat() - topLocationOnScreen
        return roundPositionToMinutes(absPos)
    }

    private fun enableScrollEditMode() {
        scrollView.scrollChangedListener = { delta ->
            fsm.fire(Event.Scroll(delta))
        }
        scrollView.singleTapListener = {
            fsm.fire(Event.CompleteEditRequest)
        }
    }

    private fun calculateBottomPosition(y: Float): Float {
        val absPos = y - topLocationOnScreen
        return roundPositionToMinutes(min(absPos, bottom.toFloat()))
    }

    private fun calculateTopPosition(y: Float): Float {
        val absPos = y - topLocationOnScreen
        val maxTopPosition = (bottom - dragView!!.height).toFloat()
        return roundPositionToMinutes(min(max(0f, absPos), maxTopPosition))
    }

    private fun listenForZoom() {
        scrollView.setOnTouchListener(zoomListener)
    }

    private fun createNewZoomState(state: State, event: Event.Zoom): State {
        val visibleHours = calculateNewVisibleHours(state.visibleHours!!, event.scaleFactor)
        return state.copy(
            visibleHours = visibleHours,
            zoomDistance = event.zoomDistance,
            hourHeight = getScreenHeight() / visibleHours.toFloat()
        )
    }

    private fun scrollToFocusPosition(prevMinuteHeight: Float, minuteHeight: Float, focusY: Float) {
        val relativeFocusY = focusY + scrollView.scrollY
        val focusTime = PositionToTimeMapper(prevMinuteHeight).timeAt(relativeFocusY, 1)
        val scrollDelta = focusTime.toPosition(minuteHeight) - relativeFocusY
        scrollView.scrollBy(0, scrollDelta.toInt())
    }

    private fun resizeCalendar(hourHeight: Float, minuteHeight: Float) {
        startResizeAnimation()
        resizeHourCells(hourHeight)
        resizeCalendarEvents(minuteHeight)
        resizeTimeLine(minuteHeight)
    }

    private fun resizeTimeLine(minuteHeight: Float) {
        timeLineView.post {
            val timeLineHeight = timeLineView.height
            timeLineView.setTopPosition(Time.now().toPosition(minuteHeight) - timeLineHeight / 2)
        }
    }

    private fun resizeCalendarEvents(minuteHeight: Float) {
        val a = scheduledEventsAdapter!!
        eventViews.forEachIndexed { i, adapterView ->
            val event = a.getItem(i)
            adapterView.setPositionAndHeight(
                event.startMinute * minuteHeight,
                (event.duration * minuteHeight).toInt()
            )
            adapterView.post {
                listener?.onZoomEvent(adapterView)
            }
        }
    }

    private fun resizeHourCells(hourHeight: Float) {
        eventContainer.hourHeight = hourHeight
        eventContainer.invalidate()
    }

    private fun startResizeAnimation() {
        val transition = AutoTransition()
        transition.duration = 0
        TransitionManager.beginDelayedTransition(this, transition)
    }

    private fun calculateNewVisibleHours(visibleHours: Int, scaleFactor: Float): Int {
        val vh = if (scaleFactor > 1) visibleHours - 1 else visibleHours + 1
        return min(max(vh, MIN_VISIBLE_HOURS), MAX_VISIBLE_HOURS)
    }

    private fun setAdapterViewTouchListener(adapterView: View) {
        val initialOffset = lastY!! - dragView!!.topLocationOnScreen

        adapterView.setOnTouchListener { _, e ->

            val action = e.actionMasked

            if (action == MotionEvent.ACTION_MOVE) {
                fsm.fire(Event.Drag(e.rawY - initialOffset))
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fsm.fire(Event.Up)
                setDragViewTouchListener()
                setTopDragViewListener()
                setBottomDragViewListener()
                adapterView.setOnTouchListener(null)
            }
            true
        }
    }

    private fun setupScroll() {
        scrollView.isVerticalScrollBarEnabled = false
    }

    private fun setMainLayout() {
        inflater.inflate(R.layout.view_calendar_day, this, true)
    }

    private fun fetchStyleAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.CalendarDayView,
                defStyleAttr,
                0
            )
            dragImage = a.getDrawable(R.styleable.CalendarDayView_dragImage)
            dragImageSize =
                a.getDimensionPixelSize(R.styleable.CalendarDayView_dragImageSize, dragImageSize)
            dragImagePadding = a.getDimensionPixelSize(
                R.styleable.CalendarDayView_dragImagePadding,
                dragImagePadding
            )
            initialVisibleHours =
                a.getInt(R.styleable.CalendarDayView_visibleHours, DEFAULT_VISIBLE_HOURS)
            timeLineLayout = a.getResourceId(
                R.styleable.CalendarDayView_timeLineLayout,
                R.layout.calendar_time_line
            )
            a.recycle()
        }
    }

    private fun addDragView(): View {
        val view = ImageView(context)
        val lp = LayoutParams(dragImageSize, dragImageSize)
        view.layoutParams = lp
        view.setPadding(dragImagePadding, dragImagePadding, dragImagePadding, dragImagePadding)
        view.setImageDrawable(dragImage)
        view.visibility = View.GONE
        addView(view)
        return view
    }

    private fun setupUnscheduledEvents() {
        unscheduledEvents.layoutManager =
            object : LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                override fun canScrollVertically() = !fsm.state.isScrollLocked
            }
    }

    fun setCalendarChangeListener(listener: CalendarChangeListener) {
        this.listener = listener
    }

    fun setUnscheduledEventsAdapter(adapter: UnscheduledEventsAdapter<*>) {
        unscheduledEventsAdapter = adapter
        unscheduledEvents.adapter = adapter
        unscheduledEventsDivider.visible = adapter.itemCount != 0
    }

    fun setScheduledEventsAdapter(adapter: ScheduledEventsAdapter<*>) {
        this.scheduledEventsAdapter?.unregisterDataSetObserver(dataSetObserver)
        this.scheduledEventsAdapter = adapter
        this.scheduledEventsAdapter?.registerDataSetObserver(dataSetObserver)
        clearAndAddEventsFromAdapter()
    }

    private fun clearAndAddEventsFromAdapter() {
        clearCalendarEvents()
        addEventsFromAdapter()
    }

    fun startEditDragEventName() =
        fsm.fire(Event.StartEditName)

    fun onEventUpdated() =
        fsm.fire(Event.CompleteEdit)

    fun onEventValidationError() =
        listener?.onEventValidationError(dragView!!)

    fun cancelEdit() {
        fsm.fire(Event.CancelEdit)
    }

    fun onRemoveEvent() {
        fsm.fire(Event.RemoveEvent)
    }

    private fun addEventsFromAdapter() {
        val a = scheduledEventsAdapter!!
        val minuteHeight = fsm.state.minuteHeight
        for (i in 0 until a.count) {
            val adapterView = a.getView(i, null, eventContainer)
            val event = a.getItem(i)
            adapterView.setPositionAndHeight(
                event.startMinute * minuteHeight,
                (event.duration * minuteHeight).toInt()
            )
            eventViews.add(adapterView)
            eventContainer.addView(adapterView)
        }
    }

    private fun positionDragView(adapterView: View, duration: Int) {
        val yPosition = adapterView.topLocationOnScreen - topLocationOnScreen.toFloat()
        return positionDragView(yPosition, (fsm.state.minuteHeight * duration).toInt())
    }

    private fun positionDragView(yPosition: Float, height: Int) {
        dragView?.setPositionAndHeight(
            yPosition,
            height
        )
    }

    private fun addEditView(): View {
        val v = inflater.inflate(R.layout.item_calendar_drag, this, false)
        v.visible = false
        addView(v)
        return v
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastY = ev.rawY
        return super.dispatchTouchEvent(ev)
    }

    private fun setupDragViews(dragView: View) {
        setupTopDragView(dragView)
        setupBottomDragView(dragView)
    }

    private fun setupBottomDragView(editView: View) {
        bottomDragView.elevation = editView.elevation
        bottomDragView.bringToFront()
        positionBottomDragView(editView)
        setBottomDragViewListener()
    }

    private fun setupTopDragView(editView: View) {
        topDragView.elevation = editView.elevation
        topDragView.bringToFront()
        positionTopDragView(editView)
        setTopDragViewListener()
    }

    private fun positionBottomDragView(editView: View) {
        val lp = bottomDragView.layoutParams as MarginLayoutParams
        lp.topMargin = editView.bottom - dragImageSize / 2
        lp.marginStart = editView.left + editView.width / 2 - dragImageSize / 2
        bottomDragView.layoutParams = lp
    }

    private fun positionTopDragView(editView: View) {
        val lp = topDragView.layoutParams as MarginLayoutParams
        lp.topMargin = editView.top - dragImageSize / 2
        lp.marginStart = editView.left + editView.width / 2 - dragImageSize / 2
        topDragView.layoutParams = lp
    }

    private fun setDragViewTouchListener() {
        var initialOffset = lastY!! - dragView!!.topLocationOnScreen
        dragView!!.setOnTouchListener { _, e ->
            val action = e.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                initialOffset = e.rawY - dragView!!.topLocationOnScreen
            }
            if (action == MotionEvent.ACTION_MOVE) {
                fsm.fire(Event.Drag(e.rawY - initialOffset))
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fsm.fire(Event.Up)
            }
            true
        }
    }

    private fun setBottomDragViewListener() {
        bottomDragView.setOnTouchListener { _, e ->
            val action = e.actionMasked
            if (action == MotionEvent.ACTION_MOVE) {
                fsm.fire(Event.DragBottomIndicator(e.rawY))
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fsm.fire(Event.Up)
            }
            true
        }
    }

    private fun setTopDragViewListener() {
        topDragView.setOnTouchListener { _, e ->
            val action = e.actionMasked
            if (action == MotionEvent.ACTION_MOVE) {
                fsm.fire(Event.DragTopIndicator(e.rawY))
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                fsm.fire(Event.Up)
            }
            true
        }
    }

    override fun onStateChanged(state: State) {
        scrollView.isLocked = state.isScrollLocked

        when (state.type) {

            State.Type.EDIT -> {
                showViews(dragView!!, topDragView, bottomDragView)

                dragView?.setPositionAndHeight(state.topDragViewPosition!!, state.height!!)
                topDragView.setTopPosition(state.topDragIndicatorPosition!!)
                bottomDragView.setTopPosition(state.bottomDragIndicatorPosition!!)
            }

            State.Type.DRAG -> {
                showViews(dragView!!, topDragView, bottomDragView)
                dragView?.setPositionAndHeight(state.topDragViewPosition!!, state.height!!)
                topDragView.setTopPosition(state.topDragIndicatorPosition!!)
                bottomDragView.setTopPosition(state.bottomDragIndicatorPosition!!)
            }

            else -> {
            }
        }
    }

    private fun roundPositionToMinutes(position: Float, roundedToMinutes: Int = 5) =
        PositionToTimeMapper(fsm.state.minuteHeight).timeAt(position, roundedToMinutes).toPosition(
            fsm.state.minuteHeight
        )

    private fun View.setPositionAndHeight(yPosition: Float, height: Int) =
        changeLayoutParams<MarginLayoutParams> {
            it.topMargin = yPosition.toInt()
            it.height = height
        }

    private fun View.setTopPosition(yPosition: Float) =
        changeLayoutParams<MarginLayoutParams> { it.topMargin = yPosition.toInt() }

    private fun View.addToTopPosition(delta: Float) =
        changeLayoutParams<MarginLayoutParams> { it.topMargin += delta.toInt() }

    private fun <T : ViewGroup.LayoutParams> View.changeLayoutParams(cb: (layoutParams: T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val lp = layoutParams as T
        cb(lp)
        layoutParams = lp
    }

    private val View.topLocationOnScreen: Int
        get() {
            val location = IntArray(2)
            getLocationOnScreen(location)
            return location[1]
        }

    private fun Time.toPosition(minuteHeight: Float) =
        toMinuteOfDay() * minuteHeight

    private fun isValidHeightForEvent(height: Int): Boolean =
        getMinutesFor(height) in MIN_EVENT_DURATION..MAX_EVENT_DURATION

    private fun getScreenHeight(): Int {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    private fun getMinutesFor(height: Int): Int {
        val minutes = height / fsm.state.minuteHeight
        return roundMinutes(minutes)
    }

    private fun showViews(vararg views: View) =
        views.forEach { it.visibility = View.VISIBLE }

    private fun hideViews(vararg views: View) =
        views.forEach { it.visibility = View.GONE }

    private fun toPx(dp: Int): Int =
        (dp * Resources.getSystem().displayMetrics.density).toInt()

    fun startEventRescheduling(calendarEvent: CalendarEvent) {
        val position = scheduledEventsAdapter!!.events.indexOf(calendarEvent)
        val adapterView = eventViews[position]
        positionDragView(adapterView, calendarEvent.duration)
        dragView?.post {
            fsm.fire(Event.StartCalendarEventEdit(adapterView, position))
        }
    }

    fun startEventRescheduling(unscheduledEvent: UnscheduledEvent) {
        val position = unscheduledEventsAdapter!!.events.indexOf(unscheduledEvent)
        val adapterView = unscheduledEvents.layoutManager.findViewByPosition(position)
        positionDragView(adapterView, unscheduledEvent.duration)
        dragView?.post {
            fsm.fire(Event.StartUnscheduledEventEdit(adapterView, position))
        }
    }

    fun scrollToNow() {
        scrollTo(Time.now())
    }

    fun scrollTo(time: Time) {
        scrollView.post {
            scrollView.scrollY = scrollPositionFrom(time)
        }
    }

    fun smoothScrollTo(time: Time) {
        val animationDuration = resources.getInteger(android.R.integer.config_mediumAnimTime)
        ObjectAnimator.ofInt(scrollView, "scrollY", scrollPositionFrom(time))
            .setDuration(animationDuration.toLong())
            .start()
    }

    private fun scrollPositionFrom(time: Time): Int {
        val hourOffset = Math.floor((fsm.state.visibleHours!! * .3)).toInt()
        val scrollHours = max(hourOffset, time.hours - hourOffset)
        return Time.at(scrollHours, time.getMinutes()).toPosition(fsm.state.minuteHeight).toInt()
    }

    fun hideTimeline() {
        timeLineView.visible = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        moveTimeLineToNow()
        val hourHeight = fsm.state.hourHeight
        eventContainer.hourHeight = hourHeight
        eventContainer.invalidate()
        context.registerReceiver(timeChangeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onDetachedFromWindow() {
        context.unregisterReceiver(timeChangeReceiver)
        eventViews.clear()
        dragView = null
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onDetachedFromWindow()
    }

    private fun roundMinutes(minutes: Float, roundToMinutes: Int = 5): Int {
        val remainder = minutes % roundToMinutes.toFloat()
        return if (remainder >= cutoff(roundToMinutes)) {
            minutes + roundToMinutes - remainder
        } else {
            minutes - remainder
        }.toInt()
    }

    private fun cutoff(roundedToMinutes: Int): Float =
        Math.floor(roundedToMinutes.toDouble() / 2).toFloat()

    interface CalendarChangeListener {
        fun onStartEditNewScheduledEvent(startTime: Time, duration: Int)
        fun onDragViewClick(dragView: View)
        fun onEventValidationError(dragView: View)
        fun onMoveEvent(startTime: Time?, endTime: Time?)
        fun onResizeEvent(startTime: Time?, endTime: Time?, duration: Int)
        fun onZoomEvent(adapterView: View)
        fun onAddEvent()
        fun onEditCalendarEvent()
        fun onEditUnscheduledCalendarEvent()
        fun onEditUnscheduledEvent()
        fun onRemoveEvent(eventId: String)
    }
}

interface StateChangeListener {
    fun onStateChanged(state: CalendarDayView.State)
}