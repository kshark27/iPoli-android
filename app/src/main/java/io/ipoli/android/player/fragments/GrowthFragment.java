package io.ipoli.android.player.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.squareup.otto.Bus;

import java.util.ArrayList;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.ipoli.android.R;
import io.ipoli.android.app.App;
import io.ipoli.android.app.BaseFragment;
import io.ipoli.android.app.help.HelpDialog;
import io.ipoli.android.quest.QuestContext;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 6/4/16.
 */
public class GrowthFragment extends BaseFragment {

    @Inject
    Bus eventBus;

    @BindView(R.id.time_spent_chart)
    PieChart timeSpentChart;

    @BindView(R.id.experience_chart)
    BarChart experienceChart;

    private Unbinder unbinder;

    protected String[] mMonths = new String[]{
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dec"
    };

    protected String[] mParties = new String[]{
            "Party A", "Party B", "Party C", "Party D", "Party E", "Party F", "Party G", "Party H",
            "Party I", "Party J", "Party K", "Party L", "Party M", "Party N", "Party O", "Party P",
            "Party Q", "Party R", "Party S", "Party T", "Party U", "Party V", "Party W", "Party X",
            "Party Y", "Party Z"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_growth, container, false);
        unbinder = ButterKnife.bind(this, view);
        App.getAppComponent(getContext()).inject(this);
        setUpTimeSpentChart();

//        LineData data = getData(7, 5);
////        data.setValueTextColor(getColor(R.color.md_blue_500));
//
//        // add some transparency to the color with "& 0x90FFFFFF"
//        setupChart(experienceChart, data);

        experienceChart.setDrawBarShadow(false);
        experienceChart.setDrawValueAboveBar(true);

        experienceChart.setDescription("");

        // if more than 60 entries are displayed in the chart, no values will be
        // drawn
        experienceChart.setMaxVisibleValueCount(60);

        // scaling can now only be done on x- and y-axis separately
        experienceChart.setPinchZoom(false);

        experienceChart.setDrawGridBackground(false);
        experienceChart.setExtraOffsets(5, 10, 5, 5);
        // experienceChart.setDrawYLabels(false);

        XAxis xAxis = experienceChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getColor(R.color.md_dark_text_54));
        xAxis.setTextSize(10f);
        xAxis.setSpaceBetweenLabels(2);

        YAxis leftAxis = experienceChart.getAxisLeft();
        leftAxis.setLabelCount(8, false);
        leftAxis.setTextColor(getColor(R.color.md_dark_text_54));
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setSpaceTop(15f);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMinValue(0f); // this replaces setStartAtZero(true)

        YAxis rightAxis = experienceChart.getAxisRight();
        rightAxis.setDrawGridLines(false);
        rightAxis.setLabelCount(8, false);
        rightAxis.setTextColor(getColor(R.color.md_dark_text_54));
        rightAxis.setSpaceTop(15f);
        rightAxis.setTextSize(10f);
        rightAxis.setAxisMinValue(0f); // this replaces setStartAtZero(true)

        Legend l = experienceChart.getLegend();
//        l.setEnabled(false);
        l.setPosition(Legend.LegendPosition.BELOW_CHART_LEFT);
        l.setForm(Legend.LegendForm.SQUARE);
        l.setFormSize(9f);
        l.setTextSize(11f);
        l.setXEntrySpace(4f);
        // l.setExtra(ColorTemplate.VORDIPLOM_COLORS, new String[] { "abc",
        // "def", "ghj", "ikl", "mno" });
        // l.setCustom(ColorTemplate.VORDIPLOM_COLORS, new String[] { "abc",
        // "def", "ghj", "ikl", "mno" });

        setDataBar(7, 200);


        return view;
    }

//    private void setupChart(LineChart chart, LineData data) {
//
////        ((LineDataSet) data.getDataSetByIndex(0)).setCircleColorHole(color);
//
//        // no description text
//        chart.setDescription("");
//        chart.setNoDataTextDescription("You need to provide data for the chart.");
//
//        // experienceChart.setDrawHorizontalGrid(false);
//        //
//        // enable / disable grid background
////        chart.setDrawGridBackground(false);
////        chart.getRenderer().getGridPaint().setGridColor(Color.WHITE & 0x70FFFFFF);
//
//        // enable touch gestures
//        chart.setTouchEnabled(true);
//
//        // enable scaling and dragging
//        chart.setDragEnabled(true);
//        chart.setScaleEnabled(true);
//
//        // if disabled, scaling can be done on x- and y-axis separately
//        chart.setPinchZoom(false);
//
////        chart.setBackgroundColor(color);
//
//        // set custom chart offsets (automatic offset calculation is hereby disabled)
//        chart.setExtraOffsets(5, 10, 5, 5);
//
//        // add data
//        chart.setData(data);
//
//        // get the legend (only possible after setting data)
//        Legend l = chart.getLegend();
////        l.setEnabled(false);
////        chart.setDrawGridBackground(false);
////
//        chart.getAxisLeft().setEnabled(false);
//        chart.getXAxis().setDrawGridLines(false);
//        chart.getXAxis().setPosition(XAxis.XAxisPosition.TOP);
//        chart.getXAxis().setAxisLineColor(getColor(R.color.md_dark_text_54));
//        chart.getXAxis().setTextColor(getColor(R.color.md_dark_text_54));
//        chart.getXAxis().setDrawAxisLine(false);
//        chart.setDrawBorders(true);
//        chart.setBorderWidth(0.5f);
//        chart.setBorderColor(getColor(R.color.md_dark_text_26));
////        chart.getAxisLeft().setSpaceTop(40);
////        chart.getAxisLeft().setSpaceBottom(40);
//        chart.getAxisRight().setDrawGridLines(false);
//        chart.getAxisRight().setDrawAxisLine(false);
//        chart.getAxisRight().setAxisLineColor(getColor(R.color.md_dark_text_54));
//        chart.getAxisRight().setTextColor(getColor(R.color.md_dark_text_54));
////        chart.getAxisRight().setDrawLabels(false);
//
////        chart.getXAxis().setEnabled(false);
//
//        // animate calls invalidate()...
//        chart.animateX(getResources().getInteger(android.R.integer.config_longAnimTime));
//    }

    private void setDataBar(int count, float range) {

        ArrayList<String> xVals = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            xVals.add(String.valueOf(i + 1));
        }

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        for (int i = 0; i < count; i++) {
            float mult = (range + 1);
            int val = (int) (Math.random() * mult);
            yVals1.add(new BarEntry(val, i));
        }

        BarDataSet set1;

//        if (experienceChart.getData() != null &&
//                experienceChart.getData().getDataSetCount() > 0) {
//            set1 = (BarDataSet)experienceChart.getData().getDataSetByIndex(0);
//            set1.setYVals(yVals1);
//            experienceChart.getData().setXVals(xVals);
//            experienceChart.getData().notifyDataChanged();
//            experienceChart.notifyDataSetChanged();
//        } else {
        set1 = new BarDataSet(yVals1, "Experience gain per day");
        set1.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                return String.valueOf(Math.round(value));
            }
        });
        set1.setBarSpacePercent(35f);
        set1.setColors(new int[]{getColor(R.color.md_blue_300)});

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(12f);
        data.setValueTextColor(getColor(R.color.md_dark_text_87));

        experienceChart.setData(data);
        experienceChart.animateY(getResources().getInteger(android.R.integer.config_longAnimTime));
    }

//    private LineData getData(int count, float range) {
//
//        ArrayList<String> xVals = new ArrayList<String>();
//        for (int i = 0; i < count; i++) {
//            xVals.add(String.valueOf(i + 1));
//        }
//
//        ArrayList<Entry> yVals = new ArrayList<Entry>();
//
//        float val = 0;
//        for (int i = 0; i < count; i++) {
//            val += (float) (Math.random() * range * 10);
//            yVals.add(new Entry(val, i));
//        }
//
//        // create a dataset and give it a type
//        LineDataSet set1 = new LineDataSet(yVals, "Experience");
//
//        set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
////        set1.setLineWidth(1.5f);
////        set1.setDrawCircles(false);
////        set1.setCircleRadius(2f);
////        set1.setCircleHoleRadius(0.5f);
//        set1.setCircleRadius(2f);
//        set1.setDrawCircleHole(false);
//        set1.setColor(getColor(R.color.md_blue_300));
//        set1.setCircleColor(getColor(R.color.md_blue_500));
//        set1.setHighLightColor(Color.BLACK);
//        set1.setFillColor(getColor(R.color.md_blue_100));
//        set1.setDrawFilled(true);
//        set1.setDrawValues(false);
//
//        ArrayList<Entry> yVals2 = new ArrayList<Entry>();
//
//        val = 0;
//        for (int i = 0; i < count; i++) {
//            val += (float) (Math.random() * range * 4);
//            yVals2.add(new Entry(val, i));
//        }
//
//        // create a dataset and give it a type
//        LineDataSet set2 = new LineDataSet(yVals2, "Coins");
//
////        set2.setLineWidth(3f);
//        set2.setCircleRadius(2f);
//        set2.setDrawCircleHole(false);
//        set2.setMode(LineDataSet.Mode.CUBIC_BEZIER);
//        set2.setColor(getColor(R.color.md_red_300));
//        set2.setCircleColor(getColor(R.color.md_red_500));
//        set2.setHighLightColor(Color.BLACK);
//        set2.setDrawFilled(true);
////        set2.setDrawCircles(false);
//        set2.setFillColor(getColor(R.color.md_red_100));
//        set2.setDrawValues(false);
//
//        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
//        dataSets.add(set1); // add the datasets
//        dataSets.add(set2); // add the datasets
//
//        // create a data object with the datasets
//
//        return new LineData(xVals, dataSets);
//    }

    private void setUpTimeSpentChart() {
        //        timeSpentChart.setUsePercentValues(true);
        timeSpentChart.setExtraOffsets(5, 0, 5, 5);

        timeSpentChart.setDragDecelerationFrictionCoef(0.95f);

        timeSpentChart.setDrawHoleEnabled(true);
        timeSpentChart.setDescription("");
        timeSpentChart.setHoleColor(Color.WHITE);

        timeSpentChart.setTransparentCircleColor(Color.WHITE);
        timeSpentChart.setTransparentCircleAlpha(110);

        timeSpentChart.setHoleRadius(44f);
        timeSpentChart.setTransparentCircleRadius(47f);

        timeSpentChart.setDrawCenterText(true);

        String centerText = "XP: 2565\nCoins: 450";
        timeSpentChart.setCenterText(centerText);
        timeSpentChart.setCenterTextColor(getColor(R.color.md_dark_text_87));
        timeSpentChart.setCenterTextSize(12f);
//        centerTextLayout = new StaticLayout(centerText, 0, centerText.length(),
//                mCenterTextPaint,
//                (int)Math.max(Math.ceil(mCenterTextLastBounds.width()), 1.f),
//                Layout.Alignment.ALIGN_NORMAL, 1.f, 0.f, false);


        timeSpentChart.setRotationAngle(0);
        // enable rotation of the chart by touch
        timeSpentChart.setRotationEnabled(true);
        timeSpentChart.setHighlightPerTapEnabled(true);
        timeSpentChart.getLegend().setEnabled(false);
        // add a selection listener
        setData(5, 30);

        timeSpentChart.animateY(getResources().getInteger(android.R.integer.config_longAnimTime), Easing.EasingOption.EaseInOutQuad);
//        timeSpentChart.spin(getResources().getInteger(android.R.integer.config_mediumAnimTime), 0, 360, Easing.EasingOption.EaseInQuad);

//        Legend l = timeSpentChart.getLegend();
//        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART);
//        l.setXEntrySpace(7f);
//        l.setYEntrySpace(0f);
//        l.setYOffset(0f);
    }

    private void setData(int count, float range) {

        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        for (int i = 0; i < count + 1; i++) {
            yVals1.add(new Entry((float) (Math.random() * range) + range / 5, i));
        }

        ArrayList<String> xVals = new ArrayList<String>();

//        for (int i = 0; i < count + 1; i++)
//            xVals.add(mParties[i % mParties.length]);

        xVals.add("Learning");
        xVals.add("Wellness");
        xVals.add("Personal");
        xVals.add("Work");
        xVals.add("Fun");
        xVals.add("Chores");

        PieDataSet dataSet = new PieDataSet(yVals1, "");
        dataSet.setSliceSpace(3f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                return Math.round(value) + " h";
            }
        });

        dataSet.setSelectionShift(5f);

        dataSet.setColors(new int[]{getColor(QuestContext.LEARNING.resLightColor),
                getColor(QuestContext.WELLNESS.resLightColor),
                getColor(QuestContext.PERSONAL.resLightColor),
                getColor(QuestContext.WORK.resLightColor),
                getColor(QuestContext.FUN.resLightColor),
                getColor(QuestContext.CHORES.resLightColor)});
        //dataSet.setSelectionShift(0f);

        PieData data = new PieData(xVals, dataSet);
//        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(12f);
        data.setValueTextColor(getColor(R.color.md_white));
        timeSpentChart.setData(data);

        // undo all highlights
        timeSpentChart.highlightValues(null);

        timeSpentChart.invalidate();
    }

    private int getColor(@ColorRes int color) {
        return ContextCompat.getColor(getActivity(), color);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }


    @Override
    public void onResume() {
        super.onResume();
        eventBus.register(this);
    }

    @Override
    public void onPause() {
        eventBus.unregister(this);
        super.onPause();
    }

    @Override
    protected boolean useOptionsMenu() {
        return true;
    }

    @Override
    protected void showHelpDialog() {
        HelpDialog.newInstance(R.layout.fragment_help_dialog_growth, R.string.help_dialog_growth_title, "growth").show(getActivity().getSupportFragmentManager());
    }
}
