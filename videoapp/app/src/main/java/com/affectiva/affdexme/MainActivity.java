package com.affectiva.affdexme;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * MainActivity class.
 */
public class MainActivity extends AppCompatActivity
    implements ActivityCompat.OnRequestPermissionsResultCallback {

  private static final int REQUEST_TAKE_GALLERY_VIDEO = 1;

  private static final int STORAGE_PERMISSION_REQUEST = 123;

  private boolean isPermissionGranted;

  // layout used to notify the user that not enough permissions have been granted to use the app
  private LinearLayout permissionsUnavailableLayout;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    Preferences.initialize(this);

    initPermissionUnavailableLayout();

    checkPermission();
  }

  private void initPermissionUnavailableLayout() {
    permissionsUnavailableLayout = findViewById(R.id.permissionsUnavialableLayout);
    permissionsUnavailableLayout.setVisibility(View.GONE);

    final Button retryPermissionsButton = findViewById(R.id.retryPermissionsButton);
    retryPermissionsButton.setOnClickListener(view -> checkPermission());
  }


  /**
   * Start upload video intent.
   *
   * @param view clicked button.
   */
  public void uploadVideoButtonClick(final View view) {
    uploadVideo();
  }

  /**
   * Upload video from the external storage.
   */
  private void uploadVideo() {
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

  /**
   * Open settings screen.
   *
   * @param view clicked view.
   */
  public void settingsButtonClick(final View view) {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  @Override
  public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }

    if (requestCode == REQUEST_TAKE_GALLERY_VIDEO && isPermissionGranted) {
      final Uri myVideoUri = data.getData();

      final Intent startVideoIntent = new Intent(this, VideoProcessingActivity.class);
      startVideoIntent.setData(myVideoUri);
      startActivity(startVideoIntent);
    }
  }

  private void checkPermission() {
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED) {
      isPermissionGranted = true;
    } else {
      ActivityCompat.requestPermissions(this,
          new String[] {
              Manifest.permission.WRITE_EXTERNAL_STORAGE
          },
          STORAGE_PERMISSION_REQUEST
      );
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode,
      final @NonNull String[] permissions,
      final @NonNull int[] grantResults) {
    if (requestCode == STORAGE_PERMISSION_REQUEST
        && grantResults.length > 0
        && grantResults[0] != PackageManager.PERMISSION_GRANTED
    ) {
      showPermissionExplanationDialog();
    } else {
      isPermissionGranted = true;
      permissionsUnavailableLayout.setVisibility(View.GONE);
    }
  }

  /**
   * Show alert dialog, if user didn't allow storage permission.
   */
  private void showPermissionExplanationDialog() {
    final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    alertDialogBuilder.setTitle(getResources().getString(R.string.insufficient_permissions));

    alertDialogBuilder
        .setMessage(getResources().getString(R.string.permissions_storage_needed_explanation))
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
}
