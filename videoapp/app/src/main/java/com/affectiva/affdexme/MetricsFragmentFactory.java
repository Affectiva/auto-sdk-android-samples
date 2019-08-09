package com.affectiva.affdexme;

import android.util.Log;

/**
 * This class returns appropriate fragment class and appropriate radio button id,
 * depending on selected metric in the settings activity.
 */
@SuppressWarnings(
    {
        "PMD.ClassNamingConventions",
        "PMD.UseUtilityClass",
        "WeakerAccess"
    }
)
public class MetricsFragmentFactory {

  private static final String TAG = "MetricsFragmentFactory";

  /**
   * Returns appropriate fragment.
   *
   * @return fragment instance.
   */
  public static BaseMetricsFragment getFragment(final FragmentType fragmentType) {
    return fragmentType.getFragment();
  }

  /**
   * Returns appropriate button id.
   *
   * @return button id.
   */
  public static Integer getFragmentButtonId(final FragmentType fragmentType) {
    return fragmentType.getFragmentButtonId();
  }

  /**
   * Enum is use to storage fragment's class and appropriate radio button id.
   */
  public enum FragmentType {
    EMOTIONS_FRAGMENT(EmotionsMetricsFragment.class, R.id.emotions_button),
    EXPRESSIONS_FRAGMENT(ExpressionsMetricsFragment.class, R.id.expressions_button),
    MEASUREMENTS_FRAGMENT(MeasurementsMetricsFragment.class, R.id.measurements_button);

    private final Class<?> fragmentClass;

    private final Integer fragmentButtonId;

    /**
     * MetricsFragmentFactory constructor.
     *
     * @param fragmentClass    fragment class.
     * @param fragmentButtonId appropriate button id.
     */
    FragmentType(final Class<?> fragmentClass, final Integer fragmentButtonId) {
      this.fragmentClass = fragmentClass;
      this.fragmentButtonId = fragmentButtonId;
    }

    /**
     * Returns appropriate fragment.
     *
     * @return fragment instance.
     */
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public BaseMetricsFragment getFragment() {
      BaseMetricsFragment metricsFragment = null;
      try {
        metricsFragment = (BaseMetricsFragment) fragmentClass.newInstance();
      } catch (IllegalAccessException | InstantiationException e) {
        Log.e(TAG, e.getMessage());
      }
      return metricsFragment;
    }

    /**
     * Returns appropriate button id.
     *
     * @return button id.
     */
    public Integer getFragmentButtonId() {
      return fragmentButtonId;
    }
  }
}
