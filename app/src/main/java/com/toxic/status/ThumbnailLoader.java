package com.toxic.status;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ThumbnailLoader {
  interface Callback {
    void loaded(Bitmap bitmap, long duration);
  }

  private final Context context;
  private final ExecutorService workers = Executors.newFixedThreadPool(3);
  private final Handler main = new Handler(Looper.getMainLooper());
  private final LruCache<String, Result> cache =
      new LruCache<String, Result>(
          (int) Math.min(32 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, Result result) {
          return result.bitmap == null ? 1 : result.bitmap.getAllocationByteCount();
        }
      };

  private static final class Result {
    final Bitmap bitmap;
    final long duration;

    Result(Bitmap bitmap, long duration) {
      this.bitmap = bitmap;
      this.duration = duration;
    }
  }

  ThumbnailLoader(Context context) {
    this.context = context;
  }

  void clear() {
    cache.evictAll();
  }

  void load(MediaItem item, ImageView view, Callback callback) {
    String tag = item.uri + "|" + item.modified;
    view.setTag(tag);
    Result hit = cache.get(tag);
    if (hit != null) {
      view.setImageBitmap(hit.bitmap);
      if (callback != null) callback.loaded(hit.bitmap, hit.duration);
      return;
    }
    view.setImageDrawable(new Icons(item.video ? "video" : "image", Ui.BORDER));
    workers.execute(
        () -> {
          if (!tag.equals(view.getTag())) return;
          Bitmap bitmap = null;
          long duration = 0;
          try {
            if (Build.VERSION.SDK_INT >= 29) {
              try {
                bitmap =
                    context.getContentResolver().loadThumbnail(item.uri, new Size(480, 600), null);
              } catch (Exception ignored) {
              }
            }
            if (item.video) {
              MediaMetadataRetriever reader = new MediaMetadataRetriever();
              try {
                reader.setDataSource(context, item.uri);
                String value = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) duration = Long.parseLong(value);
                if (bitmap == null) {
                  if (Build.VERSION.SDK_INT >= 27)
                    bitmap =
                        reader.getScaledFrameAtTime(
                            0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 600);
                  else
                    bitmap = reader.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
              } finally {
                reader.release();
              }
            } else if (bitmap == null) bitmap = image(item, 600);
            if (bitmap != null && Math.max(bitmap.getWidth(), bitmap.getHeight()) > 900) {
              float factor = 600f / Math.max(bitmap.getWidth(), bitmap.getHeight());
              Bitmap smaller =
                  Bitmap.createScaledBitmap(
                      bitmap,
                      Math.max(1, Math.round(bitmap.getWidth() * factor)),
                      Math.max(1, Math.round(bitmap.getHeight() * factor)),
                      true);
              if (smaller != bitmap) bitmap.recycle();
              bitmap = smaller;
            }
          } catch (Exception | OutOfMemoryError ignored) {
          }
          Result result = new Result(bitmap, duration);
          if (bitmap != null) cache.put(tag, result);
          main.post(
              () -> {
                if (tag.equals(view.getTag())) {
                  if (result.bitmap != null) view.setImageBitmap(result.bitmap);
                  if (callback != null) callback.loaded(result.bitmap, result.duration);
                }
              });
        });
  }

  Bitmap image(MediaItem item, int maximum) throws Exception {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (InputStream input = context.getContentResolver().openInputStream(item.uri)) {
      BitmapFactory.decodeStream(input, null, options);
    }
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > maximum * 1.5f)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap;
    try (InputStream input = context.getContentResolver().openInputStream(item.uri)) {
      bitmap = BitmapFactory.decodeStream(input, null, options);
    }
    if (bitmap == null) return null;
    try (InputStream input = context.getContentResolver().openInputStream(item.uri)) {
      ExifInterface exif = new ExifInterface(input);
      int orientation =
          exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      Matrix m = new Matrix();
      switch (orientation) {
        case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
          m.setScale(-1, 1);
          break;
        case ExifInterface.ORIENTATION_ROTATE_180:
          m.setRotate(180);
          break;
        case ExifInterface.ORIENTATION_FLIP_VERTICAL:
          m.setScale(1, -1);
          break;
        case ExifInterface.ORIENTATION_TRANSPOSE:
          m.setRotate(90);
          m.postScale(-1, 1);
          break;
        case ExifInterface.ORIENTATION_ROTATE_90:
          m.setRotate(90);
          break;
        case ExifInterface.ORIENTATION_TRANSVERSE:
          m.setRotate(270);
          m.postScale(-1, 1);
          break;
        case ExifInterface.ORIENTATION_ROTATE_270:
          m.setRotate(270);
          break;
      }
      if (!m.isIdentity()) {
        Bitmap rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        if (rotated != bitmap) bitmap.recycle();
        bitmap = rotated;
      }
    } catch (Exception ignored) {
    }
    return bitmap;
  }
}
