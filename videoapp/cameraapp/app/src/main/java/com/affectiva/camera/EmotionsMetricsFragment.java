package com.affectiva.camera;

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
 * Fragment for the emotions displaying.
 */
@SuppressWarnings(
    {
        "PMD.DoNotUseThreads",
        "PMD.DataflowAnomalyAnalysis"
    }
)
public class EmotionsMetricsFragment extends BaseMetricsFragment {

  private TextView dominantEmotionIndicator;

  private TextView moodIndicator;

  private ProgressBar angerProgressBar;

  private ProgressBar joyProgressBar;

  private ProgressBar neutralProgressBar;

  private ProgressBar sadnessProgressBar;

  private ProgressBar surpriseProgressBar;

  private ProgressBar valenceProgressBar;

  private ProgressBar valenceProgressBarNegative;

  @Override
  public View onCreateView(
      @NonNull final LayoutInflater inflater,
      final ViewGroup container,
      final Bundle savedInstanceState
  ) {
    final View rootView = inflater.inflate(
        R.layout.emotions_fragment,
        container,
        false
    );
    initializeUi(rootView);
    return rootView;
  }

  private void initializeUi(final View rootView) {
    dominantEmotionIndicator = rootView.findViewById(R.id.dominant_emotion_indicator);
    moodIndicator = rootView.findViewById(R.id.mood_indicator);
    angerProgressBar = rootView.findViewById(R.id.anger_progressBar);
    joyProgressBar = rootView.findViewById(R.id.joy_progressBar);
    neutralProgressBar = rootView.findViewById(R.id.neutral_progressBar);
    sadnessProgressBar = rootView.findViewById(R.id.sadness_progressBar);
    surpriseProgressBar = rootView.findViewById(R.id.surprise_progressBar);
    valenceProgressBar = rootView.findViewById(R.id.valence_progressBar);
    valenceProgressBarNegative = rootView.findViewById(R.id.valence_progressBarNegative);
  }

  /**
   * Updates emotions metrics.
   *
   * @param face for which metrics are updated.
   */
  @SuppressWarnings("ConstantConditions")
  @Override
  public void updateMetrics(final Face face) {
    final Face.DominantEmotionMetric dominantEmotionMetric = face.getDominantEmotion();
    final Face.DominantEmotion dominantEmotion = dominantEmotionMetric.getDominantEmotion();
    final Map<Face.Emotion, Float> emotionsMap = face.getEmotions();
    final Face.Mood mood = face.getMood();

    final int angerValue = Math.round(emotionsMap.get(Face.Emotion.ANGER));
    final int joyValue = Math.round(emotionsMap.get(Face.Emotion.JOY));
    final int neutralValue = Math.round(emotionsMap.get(Face.Emotion.NEUTRAL));
    final int sadnessValue = Math.round(emotionsMap.get(Face.Emotion.SADNESS));
    final int surpriseValue = Math.round(emotionsMap.get(Face.Emotion.SURPRISE));
    final int valenceValue = Math.abs(emotionsMap.get(Face.Emotion.VALENCE).intValue());

    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        dominantEmotionIndicator.setText(String.valueOf(dominantEmotion));
        moodIndicator.setText(String.valueOf(mood));
        angerProgressBar.setProgress(angerValue);
        joyProgressBar.setProgress(joyValue);
        neutralProgressBar.setProgress(neutralValue);
        sadnessProgressBar.setProgress(sadnessValue);
        surpriseProgressBar.setProgress(surpriseValue);

        if (emotionsMap.get(Face.Emotion.VALENCE) < 0) {
          valenceProgressBar.setVisibility(View.GONE);
          valenceProgressBarNegative.setVisibility(View.VISIBLE);
          valenceProgressBarNegative.setProgress(valenceValue);
        } else {
          valenceProgressBarNegative.setVisibility(View.GONE);
          valenceProgressBar.setVisibility(View.VISIBLE);
          valenceProgressBar.setProgress(valenceValue);
        }
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
        dominantEmotionIndicator.setText("");
        moodIndicator.setText("");
        angerProgressBar.setProgress(0);
        joyProgressBar.setProgress(0);
        neutralProgressBar.setProgress(0);
        sadnessProgressBar.setProgress(0);
        surpriseProgressBar.setProgress(0);
        valenceProgressBar.setProgress(0);
        valenceProgressBarNegative.setProgress(0);
      });
    }
  }
}
