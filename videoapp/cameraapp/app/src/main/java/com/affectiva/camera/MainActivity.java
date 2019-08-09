package com.affectiva.camera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.affectiva.vision.Face;
import com.affectiva.vision.Frame;
import com.affectiva.vision.ImageListener;

import java.util.Map;

/**
 * MainActivity class.
 */
@SuppressWarnings(
    {
        "PMD.UnusedPrivateField",
        "PMD.TooManyMethods",
        "PMD.RedundantFieldInitializer"
    }
)
public class MainActivity
    extends AppCompatActivity
    implements ActivityCompat.OnRequestPermissionsResultCallback,
    CameraDetector.CameraEventListener,
    ImageListener {

  private static final int CAMERA_PERMISSIONS_REQUEST = 42;

  private CameraDetector cameraDetector;

  private RelativeLayout mainLayout; //layout, to be resized, containing all UI elements

  private ViewGroup.LayoutParams mainLayoutParams;

  private FacePointsDrawer boundingBoxesAndPointsView;

  // layout used to notify the user that not enough permissions have been granted to use the app
  private LinearLayout permissionsUnavailableLayout;

  private BaseMetricsFragment metricsFragment;

  private SurfaceView displayCameraView;

  private DisplayMetrics displayMetrics;

  private boolean isCameraPermissionGranted;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //To maximize UI space, we declare our app to be full-screen
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.activity_main);
    Preferences.initialize(this);

    initializeUI();

    displayMetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

    checkCameraPermissions();

    initializeCameraDetector();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (metricsFragment == null) {
      initializeFragments();
    }

    if (
        isCameraPermissionGranted
            && this.cameraDetector != null
            && !this.cameraDetector.isRunning()
    ) {
      this.cameraDetector.start();
      boundingBoxesAndPointsView.startDrawThread();
    }
  }

  @Override
  @SuppressWarnings(
      {
          "PMD.DataflowAnomalyAnalysis",
          "PMD.NullAssignment"
      }
  )
  public void onPause() {
    super.onPause();
    clearViews();

    if (this.cameraDetector != null) {
      this.cameraDetector.stop();
    }

    final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    if (metricsFragment != null) {
      fragmentTransaction.remove(metricsFragment).commit();
      metricsFragment = null;
    }
    boundingBoxesAndPointsView.stopDrawThread();
  }

  @Override
  @SuppressWarnings("PMD.NullAssignment")
  protected void onDestroy() {
    super.onDestroy();
    if (cameraDetector != null) {
      cameraDetector.dispose();
      cameraDetector = null;
    }
  }

  /**
   * Initialize UI.
   */
  private void initializeUI() {
    boundingBoxesAndPointsView = findViewById(R.id.drawing_view);

    permissionsUnavailableLayout = findViewById(R.id.permissionsUnavialableLayout);
    permissionsUnavailableLayout.setVisibility(View.GONE);

    mainLayout = findViewById(R.id.main_layout);
    displayCameraView = findViewById(R.id.camera_preview);

    boundingBoxesAndPointsView.setZOrderMediaOverlay(true);
    displayCameraView.setZOrderMediaOverlay(false);

    mainLayoutParams = mainLayout.getLayoutParams();

    final Button retryPermissionsButton = findViewById(R.id.retryPermissionsButton);
    retryPermissionsButton.setOnClickListener(view -> checkCameraPermissions());
  }

  /**
   * Check is the camera permission allowed.
   */
  private void checkCameraPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      isCameraPermissionGranted = true;
    } else {
      ActivityCompat.requestPermissions(
          this,
          new String[] {Manifest.permission.CAMERA},
          CAMERA_PERMISSIONS_REQUEST
      );
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode,
      final @NonNull String[] permissions,
      final @NonNull int[] grantResults) {
    if (requestCode == CAMERA_PERMISSIONS_REQUEST
        && grantResults.length > 0
        && grantResults[0] != PackageManager.PERMISSION_GRANTED
    ) {
      showPermissionExplanationDialog();
    } else {
      isCameraPermissionGranted = true;
      permissionsUnavailableLayout.setVisibility(View.GONE);
    }
  }

  /**
   * Show alert dialog, if user didn't allow camera permission.
   */
  private void showPermissionExplanationDialog() {
    final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    alertDialogBuilder.setTitle(getResources().getString(R.string.insufficient_permissions));

    alertDialogBuilder
        .setMessage(getResources().getString(R.string.permissions_camera_needed_explanation))
        .setCancelable(false)
        .setPositiveButton(getResources().getString(R.string.understood),
            new DialogInterface.OnClickListener() {
              /**
               * Positive button click.
               * @param dialog dialog.
               * @param id button id.
               */
              public void onClick(final DialogInterface dialog, final int id) {
                dialog.cancel();
                permissionsUnavailableLayout.setVisibility(View.VISIBLE);
              }
            });

    final AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }

  /**
   * Initialize CameraDetector.
   */
  private void initializeCameraDetector() {
    cameraDetector = new CameraDetector(
        getApplicationContext(),
        displayCameraView,
        CameraDetector.CameraType.CAMERA_FRONT
    );

    cameraDetector.setOnCameraEventListener(this);
    cameraDetector.setImageListener(this);
  }

  /**
   * Open settings screen.
   *
   * @param view clicked button.
   */
  public void settingsButtonClick(final View view) {
    final Intent intent = new Intent(this, SettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        | Intent.FLAG_ACTIVITY_NEW_TASK
    );
    startActivity(intent);
    finish();
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

  /**
   * Get necessary metrics fragment class depends on selected settings.
   *
   * @return fragment instance.
   */
  private Fragment getFragmentInstance() {
    final String selectedMetricsType = Preferences.getInstance().getStringValue(
        Preferences.SELECTED_FRAGMENT,
        ViewMetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT.toString()
    );

    final ViewMetricsFragmentFactory.FragmentType fragmentType =
        ViewMetricsFragmentFactory.FragmentType.valueOf(selectedMetricsType);

    return ViewMetricsFragmentFactory.getFragment(fragmentType);
  }

  @SuppressWarnings(
      {
          "PMD.AvoidReassigningParameters",
          "PMD.CyclomaticComplexity"
      }
  )
  @Override
  public void onCameraSizeSelected(
      final int cameraFrameWidth,
      final int cameraFrameHeight,
      final Frame.Rotation rotation
  ) {
    final int displayWidth = displayMetrics.widthPixels;
    final int displayHeight = displayMetrics.heightPixels;

    final boolean elementsVisible = cameraFrameWidth != 0
        && cameraFrameHeight != 0
        && displayWidth != 0
        && displayHeight != 0;

    if (elementsVisible) {
      fitCameraFrameToDisplay(cameraFrameWidth, cameraFrameHeight, rotation);
    }
  }

  /**
   * Calculate surface view size bases on camera frame and display ratio.
   *
   * @param cameraFrameWidth  width of image received from camera
   * @param cameraFrameHeight height of image received from camera
   * @param rotation          rotation of an image
   */
  @SuppressWarnings("SuspiciousNameCombination")
  private void fitCameraFrameToDisplay(
      final int cameraFrameWidth,
      final int cameraFrameHeight,
      final Frame.Rotation rotation
  ) {
    final int displayWidth = displayMetrics.widthPixels;
    final int displayHeight = displayMetrics.heightPixels;

    int frameWidth;
    int frameHeight;
    if (isPortrait(rotation)) {
      frameWidth = cameraFrameHeight;
      frameHeight = cameraFrameWidth;
    } else {
      frameWidth = cameraFrameWidth;
      frameHeight = cameraFrameHeight;
    }

    final float displayAspectRatio = getDisplayAspectRatio(rotation, displayWidth, displayHeight);
    final float frameAspectRatio = (float) frameWidth / frameHeight;

    int surfaceWidth;
    int surfaceHeight;

    if (isPortrait(rotation)) {
      if (frameAspectRatio > displayAspectRatio) {
        surfaceWidth = displayWidth;
        surfaceHeight = (int) (surfaceWidth / frameAspectRatio);
      } else {
        surfaceWidth = displayHeight;
        surfaceHeight = (int) (surfaceWidth / frameAspectRatio);
      }
    } else {
      if (frameAspectRatio > displayAspectRatio) {
        surfaceHeight = displayHeight;
        surfaceWidth = (int) (surfaceHeight * frameAspectRatio);
      } else {
        surfaceHeight = displayWidth;
        surfaceWidth = (int) (surfaceHeight * frameAspectRatio);
      }
    }

    boundingBoxesAndPointsView.setFrameScaleAndFrameWidth(
        (float) surfaceWidth / frameWidth,
        frameWidth
    );

    mainLayoutParams.height = surfaceHeight;
    mainLayoutParams.width = surfaceWidth;
    mainLayout.setLayoutParams(mainLayoutParams);
  }

  private float getDisplayAspectRatio(
      final Frame.Rotation rotation,
      final int displayWidth,
      final int displayHeight
  ) {
    return isPortrait(rotation)
        ? (float) displayWidth / displayHeight
        : (float) displayHeight / displayWidth;
  }

  private boolean isPortrait(final Frame.Rotation rotation) {
    return rotation == Frame.Rotation.CW_270 || rotation == Frame.Rotation.CW_90;
  }

  @Override
  public void onImageResults(final Map<Integer, Face> faces, final Frame frame) {
    if (faces.isEmpty()) {
      clearViews();
    } else {
      updateViews(faces);
    }
  }

  @SuppressWarnings(
      {
          "ConstantConditions",
          "SuspiciousMethodCalls"
      }
  )
  private void updateViews(final Map<Integer, Face> faces) {
    if (faces.isEmpty() || metricsFragment == null) {
      clearViews();
    } else {
      metricsFragment.updateMetrics(faces.get(faces.keySet().toArray()[0]));
    }
    updateDrawingView(faces);
  }

  @Override
  public void onImageCapture(final Frame frame) {
    // no processing required now
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  private void updateDrawingView(final Map<Integer, Face> facesMap) {
    boundingBoxesAndPointsView.drawPointsBoundingBoxes(facesMap);
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void clearViews() {
    if (metricsFragment != null) {
      metricsFragment.setMetricsToZero();
    }

    if (boundingBoxesAndPointsView != null) {
      boundingBoxesAndPointsView.clearBoundingBoxes();
    }
  }
}
