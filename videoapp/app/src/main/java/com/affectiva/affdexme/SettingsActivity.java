package com.affectiva.affdexme;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import lib.folderpicker.FolderPicker;

/**
 * Settings activity.
 */
@SuppressWarnings("PMD.TooManyMethods")
public class SettingsActivity extends AppCompatActivity implements
    RadioGroup.OnCheckedChangeListener {

  private static final int PICK_FOLDER_REQUEST_CODE = 46;

  private RadioGroup metricsRadioGroup;

  private TextView csvLocationPath;

  private static final String DATA = "data";

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings_activity);

    initializeUi();
  }

  /**
   * Initialize UI.
   */
  private void initializeUi() {
    initializeChangeCsvPathButton();

    metricsRadioGroup = findViewById(R.id.metrics_radioGroup);
    metricsRadioGroup.setOnCheckedChangeListener(this);

    final String csvLocationPath = Preferences.getInstance().getStringValue(
        Preferences.CSV_PATH,
        "/storage/emulated/0/Download/");

    this.csvLocationPath = findViewById(R.id.csv_location_path);
    this.csvLocationPath.setText(csvLocationPath);

    checkSelectedMetrics();
  }

  /**
   * Changes path to the generating CSV metrics file.
   */
  private void initializeChangeCsvPathButton() {
    final TextView changeCsvLocation = findViewById(R.id.change_csv_location);
    changeCsvLocation.setOnClickListener(v -> {
      final Intent intent = new Intent(this, FolderPicker.class);
      startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE);
    });
  }

  @Override
  public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }

    if (requestCode == PICK_FOLDER_REQUEST_CODE) {
      final String folderLocation = data.getExtras().getString(DATA);
      Preferences.getInstance().setStringValue(Preferences.CSV_PATH, folderLocation);
      csvLocationPath.setText(folderLocation);
    }
  }

  /**
   * Check selected previously radioButton.
   */
  private void checkSelectedMetrics() {
    final String selectedRadioButton = Preferences.getInstance().getStringValue(
        Preferences.SELECTED_FRAGMENT,
        MetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT.toString()
    );

    final MetricsFragmentFactory.FragmentType fragmentType =
        MetricsFragmentFactory.FragmentType.valueOf(selectedRadioButton);

    metricsRadioGroup.check(MetricsFragmentFactory.getFragmentButtonId(fragmentType));
  }

  /**
   * On back pressed event. Returns to the previous screen.
   *
   * @param view clicked view.
   */
  public void onBackPressed(final View view) {
    this.onBackPressed();
    finish();
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  @Override
  public void onCheckedChanged(final RadioGroup group, final int checkedId) {
    MetricsFragmentFactory.FragmentType fragmentType =
        MetricsFragmentFactory.FragmentType.EMOTIONS_FRAGMENT;

    switch (checkedId) {
      case R.id.measurements_button:
        fragmentType = MetricsFragmentFactory.FragmentType.MEASUREMENTS_FRAGMENT;
        break;
      case R.id.expressions_button:
        fragmentType = MetricsFragmentFactory.FragmentType.EXPRESSIONS_FRAGMENT;
        break;
      default:
    }

    Preferences.getInstance().setStringValue(
        Preferences.SELECTED_FRAGMENT,
        fragmentType.toString()
    );
  }
}
