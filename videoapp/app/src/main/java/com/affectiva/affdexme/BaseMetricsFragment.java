package com.affectiva.affdexme;

import android.support.v4.app.Fragment;

import com.affectiva.vision.Face;

/**
 * Base metrics fragment contains methods for the UI updating.
 */
abstract class BaseMetricsFragment extends Fragment {

  /**
   * Updates metrics in the fragment, when the face was received in the onImageResult() method .
   *
   * @param face for which metrics are updated.
   */
  public abstract void updateMetrics(final Face face);

  /**
   * Clears the metrics in the fragment, when no faces were received in the onImageResult() method.
   */
  public abstract void setMetricsToZero();
}
