package com.affectiva.affdexme;

import android.annotation.SuppressLint;
import android.util.Log;

import com.affectiva.vision.Face;
import com.affectiva.vision.Frame;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * Helper class for writing of frames data to CSV file.
 */
@SuppressWarnings("WeakerAccess")
class CsvWriter {

  private static final Integer NUMBER_OF_COLUMNS = 34;

  private static final String TAG = "CsvWriter";

  private File reportFile;

  private String[] reportFields;

  private ICsvMapWriter mapWriter;

  private CellProcessor[] processors;

  /**
   * Constructor.
   *
   * @param reportFilePath report file paths.
   */
  public CsvWriter(final String reportFilePath) {
    initializeReportFile(reportFilePath);
  }

  /**
   * Initialize directory and file for the report output.
   */
  @SuppressWarnings(
      {
          "PMD.NullAssignment",
          "PMD.AvoidFileStream"
      }
  )
  private void initializeReportFile(final String reportFilePath) {
    reportFile = new File(reportFilePath);
    mapWriter = null;

    reportFields = new String[] {
        "TimeStamp",
        "faceId",
        "upperLeftX",
        "upperLeftY",
        "lowerRightX",
        "lowerRightY",
        "confidence",
        "interocularDistance",
        "pitch",
        "yaw",
        "roll",
        "joy",
        "anger",
        "surprise",
        "valence",
        "sadness",
        "neutral",
        "smile",
        "browRaise",
        "browFurrow",
        "noseWrinkle",
        "upperLipRaise",
        "mouthOpen",
        "eyeClosure",
        "cheekRaise",
        "yawn",
        "blink",
        "blinkRate",
        "eyeWiden",
        "innerBrowRaise",
        "lipCornerDepressor",
        "mood",
        "dominantEmotion",
        "dominantEmotionConfidence"
    };

    try {
      mapWriter = new CsvMapWriter(
          new FileWriter(reportFile),
          CsvPreference.STANDARD_PREFERENCE
      );

      mapWriter.writeHeader(reportFields);

    } catch (IOException e) {
      Log.e(TAG, e.getMessage());
    }

    processors = getReportProcessors();
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private CellProcessor[] getReportProcessors() {
    final List<CellProcessor> result = new ArrayList<>();
    for (int i = 1; i <= NUMBER_OF_COLUMNS; i++) {
      result.add(new Optional());
    }

    return result.toArray(new CellProcessor[0]);
  }

  /**
   * Adds frame data as CSV file row.
   *
   * @param faces faces detected for frame.
   * @param frame frame information.
   */
  @SuppressLint("DefaultLocale")
  @SuppressWarnings(
      {
          "PMD.UseCollectionIsEmpty",
          "PMD.DataflowAnomalyAnalysis",
          "PMD.AvoidSynchronizedAtMethodLevel",
          "PMD.ExcessiveMethodLength",
          "ConstantConditions"
      }
  )
  public synchronized void addFrameInfo(final Map<Integer, Face> faces, final Frame frame) {
    final Map<String, Object> reportRow = new ConcurrentHashMap<>();
    final DecimalFormat decimalFormat = new DecimalFormat("#.####");

    if (!faces.isEmpty() && faces.size() > 0) {
      for (final Map.Entry<Integer, Face> entry : faces.entrySet()) {
        final Face face = entry.getValue();
        reportRow.put(reportFields[0], frame.getTimeStamp());

        reportRow.put(reportFields[1], face.getId());

        reportRow.put(reportFields[2], Math.round(face.getBoundingBox().get(0).getX()));
        reportRow.put(reportFields[3], Math.round(face.getBoundingBox().get(0).getY()));
        reportRow.put(reportFields[4], Math.round(face.getBoundingBox().get(1).getX()));
        reportRow.put(reportFields[5], Math.round(face.getBoundingBox().get(1).getY()));

        reportRow.put(reportFields[6], Float.valueOf(decimalFormat.format(face.getConfidence())));

        reportRow.put(
            reportFields[7],
            Float.valueOf(decimalFormat.format(
                face.getMeasurements().get(Face.Measurement.INTEROCULAR_DISTANCE)
                )
            )
        );
        reportRow.put(
            reportFields[8],
            Float.valueOf(decimalFormat.format(
                face.getMeasurements().get(Face.Measurement.PITCH)
                )
            )
        );
        reportRow.put(
            reportFields[9],
            Float.valueOf(decimalFormat.format(
                face.getMeasurements().get(Face.Measurement.YAW)
                )
            )
        );
        reportRow.put(
            reportFields[10],
            Float.valueOf(decimalFormat.format(
                face.getMeasurements().get(Face.Measurement.ROLL)
                )
            )
        );

        reportRow.put(
            reportFields[11],
            Float.valueOf(decimalFormat.format(
                face.getEmotions().get(Face.Emotion.JOY)
                )
            )
        );
        reportRow.put(
            reportFields[12],
            Float.valueOf(decimalFormat.format(
                face.getEmotions().get(Face.Emotion.ANGER)
                )
            )
        );
        reportRow.put(
            reportFields[13],
            Float.valueOf(decimalFormat.format(
                face.getEmotions().get(Face.Emotion.SURPRISE)
                )
            )
        );
        reportRow.put(
            reportFields[14],
            Float.valueOf(decimalFormat.format(
                face.getEmotions().get(Face.Emotion.VALENCE)
                )
            )
        );

        reportRow.put(
            reportFields[15],
            String.format("%.4f", face.getEmotions().get(Face.Emotion.SADNESS))
        );
        reportRow.put(
            reportFields[16],
            Float.valueOf(decimalFormat.format(face.getEmotions().get(Face.Emotion.NEUTRAL)
                )
            )
        );

        reportRow.put(
            reportFields[17],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.SMILE)
                )
            )
        );
        reportRow.put(
            reportFields[18],
            Float.valueOf(decimalFormat.format(face.getExpressions().get(Face.Expression.BROW_RAISE)
                )
            )
        );
        reportRow.put(
            reportFields[19],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.BROW_FURROW)
                )
            )
        );
        reportRow.put(
            reportFields[20],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.NOSE_WRINKLE)
                )
            )
        );
        reportRow.put(
            reportFields[21],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.UPPER_LIP_RAISE)
                )
            )
        );
        reportRow.put(
            reportFields[22],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.MOUTH_OPEN)
                )
            )
        );
        reportRow.put(
            reportFields[23],
            String.format("%.4f", face.getExpressions().get(Face.Expression.EYE_CLOSURE))
        );
        reportRow.put(
            reportFields[24],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.CHEEK_RAISE)
                )
            )
        );
        reportRow.put(
            reportFields[25],
            Float.valueOf(decimalFormat.format(face.getExpressions().get(Face.Expression.YAWN))));
        reportRow.put(
            reportFields[26],
            Float.valueOf(decimalFormat.format(face.getExpressions().get(Face.Expression.BLINK))));
        reportRow.put(
            reportFields[27],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.BLINK_RATE)
                )
            )
        );
        reportRow.put(
            reportFields[28],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.EYE_WIDEN)
                )
            )
        );
        reportRow.put(
            reportFields[29],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.INNER_BROW_RAISE)
                )
            )
        );
        reportRow.put(
            reportFields[30],
            Float.valueOf(decimalFormat.format(
                face.getExpressions().get(Face.Expression.LIP_CORNER_DEPRESSOR)
                )
            )
        );

        reportRow.put(reportFields[31], face.getMood());
        reportRow.put(reportFields[32], face.getDominantEmotion().getDominantEmotion());

        reportRow.put(
            reportFields[33],
            Float.valueOf(decimalFormat.format(face.getDominantEmotion().getConfidence())));
      }
    } else {
      reportRow.put(reportFields[0], frame.getTimeStamp());
      for (int i = 1; i <= NUMBER_OF_COLUMNS - 1; i++) {
        reportRow.put(reportFields[i], "n/a");
      }
    }

    try {
      mapWriter.write(reportRow, reportFields, processors);
    } catch (IOException e) {
      Log.e(TAG, e.getMessage());
    }
  }

  /**
   * Returns created report file.
   *
   * @return file.
   */
  public File getReportFile() {
    return reportFile;
  }

  /**
   * Close report file.
   */
  public void close() {
    try {
      mapWriter.flush();
      mapWriter.close();
      Log.i(TAG, "Report file is closed");
    } catch (IOException e) {
      Log.e(TAG, e.getMessage());
    }
  }
}
