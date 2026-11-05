package com.toxic.whatssaver;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SaverApp extends Application {
  public final ExecutorService io = Executors.newSingleThreadExecutor();
  public final Handler main = new Handler(Looper.getMainLooper());
  public StatusRepository repository;
  public ThumbnailLoader thumbnails;

  @Override
  public void onCreate() {
    super.onCreate();
    repository = new StatusRepository(this);
    thumbnails = new ThumbnailLoader(this);
    io.execute(repository::cleanTemporaryFiles);
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= TRIM_MEMORY_UI_HIDDEN) thumbnails.clear();
  }
}
