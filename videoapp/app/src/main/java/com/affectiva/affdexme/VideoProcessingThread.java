package com.affectiva.affdexme;

import android.content.Context;
import android.util.Log;

import com.affectiva.vision.Detector;
import com.affectiva.vision.Face;
import com.affectiva.vision.Feature;
import com.affectiva.vision.Frame;
import com.affectiva.vision.ImageListener;
import com.affectiva.vision.SyncFrameDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The thread runs video processing using FrameRetriever class which retrieves all the frames
 * from the video file and FrameDetector class which processes the frames and gets emotions from it.
 */
@SuppressWarnings(
    {
        "PMD.DoNotUseThreads",
        "WeakerAccess"
    }
)
public class VideoProcessingThread extends Thread {

  private static final String TAG = "VideoProcessingThread";

  private final String videoPath;

  private final Detector frameDetector;

  private VideoProcessingThreadListener listener;

  private final InternalListener internalListener;

  private FrameRetriever frameRetriever;

  private static final int MAX_FACES_NUMBER = 1;

  private static final int MAX_THREAD_POOL_SIZE = 0;

  /**
   * Constructor.
   *
   * @param videoPath path to the file which should be processed
   * @param context   application context.
   */
  public VideoProcessingThread(
      final String videoPath,
      final Context context
  ) {
    super();
    this.videoPath = videoPath;

    this.frameDetector = new SyncFrameDetector(
        context,
        MAX_FACES_NUMBER,
        MAX_THREAD_POOL_SIZE
    );

    this.internalListener = new InternalListener();
  }

  @Override
  public void run() {
    final List<Feature> features = new ArrayList<>();
    features.add(Feature.EMOTIONS);
    features.add(Feature.EXPRESSIONS);

    frameDetector.enable(features);
    frameDetector.setImageListener(internalListener);
    frameDetector.start();

    frameRetriever = new FrameRetriever(videoPath);
    frameRetriever.addListener(internalListener);
    frameRetriever.extractMpegFrames();
  }

  /**
   * Stops the processing of video file.
   */
  public void stopProcessing() {
    frameRetriever.stop();
    frameDetector.stop();
    frameRetriever.removeListener(internalListener);
  }

  /**
   * Sets the listener.
   *
   * @param listener listener
   */
  public void setListener(final VideoProcessingThreadListener listener) {
    this.listener = listener;
  }

  /**
   * Interface that can be used by VideoProcessingThread clients to receive the data.
   */
  public interface VideoProcessingThreadListener {

    /**
     * Calls on image result is received.
     *
     * @param faces faces map.
     * @param frame received frame.
     */
    void onImageResults(Map<Integer, Face> faces, Frame frame);

    /**
     * Calls on video process finished.
     */
    void onProcessingFinished();

    /**
     * Calls on video progress update.
     *
     * @param percent of update.
     */
    void onProgressUpdate(double percent);
  }

  /**
   * Internal class used for implementation of interfaces needed for FrameExtractor
   * and FrameDetector.
   */
  private class InternalListener
      implements ImageListener,
      FrameRetriever.FrameRetrieverListener {
    @Override
    public void onProcessingFinished() {
      if (listener != null) {
        listener.onProcessingFinished();
      }
    }

    @Override
    public void onFrameReady(
        final byte[] data,
        final int width,
        final int height,
        final FrameRetriever.ColorFormat format,
        final long timeStamp,
        final FrameRetriever.Rotation rotation
    ) {
      final Frame frame = new Frame(
          width,
          height,
          data,
          Frame.ColorFormat.RGBA,
          Frame.Rotation.CW_180,
          timeStamp / 1000
      );
      Log.i(TAG, "Frame sent to processing: " + frame.getTimeStamp());
      frameDetector.process(frame);
    }

    @Override
    public void onProgressUpdate(final double percent) {
      if (listener != null) {
        listener.onProgressUpdate(percent);
      }
    }

    @Override
    public void onImageResults(final Map<Integer, Face> map, final Frame frame) {
      if (listener != null) {
        listener.onImageResults(map, frame);
      }
    }

    @Override
    public void onImageCapture(final Frame frame) {
      // no processing required now
    }
  }
}

