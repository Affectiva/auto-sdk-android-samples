package com.affectiva.affdexme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.affectiva.vision.Face;
import com.affectiva.vision.Frame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines a custom SurfaceView class which handles the drawing thread.
 **/
@SuppressWarnings(
    {
        "PMD.NullAssignment",
        "PMD.DoNotUseThreads",
        "PMD.TooManyMethods",
        "PMD.DataflowAnomalyAnalysis"
    }
)
public class FrameDrawer
    extends SurfaceView
    implements SurfaceHolder.Callback, View.OnTouchListener, Runnable {

  private static final String TAG = "BaseSurface";

  private static final float FACE_POINTS_STROKE_WIDTH = 5;

  private static final int FONT_SIZE = 50;

  private static final int FACE_ID_MARGIN = 20;

  /**
   * Holds the surface frame.
   */
  private SurfaceHolder surfaceHolder;

  /**
   * Draw thread.
   */
  private Thread drawThread;

  /**
   * True when the surface is ready to draw.
   */
  private boolean surfaceReady;

  /**
   * Drawing thread flag.
   */
  private boolean drawingActive;

  private Bitmap frameBitmap;

  private final BitmapParamsHolder bitmapParamsHolder;

  private final DrawFiguresHolder drawFiguresHolder;

  private final PaintsHolder paintsHolder;

  /**
   * BaseSurface constructor.
   *
   * @param context application context.
   * @param attrs   view attributes.
   */
  public FrameDrawer(final Context context, final AttributeSet attrs) {
    super(context, attrs);

    final SurfaceHolder holder = getHolder();
    holder.addCallback(this);
    setOnTouchListener(this);

    bitmapParamsHolder = new BitmapParamsHolder();

    drawFiguresHolder = new DrawFiguresHolder();
    paintsHolder = new PaintsHolder();
  }

  @Override
  public void surfaceChanged(
      final SurfaceHolder holder,
      final int format,
      final int width,
      final int height
  ) {
    // no processing required now
  }

  @Override
  public void surfaceCreated(final SurfaceHolder holder) {
    this.surfaceHolder = holder;

    if (drawThread != null) {
      Log.d(TAG, "draw thread still active..");
      drawingActive = false;
      try {
        drawThread.join();
      } catch (InterruptedException e) { // do nothing
      }
    }

    surfaceReady = true;
    startDrawThread();
    Log.d(TAG, "Created");
  }

  @Override
  public void surfaceDestroyed(final SurfaceHolder holder) {
    // Surface is not used anymore - stop the drawing thread
    stopDrawThread();
    // and release the surface
    holder.getSurface().release();

    this.surfaceHolder = null;
    surfaceReady = false;
    Log.d(TAG, "Destroyed");
  }

  @Override
  public boolean onTouch(final View view, final MotionEvent event) {
    // Handle touch events
    return true;
  }

  /**
   * Creates a new draw thread and starts it.
   */
  public void startDrawThread() {
    if (surfaceReady && drawThread == null) {
      drawThread = new Thread(this, "Draw thread");
      drawingActive = true;
      drawThread.start();
    }
  }

  /**
   * Stops the drawing thread.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void stopDrawThread() {
    if (drawThread == null) {
      Log.d(TAG, "DrawThread is null");
      return;
    }
    drawingActive = false;
    while (true) {
      try {
        Log.d(TAG, "Request last frame");
        drawThread.join(5000);
        break;
      } catch (InterruptedException e) {
        Log.e(TAG, "Could not join with draw thread");
      }
    }
    drawThread = null;
  }

  /**
   * Removes bounding boxes, face points, face id displaying.
   */
  public void clearBoundingBoxes() {
    drawFiguresHolder.clear();
  }

  /**
   * Creates bitmap from the frame, sets faces coordinates.
   *
   * @param frame    frameBitmap.
   * @param facesMap faces with metrics
   */
  @SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
  public synchronized void drawFrameAndBoundingBoxes(
      final Frame frame,
      final Map<Integer, Face> facesMap
  ) {
    createBitmapFromFrame(frame);
    setFacesCoordinates(facesMap);
  }

  private void createBitmapFromFrame(final Frame frame) {
    this.frameBitmap = Bitmap.createBitmap(
        frame.getWidth(),
        frame.getHeight(),
        Bitmap.Config.ARGB_8888
    );
    this.frameBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(getRgbaFromBgr(frame.getPixels())));
  }

  private void setFacesCoordinates(final Map<Integer, Face> facesMap) {
    drawFiguresHolder.clear();

    facesMap.forEach((k, face) -> {
      drawFiguresHolder.addFacePoints(
          face.getId(),
          calculateFacePointsCoordinates(face.getFacePoints())
      );

      drawFiguresHolder.addBoundingBox(
          face.getId(),
          calculateBoundingBoxesCoordinates(face.getBoundingBox())
      );
    });
  }

  // Converts bgr-bytes array to rgba-bytes array
  private byte[] getRgbaFromBgr(final byte[] bgr) {
    byte[] rgba = new byte[bgr.length / 3 * 4];
    int index = 0;

    for (int i = 0; i < bgr.length; i++) {
      rgba[index] = bgr[i + 2]; // R-channel
      rgba[index + 1] = bgr[i + 1]; // G-channel
      rgba[index + 2] = bgr[i]; // B-channel
      rgba[index + 3] = (byte) 255; // A-channel
      i = i + 2;
      index = index + 4;
    }

    return rgba;
  }

  /**
   * Calculate face points coordinates.
   *
   * @param facePoints face points.
   */
  @SuppressWarnings(
      {
          "ConstantConditions"
      }
  )
  private List<PointF> calculateFacePointsCoordinates(
      final Map<Face.FacePoint, Face.Point> facePoints
  ) {
    final PointF chinTipPoint = createPoint(facePoints.get(Face.FacePoint.CHIN_TIP));
    final PointF noseTipPoint = createPoint(facePoints.get(Face.FacePoint.NOSE_TIP));
    final PointF outerRightEyePoint = createPoint(facePoints.get(Face.FacePoint.OUTER_RIGHT_EYE));
    final PointF outerLeftEyePoint = createPoint(facePoints.get(Face.FacePoint.OUTER_LEFT_EYE));

    return Arrays.asList(
        chinTipPoint,
        noseTipPoint,
        outerLeftEyePoint,
        outerRightEyePoint
    );
  }

  private PointF createPoint(final Face.Point facePoint) {
    final PointF result = new PointF();
    result.x = mirrorXCoordinateIfNeeded(facePoint.getX()) * bitmapParamsHolder.getBitmapScale();
    result.y = facePoint.getY() * bitmapParamsHolder.getBitmapScale();

    return result;
  }

  /**
   * Calculate bounding box's coordinates.
   *
   * @param boundingBox bounding boxes list.
   */
  private RectF calculateBoundingBoxesCoordinates(final List<Face.Point> boundingBox) {
    final float scale = bitmapParamsHolder.getBitmapScale();

    final float left = mirrorXCoordinateIfNeeded(boundingBox.get(0).getX()) * scale
        + bitmapParamsHolder.getCoordinateX();
    final float top = boundingBox.get(0).getY() * scale
        + bitmapParamsHolder.getCoordinateY();

    final float right = mirrorXCoordinateIfNeeded(boundingBox.get(1).getX()) * scale
        + bitmapParamsHolder.getCoordinateX();
    final float bottom = boundingBox.get(1).getY() * scale
        + bitmapParamsHolder.getCoordinateY();

    return new RectF(left, top, right, bottom);
  }

  /**
   * The function mirrors the coordinate as android front camera mirrors the picture displayed.
   *
   * @param x0 x coordinate
   * @return coordinate
   */
  private float mirrorXCoordinateIfNeeded(final float x0) {
    return bitmapParamsHolder.getBitmapWidth() - x0;
  }

  private boolean isFaceDetected() {
    return !drawFiguresHolder.getBoundingBoxes().isEmpty();
  }

  @SuppressWarnings(
      {
          "PMD.AvoidCatchingGenericException",
          "PMD.AvoidInstantiatingObjectsInLoops"
      }
  )
  @Override
  public void run() {
    Log.d(TAG, "Draw thread started");

    try {
      while (drawingActive) {

        synchronized (this) {
          if (surfaceHolder == null) {
            return;
          }

          if (frameBitmap != null) {
            final Canvas canvas = surfaceHolder.lockCanvas();

            clearCanvas(canvas);

            try {
              final float scale = calculateScale();

              final Bitmap scaledBitmap = scaleBitmap(scale);
              final Bitmap mirroredBitmap = mirrorBitmap(scaledBitmap);

              calculateCenterPositionForBitmap(scale, mirroredBitmap);

              canvas.drawBitmap(
                  mirroredBitmap,
                  bitmapParamsHolder.getCoordinateX(),
                  bitmapParamsHolder.getCoordinateY(),
                  null
              );

              if (isFaceDetected()) {
                // draw bounding boxes
                drawFiguresHolder
                    .getBoundingBoxes()
                    .forEach((faceId, rect) -> {
                      canvas.drawRect(rect, paintsHolder.getBoundingBoxPaint());

                      // draw face id
                      canvas.drawText(
                          String.valueOf(faceId),
                          rect.left + FACE_ID_MARGIN,
                          rect.bottom,
                          paintsHolder.getFaceIdPaint()
                      );
                    });

                // draw face points
                drawFiguresHolder
                    .getFacesPoints()
                    .forEach((faceId, points) -> drawFacePoints(points, canvas));
              } else {
                clearBoundingBoxes();
              }
            } finally {
              surfaceHolder.unlockCanvasAndPost(canvas);
            }

            frameBitmap = null;
          }
        }

        try {
          Thread.sleep(40);
        } catch (InterruptedException e) {
          // ignore
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Exception while locking/unlocking: "
          + e.getMessage() + " / "
          + e.getClass().toString()
      );
    }
    Log.d(TAG, "Draw thread finished");
  }

  private float calculateScale() {
    final float surfaceAspectRatio = (float) this.getWidth() / this.getHeight();
    final float frameAspectRatio = (float) this.frameBitmap.getWidth()
        / this.frameBitmap.getWidth();

    float scale;
    if (frameAspectRatio < surfaceAspectRatio) {
      scale = (float) this.getHeight() / frameBitmap.getHeight();
    } else {
      scale = (float) this.getWidth() / frameBitmap.getWidth();
    }
    return scale;
  }

  private Bitmap scaleBitmap(final float scale) {
    return Bitmap.createScaledBitmap(
        this.frameBitmap,
        (int) (this.frameBitmap.getWidth() * scale),
        (int) (this.frameBitmap.getHeight() * scale),
        true
    );
  }

  private Bitmap mirrorBitmap(final Bitmap sourceBitmap) {
    final Matrix matrix = new Matrix();
    matrix.setScale(-1, 1);

    return Bitmap.createBitmap(
        sourceBitmap,
        0,
        0,
        sourceBitmap.getWidth(),
        sourceBitmap.getHeight(),
        matrix,
        false
    );
  }

  private void calculateCenterPositionForBitmap(final float scale, final Bitmap mirroredBitmap) {
    final int centerXCoordinate = (this.getWidth() - mirroredBitmap.getWidth()) / 2;
    final int centerYCoordinate = (this.getHeight() - mirroredBitmap.getHeight()) / 2;

    bitmapParamsHolder.setBitmapWidth(this.frameBitmap.getWidth());
    bitmapParamsHolder.setBitmapScale(scale);
    bitmapParamsHolder.setCoordinateX(centerXCoordinate);
    bitmapParamsHolder.setCoordinateY(centerYCoordinate);
  }

  private void clearCanvas(final Canvas canvas) {
    canvas.drawARGB(255, 0, 0, 0);
  }

  /**
   * Draw face points.
   *
   * @param facePointsMap face points map.
   * @param canvas        canvas.
   */
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void drawFacePoints(
      final List<PointF> facePointsMap,
      final Canvas canvas
  ) {
    for (final PointF pointF : facePointsMap) {
      canvas.drawCircle(
          pointF.x + bitmapParamsHolder.getCoordinateX(),
          pointF.y + bitmapParamsHolder.getCoordinateY(),
          FACE_POINTS_STROKE_WIDTH,
          paintsHolder.getFacePointsPaint()
      );
    }
  }

  /**
   * This class is use to storage drawing frameBitmap's parameters.
   */
  @SuppressWarnings(
      {
          "PMD.DataClass",
          "WeakerAccess"
      }
  )
  protected class BitmapParamsHolder {

    private int bitmapWidth;

    private float bitmapScale;

    private int coordinateX;

    private int coordinateY;

    /**
     * BitmapParamsHolder constructor.
     */
    protected BitmapParamsHolder() {

      bitmapWidth = 0;

      bitmapScale = 0f;

      coordinateX = 0;

      coordinateY = 0;
    }

    public int getBitmapWidth() {
      return bitmapWidth;
    }

    public void setBitmapWidth(final int bitmapWidth) {
      this.bitmapWidth = bitmapWidth;
    }

    public float getBitmapScale() {
      return bitmapScale;
    }

    public void setBitmapScale(final float bitmapScale) {
      this.bitmapScale = bitmapScale;
    }

    public int getCoordinateX() {
      return coordinateX;
    }

    public void setCoordinateX(final int coordinateX) {
      this.coordinateX = coordinateX;
    }

    public int getCoordinateY() {
      return coordinateY;
    }

    public void setCoordinateY(final int coordinateY) {
      this.coordinateY = coordinateY;
    }
  }

  /**
   * Storage for the transformed points for the received faces.
   */
  @SuppressWarnings("WeakerAccess")
  private class DrawFiguresHolder {
    private final Map<Integer, List<PointF>> facesPoints;

    private final Map<Integer, RectF> boundingBoxes;

    /**
     * Adds face points for the face.
     *
     * @param faceId face id.
     * @param points face points.
     */
    public void addFacePoints(
        final Integer faceId,
        final List<PointF> points
    ) {
      facesPoints.put(faceId, points);
    }

    /**
     * Adds bounding boxes' points for the face.
     *
     * @param faceId face id.
     * @param rect   rect with bounding boxes' points.
     */
    public void addBoundingBox(
        final Integer faceId,
        final RectF rect
    ) {
      this.boundingBoxes.put(faceId, rect);
    }

    /**
     * Clears maps with points.
     */
    public void clear() {
      if (!facesPoints.isEmpty()) {
        facesPoints.clear();
      }

      if (!boundingBoxes.isEmpty()) {
        boundingBoxes.clear();
      }
    }

    public Map<Integer, List<PointF>> getFacesPoints() {
      return facesPoints;
    }

    public Map<Integer, RectF> getBoundingBoxes() {
      return boundingBoxes;
    }

    /**
     * DrawFiguresHolder constructor.
     */
    public DrawFiguresHolder() {
      facesPoints = new ConcurrentHashMap<>();
      boundingBoxes = new ConcurrentHashMap<>();
    }
  }

  /**
   * This class is use to storage draw view's paints.
   */
  @SuppressWarnings(
      {
          "WeakerAccess",
          "PMD.AccessorMethodGeneration"
      }
  )
  private class PaintsHolder {
    private final Paint boundingBoxPaint;

    private final Paint facePointsPaint;

    private final Paint faceIdPaint;

    /**
     * PaintsHolder constructor.
     */
    public PaintsHolder() {

      boundingBoxPaint = new Paint();
      boundingBoxPaint.setColor(Color.WHITE);
      boundingBoxPaint.setStrokeWidth(2);
      boundingBoxPaint.setStyle(Paint.Style.STROKE);

      facePointsPaint = new Paint();
      facePointsPaint.setColor(Color.WHITE);
      final int strokeWidth = (int) (bitmapParamsHolder.getBitmapWidth() / 100f);
      facePointsPaint.setStrokeWidth(strokeWidth);

      faceIdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      faceIdPaint.setTextSize(FONT_SIZE);
      faceIdPaint.setStyle(Paint.Style.FILL);
      faceIdPaint.setColor(Color.WHITE);
    }

    public Paint getBoundingBoxPaint() {
      return boundingBoxPaint;
    }

    public Paint getFacePointsPaint() {
      return facePointsPaint;
    }

    public Paint getFaceIdPaint() {
      return faceIdPaint;
    }
  }
}