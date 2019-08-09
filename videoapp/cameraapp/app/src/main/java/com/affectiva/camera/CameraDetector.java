package com.affectiva.camera;

import android.content.Context;
import android.view.SurfaceView;

import com.affectiva.vision.Feature;
import com.affectiva.vision.Frame;
import com.affectiva.vision.FrameDetector;
import com.affectiva.vision.ImageListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade service which integrates Android Camera API and
 * Affectiva FrameDetector in order to process device camera frames.
 */
@SuppressWarnings("WeakerAccess")
class CameraDetector implements CameraHelper.OnFrameAvailableEventListener {

  private final Context context;

  private final CameraHelper cameraHelper;

  private FrameDetector frameDetector;

  private final CameraType cameraType;

  private Long firstFrameTimestamp;

  private CameraEventListener listener;

  private static final int MAX_FACES_NUMBER = 1;

  private static final int MAX_THREAD_POOL_SIZE = 0;

  /**
   * CameraDetector constructor.
   *
   * @param context             application context.
   * @param initialCameraFacing identifier of the camera type.
   */
  public CameraDetector(
      final Context context,
      final SurfaceView surfaceView,
      final CameraType initialCameraFacing
  ) {
    this.context = context;
    this.cameraType = initialCameraFacing;
    initializeFrameDetector();

    this.cameraHelper = new CameraHelper(context, surfaceView);

  }

  /**
   * Starts frameDetector and camera.
   */
  protected void start() {
    if (this.frameDetector != null && !this.frameDetector.isRunning()) {
      frameDetector.start();
      cameraHelper.setOnFrameAvailableListener(this);
      cameraHelper.startCamera(cameraType);
    }
  }

  /**
   * Stops frameDetector and camera.
   */
  protected void stop() {
    if (frameDetector != null) {
      frameDetector.stop();
      cameraHelper.setOnFrameAvailableListener(null);
      cameraHelper.stopCamera();
      resetTimestamp();
    }
  }

  /**
   * Disposes frameDetector.
   */
  @SuppressWarnings("PMD.NullAssignment")
  protected void dispose() {
    if (frameDetector != null) {
      frameDetector.dispose();
      frameDetector = null;
    }
  }

  /**
   * Checks frameDetector's status.
   *
   * @return true if frameDetector running.
   */
  @SuppressWarnings(
      {
          "PMD.ConfusingTernary",
          "PMD.OnlyOneReturn"
      }
  )
  protected boolean isRunning() {
    if (frameDetector != null) {
      return frameDetector.isRunning();
    } else {
      return false;
    }
  }

  /**
   * Sets onCameraEventListener.
   *
   * @param listener listener.
   */
  public void setOnCameraEventListener(final CameraEventListener listener) {
    this.listener = listener;
  }

  /**
   * Sets frameDetector's imageListener.
   *
   * @param imageListener imageListener.
   */
  public void setImageListener(final ImageListener imageListener) {
    this.frameDetector.setImageListener(imageListener);
  }

  /**
   * Resets the time of first frame.
   */
  @SuppressWarnings("PMD.NullAssignment")
  private void resetTimestamp() {
    this.firstFrameTimestamp = null;
  }

  /**
   * Initialize FrameDetector.
   */
  @SuppressWarnings(
      {
          "PMD.AvoidDuplicateLiterals",
          "PMD.SystemPrintln"
      }
  )
  private void initializeFrameDetector() {
    final int processingFrameRate = Preferences.getInstance().getIntValue(
        Preferences.PROCESSING_FRAME_RATE,
        20);


    frameDetector = new FrameDetector(
        context,
        processingFrameRate,
        MAX_FACES_NUMBER,
        MAX_THREAD_POOL_SIZE
    );

    enableFrameDetectorsFeatures();
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  @Override
  public void onFrameAvailable(
      final byte[] frameBytes,
      final int width,
      final int height,
      final Frame.Rotation rotation
  ) {
    final long currentTime = System.nanoTime();

    if (firstFrameTimestamp == null) {
      firstFrameTimestamp = currentTime;
    }

    final long timestamp = (currentTime - firstFrameTimestamp) / 1000000;

    final Frame frame = new Frame(
        width,
        height,
        frameBytes,
        Frame.ColorFormat.RGBA,
        rotation,
        timestamp
    );

    if (frameDetector != null && frameDetector.isRunning()) {
      frameDetector.process(frame);
    }
  }

  @Override
  public void onFrameSizeSelected(
      final int width,
      final int height,
      final Frame.Rotation rotation
  ) {
    if (listener != null) {
      listener.onCameraSizeSelected(width, height, rotation);
    }

    //Since frame size has been changed, we reset the SDK metrics
    //This also causes the SDK to reset after device rotations
    frameDetector.reset();
    resetTimestamp();
  }

  /**
   * Enable detecting metrics, depending on the choice of settings.
   */
  private void enableFrameDetectorsFeatures() {
    final List<Feature> features = new ArrayList<>();
    features.add(Feature.EMOTIONS);
    features.add(Feature.EXPRESSIONS);

    frameDetector.enable(features);
  }

  /**
   * Camera type enum.
   */
  public enum CameraType {
    CAMERA_FRONT,
    CAMERA_BACK
  }

  /**
   * Reports events related to the handling of the Android Camera by {@link CameraDetector}.
   */
  public interface CameraEventListener {
    /**
     * Called when the size and orientation of the camera preview frames are known.
     *
     * @param width    the unrotated width of the camera frames.
     * @param height   the unrotated height of the camera frames.
     * @param rotation the direction in which camera frames will be rotated
     *                 before processing by the SDK.
     */
    void onCameraSizeSelected(int width, int height, Frame.Rotation rotation);
  }
}
