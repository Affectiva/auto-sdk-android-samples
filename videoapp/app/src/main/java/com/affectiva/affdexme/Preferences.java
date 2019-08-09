package com.affectiva.affdexme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Application SharedPreferences storage singleton.
 */
@SuppressWarnings("WeakerAccess")
public final class Preferences {

  private final transient SharedPreferences sharedPreferences;

  private static Preferences instance;

  private final transient SharedPreferences.Editor editor;

  private static final String SHARED_PREF_NAME = "shared_preferences";

  public static final String SELECTED_FRAGMENT = "SELECTED_FRAGMENT";

  public static final String CSV_PATH = "CSV_PATH";

  public static final String CSV_DEFAULT_PATH = "/storage/emulated/0/Download/";

  /**
   * Private constructor to prevent creating multiple instances.
   *
   * @param context - context.
   */
  @SuppressLint("CommitPrefEdits")
  private Preferences(final Context context) {
    sharedPreferences = context.getSharedPreferences(
        SHARED_PREF_NAME,
        Context.MODE_PRIVATE
    );
    editor = sharedPreferences.edit();
  }

  /**
   * Initialize Preferences instance.
   *
   * @param context - application context.
   */
  public static void initialize(final Context context) {
    synchronized (Preferences.class) {
      if (instance == null) {
        instance = new Preferences(context);
      }
    }
  }

  /**
   * Returns instance of Preferences, if it was initialized.
   *
   * @return - Preferences singleton.
   */
  public static Preferences getInstance() {
    synchronized (Preferences.class) {
      if (instance == null) {
        throw new IllegalStateException(Preferences.class.getSimpleName()
            + " is not initialized, call initializeInstance(..) method first.");
      }
    }
    return instance;
  }

  /**
   * Put string value to the SharedPreferences.
   *
   * @param key   - string key.
   * @param value - string value.
   */
  public void setStringValue(final String key, final String value) {
    editor.putString(key, value);
    editor.apply();
  }

  /**
   * Returns string value from the SharedPreferences by it's key.
   *
   * @param key          - key.
   * @param defaultValue - default value.
   * @return entry string value.
   */
  public String getStringValue(final String key, final String defaultValue) {
    return sharedPreferences.getString(key, defaultValue);
  }

  /**
   * Put integer value to the SharedPreferences.
   *
   * @param key   - string key.
   * @param value - integer value.
   */
  public void setIntValue(final String key, final int value) {
    editor.putInt(key, value);
    editor.apply();
  }

  /**
   * Returns integer value from the SharedPreferences by it's key.
   *
   * @param key          - key.
   * @param defaultValue - default value.
   * @return entry integer value.
   */
  public int getIntValue(final String key, final int defaultValue) {
    return sharedPreferences.getInt(key, defaultValue);
  }
}
