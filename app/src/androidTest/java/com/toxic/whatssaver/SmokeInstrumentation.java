package com.toxic.whatssaver;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.core.content.FileProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/** Integration checks run in an emulator; no testing hooks ship in the application. */
public final class SmokeInstrumentation extends Instrumentation {
  private final StringBuilder report = new StringBuilder();
  private SaverApp app;
  private ViewerActivity viewer;
  private Bundle options;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    options = args == null ? new Bundle() : args;
    start();
  }

  private void check(boolean okay, String message) {
    if (!okay) throw new AssertionError(message);
    report.append("PASS ").append(message).append('\n');
  }

  private void pause(long ms) {
    SystemClock.sleep(ms);
  }

  private <T> T ui(Callable<T> call) throws Exception {
    Object[] result = new Object[2];
    runOnMainSync(
        () -> {
          try {
            result[0] = call.call();
          } catch (Throwable e) {
            result[1] = e;
          }
        });
    if (result[1] != null) throw new RuntimeException((Throwable) result[1]);
    return (T) result[0];
  }

  private Object field(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private void waitFor(Callable<Boolean> condition, String message) throws Exception {
    for (int i = 0; i < 600; i++) {
      if (condition.call()) {
        check(true, message);
        return;
      }
      pause(100);
    }
    check(false, message);
  }

  private void event(GestureStage stage, long down, long now, int action, float x, float y)
      throws Exception {
    ui(
        () -> {
          MotionEvent e = MotionEvent.obtain(down, now, action, x, y, 0);
          stage.dispatchTouchEvent(e);
          e.recycle();
          return null;
        });
  }

  private void doubleTap(GestureStage stage, float x, float y) throws Exception {
    long t = SystemClock.uptimeMillis();
    event(stage, t, t, MotionEvent.ACTION_DOWN, x, y);
    event(stage, t, t + 35, MotionEvent.ACTION_UP, x, y);
    event(stage, t + 110, t + 110, MotionEvent.ACTION_DOWN, x, y);
    event(stage, t + 110, t + 145, MotionEvent.ACTION_UP, x, y);
    pause(400);
  }

  private void swipe(GestureStage stage, float x, float y, float toX, float toY) throws Exception {
    long t = SystemClock.uptimeMillis();
    event(stage, t, t, MotionEvent.ACTION_DOWN, x, y);
    for (int i = 1; i <= 8; i++)
      event(
          stage,
          t,
          t + i * 35,
          MotionEvent.ACTION_MOVE,
          x + (toX - x) * i / 8,
          y + (toY - y) * i / 8);
    event(stage, t, t + 300, MotionEvent.ACTION_UP, toX, toY);
    pause(450);
  }

  private void pinch(GestureStage stage) throws Exception {
    ui(
        () -> {
          long t = SystemClock.uptimeMillis();
          float cx = stage.getWidth() / 2f, cy = stage.getHeight() / 2f;
          MotionEvent.PointerProperties[] props = {
            new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()
          };
          MotionEvent.PointerCoords[] coords = {
            new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()
          };
          for (int i = 0; i < 2; i++) {
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i].pressure = 1;
            coords[i].size = 1;
            coords[i].y = cy;
          }
          coords[0].x = cx - 50;
          coords[1].x = cx + 50;
          for (int step = -2; step <= 10; step++) {
            int action, count;
            if (step == -2) {
              action = MotionEvent.ACTION_DOWN;
              count = 1;
            } else if (step == -1) {
              action =
                  MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
              count = 2;
            } else {
              action = MotionEvent.ACTION_MOVE;
              count = 2;
              coords[0].x = cx - 50 - step * 11;
              coords[1].x = cx + 50 + step * 11;
            }
            MotionEvent e =
                MotionEvent.obtain(
                    t,
                    t + (step + 2) * 35,
                    action,
                    count,
                    props,
                    coords,
                    0,
                    0,
                    1,
                    1,
                    0,
                    0,
                    android.view.InputDevice.SOURCE_TOUCHSCREEN,
                    0);
            stage.dispatchTouchEvent(e);
            e.recycle();
          }
          MotionEvent e =
              MotionEvent.obtain(
                  t,
                  t + 500,
                  MotionEvent.ACTION_CANCEL,
                  1,
                  props,
                  coords,
                  0,
                  0,
                  1,
                  1,
                  0,
                  0,
                  android.view.InputDevice.SOURCE_TOUCHSCREEN,
                  0);
          stage.dispatchTouchEvent(e);
          e.recycle();
          return null;
        });
    pause(900);
  }

  @Override
  public void onStart() {
    waitForIdleSync();
    if ("interface".equals(options.getString("suite"))) {
      new InterfaceChecks(this).run();
      return;
    }
    Bundle result = new Bundle();
    if ("storage".equals(options.getString("suite"))) {
      storageChecks(result);
      return;
    }
    List<MediaItem> cleanup = new ArrayList<>();
    int oldVolume = -1;
    try {
      app = (SaverApp) getTargetContext().getApplicationContext();
      File folder = new File(app.getCacheDir(), "shared");
      folder.mkdirs();
      File picture = new File(folder, "qa-image.jpg"), movie = new File(folder, "qa-video.mp4");
      Bitmap bitmap = Bitmap.createBitmap(640, 960, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.drawColor(Color.rgb(34, 91, 67));
      try (FileOutputStream out = new FileOutputStream(picture)) {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
      }
      bitmap.recycle();
      try (InputStream in = getContext().getAssets().open("qa-video.mp4");
          FileOutputStream out = new FileOutputStream(movie)) {
        StatusRepository.copy(in, out);
      }
      MediaItem photo =
          new MediaItem(
              FileProvider.getUriForFile(app, app.getPackageName() + ".files", picture),
              picture.getName(),
              "image/jpeg",
              System.currentTimeMillis(),
              picture.length(),
              "qa_image_0000000000000000",
              false);
      MediaItem video =
          new MediaItem(
              FileProvider.getUriForFile(app, app.getPackageName() + ".files", movie),
              movie.getName(),
              "video/mp4",
              System.currentTimeMillis(),
              movie.length(),
              "qa_video_0000000000000000",
              false);
      MediaItem savedPhoto = app.repository.save(photo),
          savedAgain = app.repository.save(photo),
          savedVideo = app.repository.save(video);
      cleanup.add(savedPhoto);
      cleanup.add(savedVideo);
      check(
          savedPhoto.uri.equals(savedAgain.uri), "duplicate save reuses the existing gallery file");
      check(app.repository.saved().size() >= 2, "image and video persisted in Saved database");
      try (InputStream in = app.getContentResolver().openInputStream(savedPhoto.uri)) {
        check(in != null && in.read() != -1, "exported media is readable");
      }
      try (InputStream in =
          app.getContentResolver().openInputStream(app.repository.shareUri(photo))) {
        check(in != null && in.read() != -1, "share content URI is readable");
      }
      String session = app.repository.writeSession(Arrays.asList(photo, video));
      check(
          app.repository.readSession(session).size() == 2,
          "viewer session survives reconstruction");
      Intent intent =
          new Intent(app, ViewerActivity.class)
              .putExtra("session", session)
              .putExtra("index", 0)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      viewer = (ViewerActivity) startActivitySync(intent);
      waitFor(
          () -> ui(() -> field(viewer, "imageBitmap") != null),
          "full image decoded in internal viewer");
      GestureStage stage = (GestureStage) field(viewer, "stage");
      ui(
          () -> {
            android.view.WindowManager.LayoutParams attributes = viewer.getWindow().getAttributes();
            attributes.screenBrightness = .3f;
            viewer.getWindow().setAttributes(attributes);
            return null;
          });
      swipe(
          stage,
          stage.getWidth() * .25f,
          stage.getHeight() * .65f,
          stage.getWidth() * .25f,
          stage.getHeight() * .35f);
      float imageBrightness = ui(() -> viewer.getWindow().getAttributes().screenBrightness);
      check(imageBrightness > .3f, "left vertical image gesture increases brightness");
      swipe(
          stage,
          stage.getWidth() * .25f,
          stage.getHeight() * .35f,
          stage.getWidth() * .25f,
          stage.getHeight() * .65f);
      check(
          ui(() -> viewer.getWindow().getAttributes().screenBrightness < imageBrightness),
          "left vertical image gesture decreases brightness");
      doubleTap(stage, stage.getWidth() / 2f, stage.getHeight() / 2f);
      check(ui(() -> stage.zoom() > 2), "image double tap zooms");
      doubleTap(stage, stage.getWidth() / 2f, stage.getHeight() / 2f);
      check(ui(() -> stage.zoom() == 1), "image double tap restores fit");
      pinch(stage);
      check(
          ui(() -> stage.zoom() > 1.5f),
          "two pointer pinch zooms the media (scale=" + ui(stage::zoom) + ")");
      ui(
          () -> {
            stage.reset();
            return null;
          });
      swipe(
          stage,
          stage.getWidth() * .8f,
          stage.getHeight() / 2f,
          stage.getWidth() * .15f,
          stage.getHeight() / 2f);
      waitFor(
          () ->
              ui(
                  () ->
                      field(viewer, "player") != null
                          && ((ExoPlayer) field(viewer, "player")).getPlaybackState()
                              == Player.STATE_READY),
          "horizontal swipe navigates and video prepares");
      ExoPlayer player = (ExoPlayer) field(viewer, "player");
      check(ui(() -> player.getDuration() > 15000), "video duration is available");
      ui(
          () -> {
            player.pause();
            player.seekTo(10000);
            return null;
          });
      pause(500);
      doubleTap(stage, stage.getWidth() * .12f, stage.getHeight() * .45f);
      check(
          ui(() -> Math.abs(player.getCurrentPosition() - 5000) < 500),
          "left video edge double tap seeks back 5s");
      doubleTap(stage, stage.getWidth() * .88f, stage.getHeight() * .45f);
      check(
          ui(() -> Math.abs(player.getCurrentPosition() - 10000) < 500),
          "right video edge double tap seeks forward 5s");
      doubleTap(stage, stage.getWidth() / 2f, stage.getHeight() / 2f);
      check(ui(() -> stage.zoom() > 2), "video center double tap zooms TextureView");
      doubleTap(stage, stage.getWidth() * .12f, stage.getHeight() * .45f);
      check(
          ui(() -> stage.zoom() > 2 && Math.abs(player.getCurrentPosition() - 5000) < 500),
          "left video edge seeks 5s while zoomed");
      doubleTap(stage, stage.getWidth() * .88f, stage.getHeight() * .45f);
      check(
          ui(() -> stage.zoom() > 2 && Math.abs(player.getCurrentPosition() - 10000) < 500),
          "right video edge seeks 5s while zoomed");
      ui(
          () -> {
            stage.reset();
            return null;
          });
      ui(
          () -> {
            android.view.WindowManager.LayoutParams a = viewer.getWindow().getAttributes();
            a.screenBrightness = .4f;
            viewer.getWindow().setAttributes(a);
            return null;
          });
      swipe(
          stage,
          stage.getWidth() * .2f,
          stage.getHeight() * .7f,
          stage.getWidth() * .2f,
          stage.getHeight() * .4f);
      check(
          ui(() -> viewer.getWindow().getAttributes().screenBrightness > .7f),
          "left vertical gesture increases window brightness");
      AudioManager audio =
          (AudioManager) app.getSystemService(android.content.Context.AUDIO_SERVICE);
      oldVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
      int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, max / 3), 0);
      int before = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
      swipe(
          stage,
          stage.getWidth() * .8f,
          stage.getHeight() * .7f,
          stage.getWidth() * .8f,
          stage.getHeight() * .4f);
      check(
          audio.getStreamVolume(AudioManager.STREAM_MUSIC) > before,
          "right vertical gesture increases media volume");
      ui(
          () -> {
            player.play();
            return null;
          });
      pause(300);
      ui(
          () -> {
            viewer.onPause();
            return null;
          });
      check(
          ui(() -> !player.getPlayWhenReady()), "video pauses when the activity loses foreground");
      ui(
          () -> {
            viewer.onResume();
            return null;
          });
      ui(
          () -> {
            viewer.finish();
            return null;
          });
      pause(300);
      viewer = null;
      for (MediaItem item : cleanup) app.repository.deleteSaved(item);
      cleanup.clear();
      check(picture.isFile() && movie.isFile(), "deleting a saved copy preserves source media");
      check(
          app.repository.saved().stream().noneMatch(i -> i.key.startsWith("qa_")),
          "deleted downloads are removed from Saved");
      result.putString("stream", report.toString());
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "FAIL " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (viewer != null)
        try {
          runOnMainSync(viewer::finish);
        } catch (Exception ignored) {
        }
      if (app != null) {
        for (MediaItem item : cleanup)
          try {
            app.repository.deleteSaved(item);
          } catch (Exception ignored) {
          }
        if (oldVolume >= 0)
          ((AudioManager) app.getSystemService(android.content.Context.AUDIO_SERVICE))
              .setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0);
      }
    }
  }

  private void storageChecks(Bundle result) {
    try {
      app = (SaverApp) getTargetContext().getApplicationContext();
      int source = Integer.parseInt(options.getString("source", "0"));
      int expectedImages = Integer.parseInt(options.getString("images", "6"));
      int expectedVideos = Integer.parseInt(options.getString("videos", "2"));
      check(app.repository.connected(source), "real Android folder grant is persisted");
      List<MediaItem> statuses = app.repository.statuses(source);
      long images = statuses.stream().filter(i -> !i.video).count();
      long videos = statuses.stream().filter(i -> i.video).count();
      check(
          images == expectedImages && videos == expectedVideos,
          "all expected images and videos are discovered: " + images + "/" + videos);
      if (source == 0) {
        for (String account : new String[] {"1002", "1007"}) {
          boolean found = false;
          for (MediaItem item : statuses) {
            if (android.provider.DocumentsContract.getDocumentId(item.uri)
                .contains("/accounts/" + account + "/Media/.Statuses/")) found = true;
          }
          check(
              found,
              "statuses from account " + account + " are readable without choosing that account");
        }
        check(
            statuses.stream().anyMatch(i -> i.name.equals("status1.jpg")),
            "legacy Media/.Statuses remains readable beside account folders");
      }
      for (MediaItem item : statuses) {
        try (InputStream in = app.getContentResolver().openInputStream(item.uri)) {
          check(in != null && in.read() >= 0, "discovered status is readable: " + item.name);
        }
      }
      for (int i = 1; i < statuses.size(); i++)
        check(
            statuses.get(i - 1).modified >= statuses.get(i).modified,
            "statuses are ordered newest first");
      result.putString("stream", report.toString());
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "FAIL " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
