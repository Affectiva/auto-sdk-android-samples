package com.affectiva.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.affectiva.vision.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * CameraHelper class controls the camera and surfaceView.
 */
@SuppressWarnings(
    {
        "PMD.DataflowAnomalyAnalysis",
        "PMD.TooManyMethods",
        "PMD.AvoidCatchingGenericException",
        "WeakerAccess",
        "deprecation"
    }
)
public class CameraHelper
    extends OrientationEventListener
    implements SurfaceHolder.Callback {

  private static final String TAG = "CameraHelper";

  private static final int PREVIEW_IMAGE_FORMAT = ImageFormat.NV21;

  private static final float TARGET_FRAME_RATE = 5;

  private static final Integer MAX_IMAGE_HEIGHT = 480;

  private final CameraFacade cameraFacade;

  private final SurfaceHolder surfaceHolder;

  private Camera camera;

  private int previewWidth;
  private int previewHeight;

  private int displayRotation;

  private Frame.Rotation frameRotation;

  private final Display defaultDisplay;

  //boolean states
  private boolean isPreviewing;
  private boolean isSurfaceCreated;
  private boolean isCameraStarted;

  private OnFrameAvailableEventListener listener;

  private static int[] pixelsTmp;

  /**
   * Listen the frame ready event.
   */
  public interface OnFrameAvailableEventListener {
    /**
     * Calls when frame is available.
     */
    void onFrameAvailable(byte[] frame, int width, int height, Frame.Rotation rotation);

    /**
     * Calls when frame's size changed.
     */
    void onFrameSizeSelected(int width, int height, Frame.Rotation rotation);
  }

  /**
   * CameraHelper constructor.
   *
   * @param context             application context.
   * @param providedSurfaceView surfaceView.
   */
  protected CameraHelper(final Context context, final SurfaceView providedSurfaceView) {
    super(context);

    this.defaultDisplay
        = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
        .getDefaultDisplay();

    this.cameraFacade = new CameraFacade();

    this.surfaceHolder = providedSurfaceView.getHolder();
    this.surfaceHolder.addCallback(this);
  }

  /**
   * Sets onFrameAvailableEventListener.
   *
   * @param listener listener.
   */
  public void setOnFrameAvailableListener(final OnFrameAvailableEventListener listener) {
    this.listener = listener;
  }

  @Override
  public void surfaceCreated(final SurfaceHolder holder) {
    isSurfaceCreated = true;
    if (isCameraStarted) {
      startPreviewing(holder);
    }
  }

  @Override
  public void surfaceChanged(
      final SurfaceHolder holder,
      final int format,
      final int width,
      final int height
  ) {
    if (isCameraStarted) {
      stopPreviewing();
      startPreviewing(holder);
    }
  }

  @Override
  public void surfaceDestroyed(final SurfaceHolder holder) {
    isSurfaceCreated = false;
    if (isCameraStarted) {
      stopPreviewing();
    }
  }

  /**
   * Stops camera previewing.
   */
  private void stopPreviewing() {
    if (isPreviewing) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      disable(); // disable orientation listening
    }
    isPreviewing = false;
  }

  /**
   * Stops previewing, detaches from the SurfaceHolder, and releases the exclusive
   * lock on the camera.
   */
  @SuppressWarnings("PMD.NullAssignment")
  public void stopCamera() {
    if (camera != null && isCameraStarted) {
      if (isPreviewing) {
        stopPreviewing();
      }
      camera.release();
      camera = null;
    }

    isCameraStarted = false;
  }

  /**
   * Acquires exclusive access to the device's camera and connects to the SurfaceHolder that will
   * be used for previewing. Note that previewing will not begin until we receive the
   * surfaceChanged callback from the SurfaceHolder.
   */
  public void startCamera(final CameraDetector.CameraType cameraType) {
    // Open camera and initialize its parameters
    camera = cameraFacade.acquireCamera(cameraType);
    initCameraParams();

    setCameraDisplayOrientation();

    isCameraStarted = true;

    if (isSurfaceCreated) {
      startPreviewing(surfaceHolder);
    }
  }

  /**
   * Starts camera previewing.
   *
   * @param holder surface holder.
   */
  private void startPreviewing(final SurfaceHolder holder) {
    try {
      camera.setPreviewDisplay(holder);
    } catch (IOException e) {
      Log.e(TAG, "Unable to start camera preview" + e.getMessage());
    }

    enable(); // enable orientation listening

    camera.setPreviewCallback((data, camera) -> {
      final Bitmap bitmap = getBitmapFromBytes(
          convertNv21toArgb8888(data, previewWidth, previewHeight));

      final byte[] frameData = getByteArrayFromBitmap(bitmap);

      if (listener != null) {
        listener.onFrameAvailable(frameData, previewWidth, previewHeight, frameRotation);
      }
    });

    camera.startPreview();
    isPreviewing = true;
  }

  /**
   * Converts received byte array from YUV_NV21 format to ARGB8888.
   *
   * @param data   byte array.
   * @param width  frame width.
   * @param height frame height.
   * @return converted byte array.
   */
  @SuppressWarnings(
      {
          "PMD.ShortVariable",
          "PMD.MethodNamingConventions",
          "PMD.OneDeclarationPerLine"
      }
  )
  private int[] convertNv21toArgb8888(
      final byte[] data,
      final int width,
      final int height
  ) {
    final int size = width * height;
    if (pixelsTmp == null) {
      pixelsTmp = new int[size];
    }
    int u;
    int v;
    int y1;
    int y2;
    int y3;
    int y4;

    for (int i = 0, k = 0; i < size; i += 2, k += 2) {
      y1 = data[i] & 0xff;
      y2 = data[i + 1] & 0xff;
      y3 = data[width + i] & 0xff;
      y4 = data[width + i + 1] & 0xff;

      v = data[size + k] & 0xff;
      u = data[size + k + 1] & 0xff;
      v = v - 128;
      u = u - 128;

      pixelsTmp[i] = convertYuvToArgb(y1, u, v);
      pixelsTmp[i + 1] = convertYuvToArgb(y2, u, v);
      pixelsTmp[width + i] = convertYuvToArgb(y3, u, v);
      pixelsTmp[width + i + 1] = convertYuvToArgb(y4, u, v);

      if (i != 0 && (i + 2) % width == 0) {
        i += width;
      }
    }

    return pixelsTmp;
  }

  @SuppressWarnings("PMD.ShortVariable")
  private int convertYuvToArgb(final int y, final int u, final int v) {
    int r = y + (int) (1.772f * v);
    int g = y - (int) (0.344f * v + 0.714f * u);
    int b = y + (int) (1.402f * u);
    r = r > 255 ? 255 : r < 0 ? 0 : r;
    g = g > 255 ? 255 : g < 0 ? 0 : g;
    b = b > 255 ? 255 : b < 0 ? 0 : b;
    return 0xff000000 | (r << 16) | (g << 8) | b;
  }

  @SuppressWarnings("PMD.UseVarargs")
  private Bitmap getBitmapFromBytes(final int[] data) {
    final Bitmap bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(data, 0, previewWidth, 0, 0, previewWidth, previewHeight);

    return bitmap;
  }

  private byte[] getByteArrayFromBitmap(final Bitmap bitmap) {
    final int byteCount = bitmap.getByteCount();
    final ByteBuffer byteBuffer = ByteBuffer.allocate(byteCount);
    bitmap.copyPixelsToBuffer(byteBuffer);

    return byteBuffer.array();
  }

  // If you quickly rotate 180 degrees, Activity does not restart, so you need this
  // orientation Listener.
  @Override
  public void onOrientationChanged(final int orientation) {
    // this method gets called for every tiny 1 degree change in orientation, so it's called
    // really often if the device is handheld. We don't need to reset the camera display
    // orientation unless there is a change to the display rotation
    // (i.e. a 90/180/270 degree switch).
    if (defaultDisplay.getRotation() != displayRotation) {
      displayRotation = defaultDisplay.getRotation();
      setCameraDisplayOrientation();
    }
  }

  /**
   * Initialize camera parameters.
   */
  private void initCameraParams() {
    final Camera.Parameters cameraParams = camera.getParameters();
    cameraParams.setPreviewFormat(PREVIEW_IMAGE_FORMAT);
    setOptimalPreviewFrameRate(cameraParams);
    setOptimalPreviewSize(cameraParams);

    camera.setParameters(cameraParams);
  }

  /**
   * Get camera's frame rates and set the frame rate closest to the target.
   * Camera provides a set of ranges. Fixed frame rates have lo=hi.
   * Variable frame rates have lo different from hi. Use target to match to the camera's high part
   * of the range.
   *
   * @param cameraParams camera params.
   */
  @SuppressWarnings(
      {
          "PMD.UseStringBufferForStringAppends",
          "PMD.AvoidFinalLocalVariable",
          "ConstantConditions"
      }
  )
  private static void setOptimalPreviewFrameRate(final Camera.Parameters cameraParams) {
    final List<int[]> ranges = cameraParams.getSupportedPreviewFpsRange();
    final int minimalRangesSize = 1;

    if (minimalRangesSize == ranges.size()) {
      return; // only one option: no need to set anything.
    }

    int[] optimalRange = null;
    int minDiff = Integer.MAX_VALUE;
    final int targetHiMS = (int) (1000 * TARGET_FRAME_RATE);

    for (final int[] range : ranges) {
      final int currentDiff = Math.abs(range[1] - targetHiMS);
      if (currentDiff <= minDiff) {
        optimalRange = range;
        minDiff = currentDiff;
      }
    }
    cameraParams.setPreviewFpsRange(optimalRange[0], optimalRange[1]);
  }

  // Finds the closest height.
  @SuppressWarnings("ConstantConditions")
  private void setOptimalPreviewSize(final Camera.Parameters cameraParams) {
    final List<Camera.Size> supportedPreviewSizes = cameraParams.getSupportedPreviewSizes();

    if (null == supportedPreviewSizes) {
      Log.v(TAG, "Camera returning null for getSupportedPreviewSizes(), will use default");
      return;
    }

    Camera.Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    for (final Camera.Size size : supportedPreviewSizes) {
      if (Math.abs(size.height - MAX_IMAGE_HEIGHT) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - MAX_IMAGE_HEIGHT);
      }
    }

    previewWidth = optimalSize.width;
    previewHeight = optimalSize.height;

    cameraParams.setPreviewSize(previewWidth, previewHeight);
  }


  // Makes the camera image show in the same orientation as the display.
  private void setCameraDisplayOrientation() {
    final Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraFacade.getCameraId(), info);

    int degrees = 0;
    switch (displayRotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
      default:
    }

    int rotation;

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      //determine amount to rotate image and call computeFrameRotation()
      //to have the Frame.ROTATE object ready for CameraDetector to use
      rotation = (info.orientation + degrees) % 360;

      computeFrameRotation(rotation);

      //Android mirrors the image that will be displayed on screen, but not the image
      //that will be sent as bytes[] in onPreviewFrame(), so we compensate for mirroring after
      //calling computeFrameRotation()
      rotation = (360 - rotation) % 360; // compensate the mirror
    } else {
      // back-facing
      //determine amount to rotate image and call computeFrameRotation()
      //to have the Frame.ROTATE object ready for CameraDetector to use
      rotation = (info.orientation - degrees + 360) % 360;

      computeFrameRotation(rotation);
    }
    camera.setDisplayOrientation(rotation);

    //Now that rotation has been determined (or updated) inform listener of new frame size.
    if (listener != null) {
      listener.onFrameSizeSelected(previewWidth, previewHeight, frameRotation);
    }
  }

  /**
   * Compute frame rotation.
   *
   * @param rotation image rotation.
   */
  private void computeFrameRotation(final int rotation) {
    switch (rotation) {
      case 0:
        frameRotation = Frame.Rotation.UPRIGHT;
        break;

      case 90:
        frameRotation = Frame.Rotation.CW_90;
        break;

      case 180:
        frameRotation = Frame.Rotation.CW_180;
        break;

      case 270:
        frameRotation = Frame.Rotation.CW_270;
        break;

      default:
        frameRotation = Frame.Rotation.UPRIGHT;
        break;
    }
  }

  /**
   * CameraFacade manages camera creation and settings.
   */
  private static class CameraFacade {
    private int cameraId;

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private Camera acquireCamera(final CameraDetector.CameraType cameraType)
        throws IllegalStateException {

      if (cameraType == null) {
        throw new IllegalStateException("camera type must be set before calling acquireCamera");
      }

      final int cameraToOpen = CameraDetector.CameraType.CAMERA_FRONT == cameraType
          ? Camera.CameraInfo.CAMERA_FACING_FRONT
          : Camera.CameraInfo.CAMERA_FACING_BACK;

      final int cameraNumber = Camera.getNumberOfCameras();
      int cameraID = -1;
      final Camera.CameraInfo caminfo = new Camera.CameraInfo();

      for (int i = 0; i < cameraNumber; i++) {
        Camera.getCameraInfo(i, caminfo);
        if (caminfo.facing == cameraToOpen) {
          cameraID = i;
          break;
        }
      }
      if (cameraID == -1) {
        throw new IllegalStateException("This device does not have a camera of the requested type");
      }
      Camera result;
      try {
        result = Camera.open(cameraID);
      } catch (RuntimeException e) {
        final String msg = "Camera is unavailable. Please close the app that is using the camera "
            + "and then try again.\n" + "Error:  " + e.getMessage();
        throw new IllegalStateException(msg, e);
      }
      cameraId = cameraID;

      return result;
    }

    private int getCameraId() {
      return cameraId;
    }
  }
}
