package com.affectiva.affdexme;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.affectiva.vision.Face;
import com.affectiva.vision.Frame;

import java.util.Map;

/**
 * VideoProcessingActivity class.
 */
@SuppressWarnings(
    {
        "PMD.TooManyMethods",
        "PMD.DataflowAnomalyAnalysis"
    }
)
public class VideoProcessingActivity extends AppCompatActivity {

  private static final int REQUEST_TAKE_GALLERY_VIDEO = 1;

  private static final int CANCEL_BUTTON_DISPLAY_DURATION = 2000;

  private static final String REPORT_POSTFIX = "-report.csv";

  private String videoPath;

  private Handler handler;

  private FrameDrawer frameDrawer;

  private CsvWriter csvWriter;

  private VideoProcessingThread videoProcessingThread;

  private ProgressBar videoSeekBar;

  private Dialog notificationDialog;

  private BaseMetricsFragment metricsFragment;

  private Button stopButton;

  private boolean isUploadVideoInProgress;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.video_activity);
    frameDrawer = findViewById(R.id.baseSurface);
    videoPath = getRealPathFromUri(getIntent().getData());

    initializeUI();
    initializeFragments();
  }

  @Override
  protected void onResume() {
    super.onResume();

    handler = new Handler();

    if (videoProcessingThread == null) {
      startVideoProcessing();
    }

    if (metricsFragment == null) {
      initializeFragments();
    }

    // start the drawing
    frameDrawer.startDrawThread();
    displayStopButton();
    isUploadVideoInProgress = false;
  }

  @Override
  @SuppressWarnings("PMD.NullAssignment")
  protected void onPause() {
    super.onPause();

    // stop the drawing to save cpu time
    frameDrawer.stopDrawThread();

    stopVideoProcessThread();

    removeFragment();

    handler.removeCallbacksAndMessages(null);
    handler = null;

    if (notificationDialog != null) {
      notificationDialog.dismiss();
      notificationDialog = null;
    }

    if (!isUploadVideoInProgress) {
      finish();
    }
  }

  @Override
  public void onBackPressed() {
    if (videoProcessingThread.isAlive()) {
      videoProcessingThread.stopProcessing();

      showVideoDialog(
          false,
          Preferences.getInstance().getStringValue(
              Preferences.CSV_PATH,
              Preferences.CSV_DEFAULT_PATH
          )
      );
    }
  }

  /**
   * Initialize views.
   */
  private void initializeUI() {
    videoSeekBar = findViewById(R.id.video_seekBar);
    videoSeekBar.setEnabled(false);
    stopButton = findViewById(R.id.stop_video_button);
  }

  /**
   * Initialize metrics fragments.
   */
  private void initializeFragments() {
    final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    metricsFragment = (BaseMetricsFragment) getFragmentInstance();

    fragmentTransaction.add(
        R.id.fragment_container,
        metricsFragment,
        metricsFragment.toString()
    );

    fragmentTransaction.commit();
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void stopVideoProcessThread() {
    if (videoProcessingThread != null) {
      if (videoProcessingThread.isAlive()) {
        videoProcessingThread.stopProcessing();
      }
      videoProcessingThread.interrupt();
      videoProcessingThread = null;
    }
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void removeFragment() {
    final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    if (metricsFragment != null) {
      fragmentTransaction.remove(metricsFragment).commit();
      metricsFragment = null;
    }
  }

  /**
   * Get necessary metrics fragment class depends on selected settings.
   *
   * @return fragment instance.
   */
  private Fragment getFragmentInstance() {
    final String selectedMetricsType = Preferences.getInstance().getStringValue(
        Preferences.SELECTED_FRAGMENT,
        MetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT.toString()
    );

    final MetricsFragmentFactory.FragmentType fragmentType =
        MetricsFragmentFactory.FragmentType.valueOf(selectedMetricsType);

    return MetricsFragmentFactory.getFragment(fragmentType);
  }

  private void startVideoProcessing() {
    csvWriter = new CsvWriter(getReportFilePath());
    videoProcessingThread = new VideoProcessingThread(
        videoPath,
        getBaseContext()
    );

    videoProcessingThread.setListener(new VideoProcessingThread.VideoProcessingThreadListener() {
      @Override
      public void onImageResults(final Map<Integer, Face> faces, final Frame frame) {
        drawFrameAndBoundingBoxes(frame, faces);
        addDataToCsv(faces, frame);

        if (faces.isEmpty()) {
          setMetricsToZero();
        } else {
          final Face face = faces.get(faces.keySet().toArray()[0]);
          if (metricsFragment != null) {
            metricsFragment.updateMetrics(face);
          }
        }
      }

      @Override
      public void onProcessingFinished() {
        csvWriter.close();
        videoSeekBar.setProgress(100);
        showCsvReportSuccessDialog();
      }

      @Override
      public void onProgressUpdate(final double percent) {
        runOnUiThread(
            () -> videoSeekBar.setProgress((int) percent)
        );
      }
    });

    handler.post(() -> videoProcessingThread.start());
  }

  private String getReportFilePath() {
    final String nameWithoutExtension = videoPath.substring(0, videoPath.lastIndexOf('.'));

    final String customCsvPath = Preferences.getInstance().getStringValue(
        Preferences.CSV_PATH, Preferences.CSV_DEFAULT_PATH);

    final String videoName =
        nameWithoutExtension.substring(nameWithoutExtension.lastIndexOf('/') + 1);

    return customCsvPath + "/" + videoName + REPORT_POSTFIX;
  }

  private void showCsvReportSuccessDialog() {
    runOnUiThread(
        () -> showVideoDialog(
            true,
            Preferences.getInstance().getStringValue(
                Preferences.CSV_PATH,
                Preferences.CSV_DEFAULT_PATH
            )
        )
    );
  }

  private void drawFrameAndBoundingBoxes(final Frame frame, final Map<Integer, Face> faces) {
    frameDrawer.drawFrameAndBoundingBoxes(frame, faces);
  }

  private void addDataToCsv(final Map<Integer, Face> faces, final Frame frame) {
    csvWriter.addFrameInfo(faces, frame);
  }

  private void setMetricsToZero() {
    if (metricsFragment != null) {
      metricsFragment.setMetricsToZero();
    }
  }

  /**
   * Click on cancel video processing button.
   *
   * @param view clicked button.
   */
  public void onStopVideoButtonClicked(final View view) {
    if (videoProcessingThread.isAlive()) {
      videoProcessingThread.stopProcessing();
      csvWriter.close();

      showVideoDialog(
          false,
          Preferences.getInstance().getStringValue(
              Preferences.CSV_PATH,
              Preferences.CSV_DEFAULT_PATH
          )
      );
    }
  }

  /**
   * Display cancel video progress button on touch.
   */
  @SuppressLint("ClickableViewAccessibility")
  private void displayStopButton() {
    frameDrawer.setOnTouchListener((v, event) -> {
      stopButton.setVisibility(View.VISIBLE);

      new Handler().postDelayed(() ->
          stopButton.setVisibility(View.GONE), CANCEL_BUTTON_DISPLAY_DURATION);
      return true;
    });
  }

  private String getRealPathFromUri(final Uri uri) {
    final String[] projection = {MediaStore.Images.Media.DATA};
    @SuppressWarnings("deprecation") final Cursor cursor = managedQuery(
        uri,
        projection,
        null,
        null,
        null
    );
    final int columnIndex = cursor
        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
    cursor.moveToFirst();
    return cursor.getString(columnIndex);
  }

  /**
   * Show alert notice_dialog if video processing was canceled.
   *
   * @param isVideoFinished boolean to differentiate notificationDialog type.
   */
  public void showVideoDialog(final boolean isVideoFinished, final String reportFilePath) {
    notificationDialog = new Dialog(this);

    initializeDialogViews(notificationDialog, reportFilePath, isVideoFinished);

    notificationDialog.show();
  }

  /**
   * Initialize notificationDialog view.
   *
   * @param dialog          cancelVideoDialog.
   * @param reportFilePath  report file path
   * @param isVideoFinished boolean to differentiate notificationDialog type.
   */
  private void initializeDialogViews(
      final Dialog dialog,
      final String reportFilePath,
      final boolean isVideoFinished
  ) {
    dialog.setContentView(R.layout.notice_dialog);

    final TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
    dialogTitle.setText(R.string.video_partially_processed);

    final TextView dialogMessage = dialog.findViewById(R.id.dialog_message);
    dialogMessage.setText(generateCsvPathMessage(reportFilePath));

    dialog.setCancelable(false);
    dialog.setCanceledOnTouchOutside(false);

    if (isVideoFinished) {
      dialogTitle.setText(R.string.video_processed);
      initializeUploadNewVideoButton(dialog);
    }
  }

  private void initializeUploadNewVideoButton(final Dialog dialog) {
    final Button uploadVideoButton = dialog.findViewById(R.id.upload_new_video_button);
    uploadVideoButton.setVisibility(View.VISIBLE);

    uploadVideoButton.setOnClickListener(v -> {
      uploadVideo();
    });
  }

  /**
   * Upload video from the external storage.
   */
  private void uploadVideo() {
    isUploadVideoInProgress = true;
    final Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setDataAndType(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        "video/*"
    );

    startActivityForResult(
        Intent.createChooser(
            intent,
            "Select Video"
        ),
        REQUEST_TAKE_GALLERY_VIDEO
    );
  }

  @Override
  public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if (resultCode == RESULT_OK && requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
      this.videoPath = getRealPathFromUri(data.getData());
    }
  }

  /**
   * Generate message with path to CSV file.
   *
   * @return path string.
   */
  private String generateCsvPathMessage(final String reportFilePath) {
    return "Video file has been processed and the data is stored in the file "
        + reportFilePath;
  }

  /**
   * Dismiss notificationDialog.
   *
   * @param view clicked view.
   */
  public void onDismissClicked(final View view) {
    notificationDialog.dismiss();
    finish();
  }
}
