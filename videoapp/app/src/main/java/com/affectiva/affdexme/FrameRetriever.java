package com.affectiva.affdexme;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * This class is use to retrieve frames from the video source.
 */
@SuppressWarnings(
    {
        "PMD.DataflowAnomalyAnalysis",
        "PMD.NullAssignment",
        "PMD.AvoidThrowingRawExceptionTypes",
        "WeakerAccess"
    }
)
public class FrameRetriever {

  private static final String TAG = "FrameRetriever";

  private static final int EMPTY_FRAMES_MAX_COUNT = 10;

  private static final int BASE_HEIGHT = 480;

  private static final int TIMEOUT_MS = 2500;

  private static final int EGL_OPENGL_ES2_BIT = 0x0004;

  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

  private static final int TIMEOUT_USEC = 10_000;

  private int pictureWidth;

  private int pictureHeight;

  private String videoPath;

  private long videoLength;

  private Rotation rotation;

  private final List<FrameRetrieverListener> listeners = new ArrayList<>();

  private boolean stopped;

  /**
   * FrameRetriever constructor.
   *
   * @param videoPath video path
   */
  public FrameRetriever(final String videoPath) {
    this(videoPath, getRotation(videoPath));
  }

  /**
   * FrameRetriever constructor.
   *
   * @param videoPath the path to the file which should be processed.
   * @param rotation  rotation
   */
  public FrameRetriever(
      final String videoPath,
      final Rotation rotation
  ) {
    this.rotation = rotation;
    this.videoPath = videoPath;
    setVideoLength(videoPath);
  }

  private void setVideoLength(final String videoPath) {
    final MediaMetadataRetriever mmr = new MediaMetadataRetriever();
    mmr.setDataSource(videoPath);
    final String length = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

    this.pictureWidth = Integer.valueOf(
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    );

    this.pictureHeight = Integer.valueOf(
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    );

    this.videoLength = Long.parseLong(length) * 1000;
  }

  /**
   * returns frameRetriever status. True if it's stopped.
   *
   * @return is the frameRetriever stopped.
   */
  public boolean isStopped() {
    return stopped;
  }

  /**
   * Stops frameRetriever processing.
   */
  public void stop() {
    stopped = true;
  }

  /**
   * Adds frameRetriever listener.
   *
   * @param frameRetrieverListener listener.
   */
  public void addListener(final FrameRetrieverListener frameRetrieverListener) {
    listeners.add(frameRetrieverListener);
  }

  /**
   * Removes frameRetriever listener.
   *
   * @param frameRetrieverListener listener.
   */
  public void removeListener(final FrameRetrieverListener frameRetrieverListener) {
    listeners.remove(frameRetrieverListener);
  }

  /**
   * Returns video rotation.
   *
   * @param videoPath video path.
   * @return enum Rotation.
   */
  public static Rotation getRotation(final String videoPath) {
    final MediaMetadataRetriever mmr = new MediaMetadataRetriever();
    mmr.setDataSource(videoPath);
    final String rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
    mmr.release();
    final int rot = Integer.parseInt(rotation);

    Rotation result;
    switch (rot) {
      case 90:
        result = Rotation.BY_90_CW;
        break;
      case 180:
        result = Rotation.BY_180;
        break;
      case 270:
        result = Rotation.BY_90_CCW;
        break;
      default:
        result = Rotation.NO_ROTATION;
        break;
    }

    return result;
  }

  /**
   * Calculates the dimensions that the decoded frames should to maintain the correct aspect ratio.
   *
   * @param format   a MediaFormat object, used to extract image width and height information
   * @param rotation rotation
   * @return a Dimension object containing the base and height
   */
  private Dimension calculateFrameDimension(
      final MediaFormat format,
      final Rotation rotation
  ) {
    final int imageHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
    final int imageWidth = format.getInteger(MediaFormat.KEY_WIDTH);

    int resultWidth = 0;
    int resultHeight = 0;

    if (imageHeight > 0 || imageWidth > 0) {
      //base is height, returning scaled width
      resultHeight = BASE_HEIGHT;
      resultWidth = (int) ((float) BASE_HEIGHT * ((float) imageWidth / (float) imageHeight));

      // frames coming from MediaExtractor are rotated appropriately, but the height and width
      // information in MediaFormat are not swapped, so we must swap them ourselves!
      if (isPortrait(rotation)) {
        final int temp = resultWidth;
        //noinspection SuspiciousNameCombination
        resultWidth = resultHeight;
        resultHeight = temp;
      }
    }

    return new Dimension(resultWidth, resultHeight);
  }

  private boolean isPortrait(final Rotation rotation) {
    return rotation == Rotation.BY_90_CCW || rotation == Rotation.BY_90_CW;
  }

  /**
   * Tests extraction from an MP4 to a series of PNG files.
   * We scale the video to 640x480 for the PNG just to demonstrate that we can scale the video
   * with the GPU. If the input video has a different aspect ratio, we could preserve it by
   * adjusting the GL viewport to get letterboxing or pillarboxing, but generally if you're
   * extracting frames you don't want black bars.
   */
  @SuppressWarnings("PMD.PreserveStackTrace")
  public void extractMpegFrames() {
    final File inputFile = new File(videoPath);
    // The MediaExtractor error messages aren't very useful. Check to see if the input file
    // exists so we can throw a better one if it's not there.
    if (!inputFile.canRead()) {
      throw new RuntimeException("Unable to read " + inputFile);
    }

    final MediaExtractor extractor = new MediaExtractor();

    try {
      extractor.setDataSource(inputFile.toString());
    } catch (IOException e) {
      throw new RuntimeException(
          "failure when setting "
              + inputFile
              + "as data source for MediaExtractor"
      );
    }

    final int trackIndex = getVideoTrackNumber(extractor);
    if (trackIndex < 0) {
      throw new RuntimeException("No video track found in " + inputFile);
    }
    extractor.selectTrack(trackIndex);

    final MediaFormat format = extractor.getTrackFormat(trackIndex);

    final Dimension frameDimension = this.calculateFrameDimension(
        format,
        rotation
    );
    pictureWidth = frameDimension.getWidth();
    pictureHeight = frameDimension.getHeight();

    final CodecOutputSurface outputSurface = new CodecOutputSurface(pictureWidth, pictureHeight);

    extractMpegFrames(outputSurface, format, extractor);

    outputSurface.release();
    extractor.release();

    notifyListenersWithFinishedStatus();
  }

  private void extractMpegFrames(
      final CodecOutputSurface outputSurface,
      final MediaFormat format,
      final MediaExtractor extractor
  ) {
    MediaCodec decoder = null;

    try {
      // Create a MediaCodec decoder, and configure it with the MediaFormat from the
      // extractor. It's very important to use the format from the extractor because
      // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
      final String mime = format.getString(MediaFormat.KEY_MIME);
      decoder = MediaCodec.createDecoderByType(mime);

      decoder.configure(format, outputSurface.getSurface(), null, 0);
      decoder.start();

      doExtract(extractor, decoder, outputSurface);
    } catch (IOException e) {
      Log.e(TAG, e.getMessage(), e);
    } finally {

      // release everything we grabbed
      if (decoder != null) {
        decoder.stop();
        decoder.release();
      }
    }
  }

  /**
   * Returns video track number.
   *
   * @param extractor mediaExtractor.
   * @return track number.
   */
  public int getVideoTrackNumber(final MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    final int numTracks = extractor.getTrackCount();

    int result = -1;

    for (int i = 0; i < numTracks; i++) {
      final MediaFormat format = extractor.getTrackFormat(i);
      final String mime = format.getString(MediaFormat.KEY_MIME);
      if (mime.startsWith("video/")) {
        result = i;
        break;
      }
    }

    return result;
  }

  /**
   * Work loop.
   */
  @SuppressWarnings(
      {
          "PMD.UnusedLocalVariable",
          "PMD.EmptyIfStmt",
          "PMD.PrematureDeclaration",
          "PMD.CyclomaticComplexity"
      }
  )
  private void doExtract(
      final MediaExtractor extractor,
      final MediaCodec decoder,
      final CodecOutputSurface outputSurface
  ) {

    final ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
    final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    boolean outputDone = false;
    boolean inputDone = false;
    int emptyFrames = 0;
    while (!outputDone && !isStopped()) {
      // Feed more data to the decoder.
      if (!inputDone) {
        final int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufIndex >= 0) {
          final ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
          // Read the sample data into the ByteBuffer. This neither respects nor updates
          // inputBuf's position, limit, etc.
          final int chunkSize = extractor.readSampleData(inputBuf, 0);
          if (chunkSize < 0) {
            // End of stream -- send empty frame with EOS flag set.
            decoder.queueInputBuffer(
                inputBufIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            );
            inputDone = true;
          } else {
            final long presentationTimeUs = extractor.getSampleTime();
            decoder.queueInputBuffer(
                inputBufIndex,
                0,
                chunkSize,
                presentationTimeUs,
                0
            );
            extractor.advance();
          }
        }
      }

      if (!outputDone) {
        final int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
          // no output available yet
          Log.d(TAG, "no output from decoder available");
        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          // not important for us, since we're using Surface
        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          final MediaFormat newFormat = decoder.getOutputFormat();
        } else if (decoderStatus < 0) {
          throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: "
              + decoderStatus);
        } else { // decoderStatus >= 0
          if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            outputDone = true;
          }

          final boolean doRender = info.size != 0;
          if (info.size == 0) {
            emptyFrames++;
          }

          if (emptyFrames == EMPTY_FRAMES_MAX_COUNT) {
            throw new RuntimeException("exceeded max empty frames count");
          }
          // As soon as we call releaseOutputBuffer, the buffer will be forwarded to
          // SurfaceTexture to convert to a texture. The API doesn't guarantee that the
          // texture will be available before the call returns, so we need to wait for the
          // onFrameAvailable callback to fire.
          decoder.releaseOutputBuffer(decoderStatus, doRender);

          if (doRender) {
            outputSurface.awaitNewImage();
            outputSurface.drawImage();

            final ByteBuffer frame = outputSurface.convertFrame();
            notifyListenersWithFrame(frame.array(), pictureWidth, pictureHeight, ColorFormat.RGBA,
                info.presentationTimeUs);
          }

          final long currentTime = info.presentationTimeUs;
          notifyListenersWithProgress((currentTime * 100.0) / videoLength);
        }
      }
    }
  }

  @SuppressWarnings("SameParameterValue")
  private void notifyListenersWithFrame(
      final byte[] bytes,
      final int width,
      final int height,
      final ColorFormat format,
      final long time
  ) {
    for (final FrameRetrieverListener frameRetrieverListener : listeners) {
      frameRetrieverListener.onFrameReady(bytes, width, height, format, time, Rotation.BY_90_CW);
    }
  }

  private void notifyListenersWithProgress(final double progress) {
    for (final FrameRetrieverListener frameRetrieverListener : listeners) {
      frameRetrieverListener.onProgressUpdate(progress);
    }
  }

  private void notifyListenersWithFinishedStatus() {
    if (!stopped) {
      for (final FrameRetrieverListener listener : listeners) {
        listener.onProcessingFinished();
      }
    }
  }

  /**
   * Holds state associated with a Surface used for MediaCodec decoder output.
   * The constructor for this class will prepare GL, create a SurfaceTexture,
   * and then create a Surface for that SurfaceTexture.
   * The Surface can be passed to MediaCodec.configure() to receive decoder output. When a frame
   * arrives, we latch the texture with updateTexImage(), then render the texture with GL
   * to a pbuffer.
   * By default, the Surface will be using a BufferQueue in asynchronous mode, so we
   * can potentially drop frames.
   */
  private static class CodecOutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private STextureRender textureRender;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private final EGL10 egl;

    private EGLDisplay eglDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL10.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL10.EGL_NO_SURFACE;

    private final int width;
    private final int height;

    private final Object frameSyncObject = new Object(); // guards frameAvailable
    private boolean frameAvailable;

    private ByteBuffer pixelBuf;

    /**
     * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions. The new
     * EGL context and surface will be made current. Creates a Surface that can be passed to
     * MediaCodec.configure().
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public CodecOutputSurface(final int width, final int height) {
      if (width <= 0 || height <= 0) {
        throw new IllegalArgumentException();
      }
      egl = (EGL10) EGLContext.getEGL();
      this.width = width;
      this.height = height;

      eglSetup();
      makeCurrent();
      setup();
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
      textureRender = new STextureRender();
      textureRender.surfaceCreated();

      surfaceTexture = new SurfaceTexture(textureRender.getTextureId());
      surfaceTexture.setOnFrameAvailableListener(this);

      surface = new Surface(surfaceTexture);

      pixelBuf = ByteBuffer.allocateDirect(width * height * 4);
      pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Prepares EGL. We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    private void eglSetup() {
      eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
      if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
        throw new RuntimeException("unable to get EGL14 display");
      }

      final int[] version = new int[2];
      if (!egl.eglInitialize(eglDisplay, version)) {
        eglDisplay = null;
        throw new RuntimeException("unable to initialize EGL14");
      }

      // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
      final int[] attribList = {
          EGL10.EGL_RED_SIZE, 8,
          EGL10.EGL_GREEN_SIZE, 8,
          EGL10.EGL_BLUE_SIZE, 8,
          EGL10.EGL_ALPHA_SIZE, 8,
          EGL10.EGL_RENDERABLE_TYPE,
          EGL_OPENGL_ES2_BIT,
          EGL10.EGL_SURFACE_TYPE,
          EGL10.EGL_PBUFFER_BIT,
          EGL10.EGL_NONE
      };

      final EGLConfig[] configs = new EGLConfig[1];
      final int[] numConfigs = new int[1];
      if (!egl.eglChooseConfig(eglDisplay, attribList, configs, configs.length, numConfigs)) {
        throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
      }

      // Configure context for OpenGL ES 2.0.
      final int[] attributes = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
      eglContext = egl.eglCreateContext(
          eglDisplay, configs[0],
          EGL10.EGL_NO_CONTEXT,
          attributes
      );
      checkEglError("eglCreateContext");
      if (eglContext == null) {
        throw new RuntimeException("null context");
      }

      // Create a pbuffer surface.
      final int[] surfaceAttribs = {
          EGL10.EGL_WIDTH,
          width,
          EGL10.EGL_HEIGHT,
          height,
          EGL10.EGL_NONE
      };
      eglSurface = egl.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs);
      checkEglError("eglCreatePbufferSurface");
      if (eglSurface == null) {
        throw new RuntimeException("surface was null");
      }
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
      if (eglDisplay != EGL10.EGL_NO_DISPLAY) {
        egl.eglDestroySurface(eglDisplay, eglSurface);
        egl.eglDestroyContext(eglDisplay, eglContext);
        egl.eglMakeCurrent(
            eglDisplay,
            EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_CONTEXT
        );
        egl.eglTerminate(eglDisplay);
      }
      eglDisplay = EGL10.EGL_NO_DISPLAY;
      eglContext = EGL10.EGL_NO_CONTEXT;
      eglSurface = EGL10.EGL_NO_SURFACE;

      surface.release();

      // this causes a bunch of warnings that appear harmless but might confuse someone:
      // W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
      // surfaceTexture.release();

      textureRender = null;
      surface = null;
      surfaceTexture = null;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
      if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        throw new RuntimeException("eglMakeCurrent failed");
      }
    }

    /**
     * Returns the Surface.
     */
    public Surface getSurface() {
      return surface;
    }

    /**
     * Latches the next buffer into the texture. Must be called from the thread that created the
     * CodecOutputSurface object. (More specifically, it must be called on the thread with
     * the EGLContext that contains the GL texture object used by SurfaceTexture.)
     */
    public void awaitNewImage() {

      synchronized (frameSyncObject) {
        while (!frameAvailable) {
          try {
            // Wait for onFrameAvailable() to signal us. Use a timeout to avoid
            // stalling the test if it doesn't arrive.
            frameSyncObject.wait(TIMEOUT_MS);
            if (!frameAvailable) {
              throw new RuntimeException("frame wait timed out");
            }
          } catch (InterruptedException ie) {
            // shouldn't happen
            throw new RuntimeException(ie);
          }
        }
        frameAvailable = false;
      }

      // Latch the data.
      textureRender.checkGlError("before updateTexImage");
      surfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    public void drawImage() {
      textureRender.drawFrame(surfaceTexture);
    }

    // SurfaceTexture callback
    @Override
    public void onFrameAvailable(final SurfaceTexture st) {
      synchronized (frameSyncObject) {
        if (frameAvailable) {
          throw new RuntimeException("frameAvailable already set, frame could be dropped");
        }
        frameAvailable = true;
        frameSyncObject.notifyAll();
      }
    }

    /**
     * Converts frame to the byte buffer.
     *
     * @return byte buffer.
     */
    public ByteBuffer convertFrame() {
      pixelBuf.rewind();
      GLES20.glReadPixels(
          0,
          0,
          width,
          height,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          pixelBuf
      );
      return pixelBuf;
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private void checkEglError(final String msg) {
      int error;
      if ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
        throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
      }
    }
  }

  /**
   * Code for rendering a texture onto a surface using OpenGL ES 2.0.
   */
  @SuppressWarnings("PMD.LongVariable")
  private static class STextureRender {
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] triangleVerticesData = {
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0, 0.f, 0.f, 1.0f, -1.0f, 0, 1.f, 0.f, -1.0f,
        1.0f, 0, 0.f, 1.f, 1.0f, 1.0f, 0, 1.f, 1.f,};

    private final FloatBuffer triangleVertices;

    private static final String VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n"
        + "uniform mat4 uSTMatrix;\n"
        + "attribute vec4 aPosition;\n" + "attribute vec4 aTextureCoord;\n"
        + "varying vec2 vTextureCoord;\n" + "void main() {\n"
        + "    gl_Position = uMVPMatrix * aPosition;\n"
        + "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" + "}\n";

    private static final String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n"
        + "precision mediump float;\n"
        + // highp here doesn't seem to matter
        "varying vec2 vTextureCoord;\n" + "uniform samplerExternalOES sTexture;\n"
        + "void main() {\n"
        + "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" + "}\n";

    private final float[] mvpMatrix = new float[16];
    private final float[] stMatrix = new float[16];

    private int program;
    private int textureId = -12_345;
    private int muMvpMatrixHandle;
    private int muStMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    /**
     * STextureRender constructor.
     */
    public STextureRender() {
      triangleVertices = ByteBuffer.allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
          .order(ByteOrder.nativeOrder()).asFloatBuffer();
      triangleVertices.put(triangleVerticesData).position(0);

      Matrix.setIdentityM(stMatrix, 0);
    }

    /**
     * Returns texture ID.
     *
     * @return texture ID.
     */
    public int getTextureId() {
      return textureId;
    }

    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    public void drawFrame(final SurfaceTexture surfaceTexture) {
      checkGlError("onDrawFrame start");
      surfaceTexture.getTransformMatrix(stMatrix);

      // (optional) clearBoundingBoxes to green so we can see if we're failing to set pixels
      GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

      GLES20.glUseProgram(program);
      checkGlError("glUseProgram");

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

      triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
      GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
          TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
      checkGlError("glVertexAttribPointer maPosition");
      GLES20.glEnableVertexAttribArray(maPositionHandle);
      checkGlError("glEnableVertexAttribArray maPositionHandle");

      triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
      GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
          TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
      checkGlError("glVertexAttribPointer maTextureHandle");
      GLES20.glEnableVertexAttribArray(maTextureHandle);
      checkGlError("glEnableVertexAttribArray maTextureHandle");

      Matrix.setIdentityM(mvpMatrix, 0);
      GLES20.glUniformMatrix4fv(muMvpMatrixHandle, 1, false, mvpMatrix, 0);
      GLES20.glUniformMatrix4fv(muStMatrixHandle, 1, false, stMatrix, 0);

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      checkGlError("glDrawArrays");

      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    /**
     * Initializes GL state. Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
      program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
      if (program == 0) {
        throw new RuntimeException("failed creating program");
      }

      maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
      checkLocation(maPositionHandle, "aPosition");
      maTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
      checkLocation(maTextureHandle, "aTextureCoord");

      muMvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
      checkLocation(muMvpMatrixHandle, "uMVPMatrix");
      muStMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
      checkLocation(muStMatrixHandle, "uSTMatrix");

      final int[] textures = new int[1];
      GLES20.glGenTextures(1, textures, 0);

      textureId = textures[0];
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
      checkGlError("glBindTexture textureId");

      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
          GLES20.GL_NEAREST);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
          GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
          GLES20.GL_CLAMP_TO_EDGE);
      checkGlError("glTexParameter");
    }

    private int loadShader(final int shaderType, final String source) {
      int shader = GLES20.glCreateShader(shaderType);
      checkGlError("glCreateShader type=" + shaderType);
      GLES20.glShaderSource(shader, source);
      GLES20.glCompileShader(shader);
      final int[] compiled = new int[1];
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
      if (compiled[0] == 0) {
        Log.e(TAG, "Could not compile shader " + shaderType + ":");
        Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
        GLES20.glDeleteShader(shader);
        shader = 0;
      }
      return shader;
    }

    @SuppressWarnings(
        {
            "PMD.OnlyOneReturn",
            "SameParameterValue"
        }
    )
    private int createProgram(final String vertexSource, final String fragmentSource) {
      final int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
      if (vertexShader == 0) {
        return 0;
      }
      final int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
      if (pixelShader == 0) {
        return 0;
      }

      int program = GLES20.glCreateProgram();
      if (program == 0) {
        Log.e(TAG, "Could not create program");
      }
      GLES20.glAttachShader(program, vertexShader);
      checkGlError("glAttachShader");
      GLES20.glAttachShader(program, pixelShader);
      checkGlError("glAttachShader");
      GLES20.glLinkProgram(program);
      final int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] != GLES20.GL_TRUE) {
        Log.e(TAG, "Could not link program: ");
        Log.e(TAG, GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteProgram(program);
        program = 0;
      }
      return program;
    }

    /**
     * Checks GL error.
     *
     * @param processName checking process name.
     */
    public void checkGlError(final String processName) {
      final int error = GLES20.glGetError();
      if (error != GLES20.GL_NO_ERROR) {
        Log.e(TAG, processName + ": glError " + error);
        throw new RuntimeException(processName + ": glError " + error);
      }
    }

    /**
     * Check location.
     *
     * @param location location.
     * @param label    location label.
     */
    public static void checkLocation(final int location, final String label) {
      if (location < 0) {
        throw new RuntimeException("Unable to locate '" + label + "' in program");
      }
    }
  }

  /**
   * This class is use to track frame processing.
   */
  public interface FrameRetrieverListener {

    /**
     * Calls on video process finished.
     */
    void onProcessingFinished();

    /**
     * Calls on frame is extracted.
     *
     * @param frame     frame's byte array.
     * @param width     frame's width.
     * @param height    frame's height.
     * @param format    frame's color format.
     * @param timeStamp frame's timestamp.
     * @param rotation  frame's rotation.
     */
    void onFrameReady(
        final byte[] frame,
        final int width,
        final int height,
        final ColorFormat format,
        final long timeStamp,
        final Rotation rotation
    );

    /**
     * Calls on video progress update.
     *
     * @param percent of update.
     */
    void onProgressUpdate(double percent);
  }

  /**
   * Decode frame's color format storage.
   */
  public enum ColorFormat {
    RGBA,
    YUV_NV21,
    UNKNOWN_TYPE;
  }

  /**
   * Defines the desired rotation before processing.
   */
  public enum Rotation {
    BY_90_CW(90.),
    BY_180(180.),
    BY_90_CCW(-90.),
    NO_ROTATION(0.);

    private double angle;

    /**
     * Rotation constructor.
     *
     * @param rotationAngle angle.
     */
    Rotation(final double rotationAngle) {
      this.angle = rotationAngle;
    }

    /**
     * Method to get the rotation angle.
     *
     * @return rotation angle in degree.
     */
    public double toDouble() {
      return this.angle;
    }
  }

  /**
   * This class is use to storage decode frame's dimension.
   */
  private class Dimension {
    private final int width;
    private final int height;

    /**
     * Constructor.
     *
     * @param width  width
     * @param height height
     */
    public Dimension(final int width, final int height) {
      this.width = width;
      this.height = height;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }
  }
}
