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
 * Fragment for the expressions displaying.
 */
@SuppressWarnings(
    {
        "PMD.DoNotUseThreads",
        "PMD.DataflowAnomalyAnalysis"
    }
)
public class ExpressionsMetricsFragment extends BaseMetricsFragment {

  private TextView blinkIndicator;

  private TextView blinkRateIndicator;

  private ProgressBar smileProgressBar;

  private ProgressBar browRaiseProgressBar;

  private ProgressBar browFurrowProgressBar;

  private ProgressBar noseWrinkleProgressBar;

  private ProgressBar upperLipRaiseProgressBar;

  private ProgressBar mouthOpenProgressBar;

  private ProgressBar eyeClosureProgressBar;

  private ProgressBar cheekRaiseProgressBar;

  private ProgressBar yawnProgressBar;

  @Override
  public View onCreateView(
      @NonNull final LayoutInflater inflater,
      final ViewGroup container,
      final Bundle savedInstanceState
  ) {
    final View rootView = inflater.inflate(
        R.layout.expressions_fragment,
        container,
        false
    );
    initializeUi(rootView);
    return rootView;
  }

  private void initializeUi(final View rootView) {
    blinkIndicator = rootView.findViewById(R.id.blink_indicator);
    blinkRateIndicator = rootView.findViewById(R.id.blink_rate_indicator);
    smileProgressBar = rootView.findViewById(R.id.smile_progressBar);
    browRaiseProgressBar = rootView.findViewById(R.id.brow_raise_progressBar);
    browFurrowProgressBar = rootView.findViewById(R.id.brow_furrow_progressBar);
    noseWrinkleProgressBar = rootView.findViewById(R.id.nose_wrinkle_progressBar);
    upperLipRaiseProgressBar = rootView.findViewById(R.id.upper_lip_raise_progressBar);
    mouthOpenProgressBar = rootView.findViewById(R.id.mouth_open_progressBar);
    eyeClosureProgressBar = rootView.findViewById(R.id.eye_closure_progressBar);
    cheekRaiseProgressBar = rootView.findViewById(R.id.cheek_raise_progressBar);
    yawnProgressBar = rootView.findViewById(R.id.yawn_progressBar);
  }

  /**
   * Updates metrics.
   *
   * @param face for which metrics are updated.
   */
  @SuppressWarnings("ConstantConditions")
  @Override
  public void updateMetrics(final Face face) {
    final Map<Face.Expression, Float> expressionMap = face.getExpressions();

    final int smileValue = Math.round(expressionMap.get(Face.Expression.SMILE));
    final int blinkValue = Math.round(expressionMap.get(Face.Expression.BLINK));
    final int blinkRateValue = Math.round(expressionMap.get(Face.Expression.BLINK_RATE));
    final int browRaiseValue = Math.round(expressionMap.get(Face.Expression.BROW_RAISE));
    final int browFurrowValue = Math.round(expressionMap.get(Face.Expression.BROW_FURROW));
    final int noseWrinkleValue = Math.round(expressionMap.get(Face.Expression.NOSE_WRINKLE));
    final int upperLipRaiseValue = Math.round(expressionMap.get(Face.Expression.UPPER_LIP_RAISE));
    final int mouthOpenValue = Math.round(expressionMap.get(Face.Expression.MOUTH_OPEN));
    final int eyeClosureValue = Math.round(expressionMap.get(Face.Expression.EYE_CLOSURE));
    final int cheekRaiseValue = Math.round(expressionMap.get(Face.Expression.CHEEK_RAISE));
    final int yawnValue = Math.round(expressionMap.get(Face.Expression.YAWN));

    final String blinkRateToDisplay = blinkRateValue == 0 ? "--" : String.valueOf(blinkRateValue);

    if (getActivity() != null) {
      getActivity().runOnUiThread(() -> {
        smileProgressBar.setProgress(smileValue);
        blinkIndicator.setText(String.valueOf(blinkValue));
        blinkRateIndicator.setText(blinkRateToDisplay);
        browRaiseProgressBar.setProgress(browRaiseValue);
        browFurrowProgressBar.setProgress(browFurrowValue);
        noseWrinkleProgressBar.setProgress(noseWrinkleValue);
        upperLipRaiseProgressBar.setProgress(upperLipRaiseValue);
        mouthOpenProgressBar.setProgress(mouthOpenValue);
        eyeClosureProgressBar.setProgress(eyeClosureValue);
        cheekRaiseProgressBar.setProgress(cheekRaiseValue);
        yawnProgressBar.setProgress(yawnValue);
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
        blinkIndicator.setText("");
        blinkRateIndicator.setText("");
        smileProgressBar.setProgress(0);
        browRaiseProgressBar.setProgress(0);
        browFurrowProgressBar.setProgress(0);
        noseWrinkleProgressBar.setProgress(0);
        upperLipRaiseProgressBar.setProgress(0);
        mouthOpenProgressBar.setProgress(0);
        eyeClosureProgressBar.setProgress(0);
        cheekRaiseProgressBar.setProgress(0);
        yawnProgressBar.setProgress(0);
      });
    }
  }
}
