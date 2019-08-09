package com.affectiva.camera;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Settings activity.
 */
@SuppressWarnings("PMD.TooManyMethods")
public class SettingsActivity extends AppCompatActivity implements
    SeekBar.OnSeekBarChangeListener,
    RadioGroup.OnCheckedChangeListener {

  private TextView processingFrameRateValue;

  private SeekBar processingFrameRateSeekBar;

  private RadioGroup metricsRadioGroup;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);

    initializeUi();
  }

  /**
   * Initialize UI.
   */
  private void initializeUi() {
    initializeProcessingFrameRateSeekBar();

    processingFrameRateValue = findViewById(R.id.processing_frame_rate_value);
    processingFrameRateValue.setText(String.valueOf(processingFrameRateSeekBar.getProgress()));

    metricsRadioGroup = findViewById(R.id.metrics_radioGroup);
    metricsRadioGroup.setOnCheckedChangeListener(this);

    checkSelectedMetrics();
  }

  /**
   * Initialize max number of processing frames displaying seekBar.
   */
  private void initializeProcessingFrameRateSeekBar() {
    processingFrameRateSeekBar = findViewById(R.id.processing_frame_rate_seekBar);

    processingFrameRateSeekBar.setProgress(
        Preferences.getInstance().getIntValue(
            Preferences.PROCESSING_FRAME_RATE,
            20
        )
    );

    processingFrameRateSeekBar.setOnSeekBarChangeListener(this);
  }

  /**
   * Check selected previously radioButton.
   */
  private void checkSelectedMetrics() {
    final String selectedRadioButton = Preferences.getInstance().getStringValue(
        Preferences.SELECTED_FRAGMENT,
        ViewMetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT.toString()
    );

    final ViewMetricsFragmentFactory.FragmentType fragmentType =
        ViewMetricsFragmentFactory.FragmentType.valueOf(selectedRadioButton);

    metricsRadioGroup.check(ViewMetricsFragmentFactory.getFragmentButtonId(fragmentType));
  }

  /**
   * On back pressed event. Returns to the previous screen.
   *
   * @param view clicked view.
   */
  public void onBackPressed(final View view) {
    final Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        | Intent.FLAG_ACTIVITY_NEW_TASK
    );
    startActivity(intent);
    finish();
  }

  @Override
  public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
    processingFrameRateValue.setText(String.valueOf(processingFrameRateSeekBar.getProgress()));

    Preferences.getInstance().setIntValue(
        Preferences.PROCESSING_FRAME_RATE,
        processingFrameRateSeekBar.getProgress()
    );
  }

  @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
  @Override
  public void onStartTrackingTouch(final SeekBar seekBar) {
  }

  @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
  @Override
  public void onStopTrackingTouch(final SeekBar seekBar) {
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  @Override
  public void onCheckedChanged(final RadioGroup group, final int checkedId) {
    ViewMetricsFragmentFactory.FragmentType fragmentType =
        ViewMetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT;

    switch (checkedId) {
      case R.id.measurements_button:
        fragmentType = ViewMetricsFragmentFactory.FragmentType.MEASUREMENTS_FRAGMENT;
        break;
      case R.id.expressions_button:
        fragmentType = ViewMetricsFragmentFactory.FragmentType.EXPRESSIONS_FRAGMENT;
        break;
      default:
    }

    Preferences.getInstance().setStringValue(
        Preferences.SELECTED_FRAGMENT,
        fragmentType.toString()
    );
  }
}
