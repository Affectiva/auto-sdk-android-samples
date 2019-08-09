package com.affectiva.affdexme;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.affectiva.vision.Face;

import java.util.Map;

/**
 * Fragment for the measurements displaying.
 */
@SuppressWarnings(
    {
        "PMD.DoNotUseThreads",
        "PMD.DataflowAnomalyAnalysis"
    }
)
public class MeasurementsMetricsFragment extends BaseMetricsFragment {

  private TextView pitchIndicator;

  private TextView rollIndicator;

  private TextView yawIndicator;

  private TextView brightnessIndicator;

  private ProgressBar brightnessProgressBar;

  private TextView interocularIndicator;

  @Override
  public View onCreateView(
      @NonNull final LayoutInflater inflater,
      final ViewGroup container,
      final Bundle savedInstanceState
  ) {
    final View rootView = inflater.inflate(
        R.layout.measurements_fragment,
        container,
        false
    );
    initializeUi(rootView);
    return rootView;
  }

  private void initializeUi(final View rootView) {
    pitchIndicator = rootView.findViewById(R.id.pitch_value);
    rollIndicator = rootView.findViewById(R.id.roll_value);
    yawIndicator = rootView.findViewById(R.id.yaw_value);
    brightnessIndicator = rootView.findViewById(R.id.brightness_value);
    brightnessProgressBar = rootView.findViewById(R.id.brightness_progressBar);
    interocularIndicator = rootView.findViewById(R.id.interocular_distance_value);
  }

  /**
   * Updates metrics.
   *
   * @param face for which metrics are updated.
   */
  @SuppressWarnings("ConstantConditions")
  @Override
  public void updateMetrics(final Face face) {
    final Map<Face.Measurement, Float> measurementMap = face.getMeasurements();
    final Map<Face.Quality, Float> qualityMap = face.getQualities();

    final int pitchValue = Math.round(measurementMap.get(Face.Measurement.PITCH));
    final int rollValue = Math.round(measurementMap.get(Face.Measurement.ROLL));
    final int yawValue = Math.round(measurementMap.get(Face.Measurement.YAW));
    final int interocularDistanceValue =
        Math.round(measurementMap.get(Face.Measurement.INTEROCULAR_DISTANCE));
    final int brightnessValue = Math.round(qualityMap.get(Face.Quality.BRIGHTNESS));

    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        pitchIndicator.setText(String.valueOf(pitchValue));
        rollIndicator.setText(String.valueOf(rollValue));
        yawIndicator.setText(String.valueOf(yawValue));
        interocularIndicator.setText(String.valueOf(interocularDistanceValue));
        brightnessIndicator.setText(String.valueOf(brightnessValue));
        brightnessProgressBar.setProgress(brightnessValue);
      });
    }
  }

  /**
   * Clears the metrics.
   */
  @Override
  public void setMetricsToZero() {
    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        rollIndicator.setText("");
        yawIndicator.setText("");
        pitchIndicator.setText("");
        brightnessIndicator.setText("");
        interocularIndicator.setText("");
        brightnessProgressBar.setProgress(0);
      });
    }
  }
}
