package com.toxic.status;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Real touch events and Android resource resolution; fixtures are granted through the system
 * picker.
 */
final class InterfaceChecks {
  private final Instrumentation runner;

  InterfaceChecks(Instrumentation runner) {
    this.runner = runner;
  }

  private void runOnMainSync(Runnable action) {
    runner.runOnMainSync(action);
  }

  private Context getTargetContext() {
    return runner.getTargetContext();
  }

  private Activity startActivitySync(Intent intent) {
    return runner.startActivitySync(intent);
  }

  private void sendPointerSync(MotionEvent event) {
    runner.sendPointerSync(event);
  }

  private void finish(int code, Bundle output) {
    runner.finish(code, output);
  }

  private final StringBuilder report = new StringBuilder();
  private SaverApp app;
  private MainActivity activity;
  private MediaItem exported;
  private String originalTree;
  private SharedPreferences storage;

  private void check(boolean okay, String message) {
    if (!okay) throw new AssertionError(message);
    report.append("PASS ").append(message).append('\n');
  }

  private Object field(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private <T> T ui(Callable<T> callback) throws Exception {
    Object[] result = new Object[2];
    runOnMainSync(
        () -> {
          try {
            result[0] = callback.call();
          } catch (Throwable e) {
            result[1] = e;
          }
        });
    if (result[1] != null) throw new RuntimeException((Throwable) result[1]);
    return (T) result[0];
  }

  private void until(Callable<Boolean> condition, String message) throws Exception {
    for (int i = 0; i < 600; i++) {
      if (condition.call()) {
        check(true, message);
        return;
      }
      SystemClock.sleep(100);
    }
    check(false, message);
  }

  private Object page(int index) throws Exception {
    return ((Object[]) field(activity, "pages"))[index];
  }

  private RecyclerView grid(int index) throws Exception {
    return (RecyclerView) field(page(index), "grid");
  }

  private void refresh() throws Exception {
    ui(
        () -> {
          Method m = MainActivity.class.getDeclaredMethod("refresh");
          m.setAccessible(true);
          m.invoke(activity);
          return null;
        });
    until(() -> ui(() -> !(Boolean) field(activity, "loading")), "gallery refresh completes");
  }

  private void swipe(ViewPager2 pager, boolean left) throws Exception {
    int[] xy = new int[2];
    ui(
        () -> {
          pager.getLocationOnScreen(xy);
          return null;
        });
    float x0 = xy[0] + pager.getWidth() * (left ? .82f : .18f);
    float x1 = xy[0] + pager.getWidth() * (left ? .18f : .82f);
    float y = xy[1] + Math.min(pager.getHeight() * .5f, 360);
    long down = SystemClock.uptimeMillis();
    for (int i = 0; i <= 12; i++) {
      int action =
          i == 0
              ? MotionEvent.ACTION_DOWN
              : i == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
      MotionEvent e =
          MotionEvent.obtain(
              down, SystemClock.uptimeMillis(), action, x0 + (x1 - x0) * i / 12, y, 0);
      sendPointerSync(e);
      e.recycle();
      SystemClock.sleep(28);
    }
    SystemClock.sleep(800);
  }

  void run() {
    runner.waitForIdleSync();
    Bundle output = new Bundle();
    int result = Activity.RESULT_OK;
    try {
      app = (SaverApp) getTargetContext().getApplicationContext();
      storage = app.getSharedPreferences("storage", Context.MODE_PRIVATE);
      originalTree = storage.getString("tree0", null);
      check(
          originalTree != null && app.repository.connected(0),
          "real WhatsApp parent permission is present");
      check(
          AppLanguages.selected(app).isEmpty(),
          "fresh install follows device language without a language prompt");
      String[] codes = {"en", "pt", "fr", "de", "it", "es", "ar", "zh", "ru", "tr", "hi", "id"};
      String[] images = {
        "Images",
        "Imagens",
        "Images",
        "Bilder",
        "Immagini",
        "Imágenes",
        "الصور",
        "图片",
        "Фото",
        "Görseller",
        "तस्वीरें",
        "Gambar"
      };
      for (int i = 0; i < codes.length; i++) {
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(codes[i]));
        Context localized = app.createConfigurationContext(config);
        check(
            images[i].equals(localized.getString(R.string.tab_images)),
            "Android resolves the " + codes[i] + " translation");
        String brightness = localized.getString(R.string.brightness_value, 55);
        check(brightness.contains("%"), "localized brightness format is valid for " + codes[i]);
      }
      activity =
          (MainActivity)
              startActivitySync(
                  new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      until(() -> ui(() -> grid(0).getAdapter().getItemCount() >= 8), "multi-row image grid loads");
      check(
          ui(() -> activity.getResources().getConfiguration().getLocales().get(0).getLanguage())
              .equals(
                  android.content.res.Resources.getSystem()
                      .getConfiguration()
                      .getLocales()
                      .get(0)
                      .getLanguage()),
          "first screen uses the actual device language");
      ViewPager2 pager = (ViewPager2) field(activity, "pager");
      swipe(pager, true);
      until(() -> ui(() -> pager.getCurrentItem() == 1), "left swipe opens Videos");
      until(() -> ui(() -> grid(1).getChildCount() > 0), "video thumbnails load");
      RecyclerView.ViewHolder videoHolder = ui(() -> grid(1).findViewHolderForAdapterPosition(0));
      TextView duration = (TextView) field(videoHolder, "duration");
      until(() -> ui(() -> duration.getVisibility() == View.VISIBLE), "video duration is visible");
      List<MediaItem> videos = (List<MediaItem>) field(page(1), "items");
      MediaMetadataRetriever reader = new MediaMetadataRetriever();
      reader.setDataSource(app, videos.get(0).uri);
      String expected =
          Ui.time(
              Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
      reader.release();
      check(
          expected.equals(ui(() -> duration.getText().toString())),
          "thumbnail duration matches the real media metadata");
      check(
          ui(
              () ->
                  duration.getRight() > videoHolder.itemView.getWidth() * .7f
                      && duration.getBottom() > videoHolder.itemView.getHeight() * .8f),
          "duration sits in the lower right corner");
      swipe(pager, true);
      until(() -> ui(() -> pager.getCurrentItem() == 2), "second left swipe opens Saved");
      swipe(pager, false);
      until(() -> ui(() -> pager.getCurrentItem() == 1), "right swipe returns to Videos");
      swipe(pager, false);
      until(() -> ui(() -> pager.getCurrentItem() == 0), "right swipe returns to Images");
      ui(
          () -> {
            grid(0).scrollToPosition(8);
            return null;
          });
      SystemClock.sleep(500);
      int first =
          ui(
              () ->
                  ((LinearLayoutManager) grid(0).getLayoutManager())
                      .findFirstVisibleItemPosition());
      swipe(pager, true);
      swipe(pager, false);
      check(
          first
              == ui(
                  () ->
                      ((LinearLayoutManager) grid(0).getLayoutManager())
                          .findFirstVisibleItemPosition()),
          "each tab keeps its vertical scroll position");
      ui(
          () -> {
            grid(0).scrollToPosition(0);
            return null;
          });
      SystemClock.sleep(500);
      List<MediaItem> photos = (List<MediaItem>) field(page(0), "items");
      exported = app.repository.save(photos.get(0));
      refresh();
      until(
          () -> ui(() -> grid(0).findViewHolderForAdapterPosition(0) != null),
          "saved image remains in its original grid position");
      RecyclerView.ViewHolder photoHolder = ui(() -> grid(0).findViewHolderForAdapterPosition(0));
      check(
          ui(() -> photoHolder.itemView.getForeground() != null),
          "saved status has a green border");
      check(
          ui(() -> ((FrameLayout) photoHolder.itemView).getChildCount() == 3),
          "saved status adds no badge, checkmark or label");
      app.repository.deleteSaved(exported);
      exported = null;
      refresh();
      check(
          ui(() -> grid(0).findViewHolderForAdapterPosition(0).itemView.getForeground() == null),
          "border disappears when the saved copy is deleted");
      storage.edit().remove("tree0").commit();
      refresh();
      swipe(pager, true);
      until(
          () -> ui(() -> pager.getCurrentItem() == 1),
          "swiping also works on the empty access screen");
      swipe(pager, true);
      until(
          () -> ui(() -> pager.getCurrentItem() == 2),
          "empty tabs remain connected by swipe navigation");
      check(true, "all interface checks completed");
      output.putString("stream", report.toString());
    } catch (Throwable e) {
      output.putString("stream", report + "FAIL " + android.util.Log.getStackTraceString(e));
      result = Activity.RESULT_CANCELED;
    } finally {
      if (storage != null && originalTree != null)
        storage.edit().putString("tree0", originalTree).commit();
      if (app != null && exported != null)
        try {
          app.repository.deleteSaved(exported);
        } catch (Exception ignored) {
        }
      if (activity != null) runOnMainSync(activity::finish);
    }
    finish(result, output);
  }
}
