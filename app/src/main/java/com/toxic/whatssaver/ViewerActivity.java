package com.toxic.whatssaver;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ViewerActivity extends Activity implements GestureStage.Listener {
  private SaverApp app;
  private List<MediaItem> items = new ArrayList<>();
  private Set<String> savedKeys = new java.util.HashSet<>();
  private int index, loadGeneration;
  private String session;
  private ExoPlayer player;
  private GestureStage stage;
  private FrameLayout root;
  private ImageView image, play, share, more, saveButton;
  private TextureView texture;
  private TextView title, time, total, hud, zoomReset, error;
  private LinearLayout top, bottom, timeline;
  private ProgressBar loading;
  private SeekBar seek;
  private boolean controls = true, resumed, seeking, saving, sharing, playOnResume = true;
  private long restorePosition;
  private float videoAspect = 1, startBrightness;
  private int startVolume;
  private AudioManager audio;
  private Bitmap imageBitmap;
  private final Runnable hideHud =
      () -> {
        if (hud != null) hud.setVisibility(View.GONE);
      };
  private final Runnable hideControls =
      () -> {
        if (player != null && player.isPlaying() && !seeking) setControls(false);
      };
  private final Runnable ticker =
      new Runnable() {
        @Override
        public void run() {
          updatePlayback();
          if (!isFinishing()) app.main.postDelayed(this, 300);
        }
      };

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(AppLanguages.wrap(base));
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (SaverApp) getApplication();
    audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    session = getIntent().getStringExtra("session");
    index = state == null ? getIntent().getIntExtra("index", 0) : state.getInt("index");
    if (state != null) {
      restorePosition = state.getLong("position");
      playOnResume = state.getBoolean("playing", true);
    }
    build();
    app.io.execute(
        () -> {
          try {
            List<MediaItem> result = app.repository.readSession(session);
            Set<String> keys = app.repository.savedKeys();
            app.main.post(
                () -> {
                  if (isDestroyed()) return;
                  items = result;
                  savedKeys = keys;
                  if (items.isEmpty()) {
                    finish();
                    return;
                  }
                  index = Math.max(0, Math.min(index, items.size() - 1));
                  showItem();
                });
          } catch (Exception e) {
            app.main.post(
                () -> {
                  toast(getString(R.string.error_file_gone));
                  finish();
                });
          }
        });
  }

  private int d(float n) {
    return Ui.dp(this, n);
  }

  private MediaItem current() {
    return items.isEmpty() ? null : items.get(index);
  }

  private void build() {
    root = new FrameLayout(this);
    Ui.systemBackground(root, Color.BLACK, Ui.GREEN);
    setContentView(root);
    Ui.edgeToEdge(this, root);
    stage = new GestureStage(this);
    root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
    image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    stage.media.addView(image, new FrameLayout.LayoutParams(-1, -1));
    texture = new TextureView(this);
    stage.media.addView(texture, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    texture.setVisibility(View.GONE);
    stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitVideo());
    loading = new ProgressBar(this);
    loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
    root.addView(loading, new FrameLayout.LayoutParams(d(30), d(30), Gravity.CENTER));
    error = Ui.text(this, getString(R.string.viewer_unavailable), 15, Color.WHITE, false);
    error.setLineSpacing(d(7), 1);
    error.setGravity(Gravity.CENTER);
    error.setPadding(d(25), d(25), d(25), d(25));
    error.setVisibility(View.GONE);
    root.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    error.setOnClickListener(v -> showItem());

    top = Ui.column(this);
    top.setBackgroundColor(Ui.GREEN);
    LinearLayout bar = Ui.row(this);
    bar.setPadding(d(4), 0, d(4), 0);
    ImageView back =
        Ui.iconButton(this, "back", getString(R.string.back_to_grid), Color.WHITE, Ui.GREEN);
    back.setOnClickListener(v -> finish());
    bar.addView(back, Ui.lp(this, 48, 48));
    title = Ui.text(this, getString(R.string.status_label), 18, Color.WHITE, false);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setPadding(d(8), 0, d(8), 0);
    bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    more = Ui.iconButton(this, "more", getString(R.string.media_options), Color.WHITE, Ui.GREEN);
    more.setOnClickListener(v -> options());
    bar.addView(more, Ui.lp(this, 48, 48));
    top.addView(bar, Ui.lp(this, -1, 56));
    root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

    bottom = Ui.column(this);
    bottom.setBackgroundColor(Color.WHITE);
    timeline = Ui.row(this);
    timeline.setPadding(d(8), 0, d(16), 0);
    play = Ui.iconButton(this, "pause", getString(R.string.pause_video), Ui.GREEN, Color.WHITE);
    play.setOnClickListener(v -> togglePlaying());
    timeline.addView(play, Ui.lp(this, 48, 48));
    time = Ui.text(this, Ui.time(0), 11, Ui.MUTED, false);
    timeline.addView(time, Ui.lp(this, 32, -2));
    seek = new SeekBar(this);
    seek.setMax(1000);
    seek.setContentDescription(getString(R.string.video_progress));
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
    seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BORDER));
    timeline.addView(seek, new LinearLayout.LayoutParams(0, d(48), 1));
    total = Ui.text(this, Ui.time(0), 11, Ui.MUTED, false);
    total.setGravity(Gravity.END);
    timeline.addView(total, Ui.lp(this, 32, -2));
    bottom.addView(timeline);
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onStartTrackingTouch(SeekBar s) {
            seeking = true;
            app.main.removeCallbacks(hideControls);
          }

          @Override
          public void onStopTrackingTouch(SeekBar s) {
            if (player != null && player.getDuration() > 0)
              player.seekTo(player.getDuration() * s.getProgress() / 1000);
            seeking = false;
            autoHide();
          }

          @Override
          public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
            if (fromUser && player != null && player.getDuration() > 0)
              time.setText(Ui.time(player.getDuration() * progress / 1000));
          }
        });
    LinearLayout actions = Ui.row(this);
    share = Ui.iconButton(this, "share", getString(R.string.share_media), Ui.GREEN, Color.WHITE);
    share.setOnClickListener(v -> share());
    actions.addView(share, new LinearLayout.LayoutParams(0, d(56), 1));
    saveButton =
        Ui.iconButton(this, "download", getString(R.string.save_to_gallery), Ui.GREEN, Color.WHITE);
    saveButton.setId(View.generateViewId());
    saveButton.setOnClickListener(v -> save());
    actions.addView(saveButton, new LinearLayout.LayoutParams(0, d(56), 1));
    bottom.addView(actions);
    root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    hud = Ui.text(this, "", 16, Color.WHITE, true);
    hud.setGravity(Gravity.CENTER);
    hud.setPadding(d(20), d(14), d(20), d(14));
    hud.setBackground(Ui.bg(0xd92b3330, 8, this));
    hud.setVisibility(View.GONE);
    root.addView(hud, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    zoomReset = Ui.text(this, getString(R.string.reset_zoom), 12, Color.WHITE, true);
    zoomReset.setPadding(d(14), d(10), d(14), d(10));
    zoomReset.setBackground(Ui.bg(0xc92b3330, 20, this));
    zoomReset.setVisibility(View.GONE);
    FrameLayout.LayoutParams resetLp =
        new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    resetLp.topMargin = d(68);
    root.addView(zoomReset, resetLp);
    zoomReset.setOnClickListener(v -> stage.reset());
  }

  private void showItem() {
    MediaItem item = current();
    if (item == null) return;
    int request = ++loadGeneration;
    releasePlayer();
    error.setVisibility(View.GONE);
    loading.setVisibility(View.VISIBLE);
    image.setImageDrawable(null);
    recycleImage();
    stage.configure(item.video, this);
    title.setText(
        getString(
            item.video ? R.string.viewer_video_title : R.string.viewer_status_title, index + 1));
    timeline.setVisibility(item.video ? View.VISIBLE : View.GONE);
    time.setText(Ui.time(0));
    total.setText(Ui.time(0));
    seek.setProgress(0);
    image.setVisibility(item.video ? View.GONE : View.VISIBLE);
    texture.setVisibility(item.video ? View.VISIBLE : View.GONE);
    updateSaveButton();
    setControls(true);
    if (item.video) {
      videoAspect = 9f / 16;
      fitVideo();
      player = new ExoPlayer.Builder(this).build();
      player.setVideoTextureView(texture);
      player.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
              .build(),
          true);
      player.setHandleAudioBecomingNoisy(true);
      player.addListener(
          new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
              if (request != loadGeneration) return;
              loading.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
              updatePlayback();
              if (state == Player.STATE_ENDED) setControls(true);
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
              if (request != loadGeneration) return;
              stage.setKeepScreenOn(playing);
              updatePlayback();
              if (playing) autoHide();
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
              if (size.height > 0) {
                videoAspect = size.width * size.pixelWidthHeightRatio / size.height;
                fitVideo();
              }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
              if (request == loadGeneration) showError();
            }
          });
      player.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri));
      player.prepare();
      if (restorePosition > 0) {
        player.seekTo(restorePosition);
        restorePosition = 0;
      }
      player.setPlayWhenReady(resumed && playOnResume);
      app.main.removeCallbacks(ticker);
      app.main.post(ticker);
    } else {
      app.main.removeCallbacks(ticker);
      app.io.execute(
          () -> {
            Bitmap bitmap = null;
            try {
              bitmap = app.thumbnails.image(item, 2400);
            } catch (Exception | OutOfMemoryError ignored) {
            }
            Bitmap result = bitmap;
            app.main.post(
                () -> {
                  if (isDestroyed() || request != loadGeneration) {
                    if (result != null) result.recycle();
                    return;
                  }
                  loading.setVisibility(View.GONE);
                  if (result == null) {
                    showError();
                    return;
                  }
                  imageBitmap = result;
                  image.setImageBitmap(result);
                  stage.aspect(result.getWidth() / (float) result.getHeight());
                });
          });
    }
  }

  private void fitVideo() {
    if (texture == null || stage == null || stage.getWidth() == 0 || videoAspect <= 0) return;
    int width = stage.getWidth(), height = Math.round(width / videoAspect);
    if (height > stage.getHeight()) {
      height = stage.getHeight();
      width = Math.round(height * videoAspect);
    }
    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) texture.getLayoutParams();
    if (lp.width != width || lp.height != height) {
      lp.width = width;
      lp.height = height;
      lp.gravity = Gravity.CENTER;
      texture.setLayoutParams(lp);
    }
    stage.aspect(videoAspect);
  }

  private void showError() {
    loading.setVisibility(View.GONE);
    error.setVisibility(View.VISIBLE);
    stage.setKeepScreenOn(false);
    setControls(true);
  }

  private void recycleImage() {
    if (imageBitmap != null) {
      imageBitmap.recycle();
      imageBitmap = null;
    }
  }

  private void releasePlayer() {
    if (player != null) {
      player.release();
      player = null;
    }
    stage.setKeepScreenOn(false);
    app.main.removeCallbacks(hideControls);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (player != null) player.setPlayWhenReady(playOnResume);
  }

  @Override
  protected void onPause() {
    super.onPause();
    resumed = false;
    if (player != null) {
      playOnResume = player.getPlayWhenReady();
      player.pause();
    }
    app.main.removeCallbacks(hideControls);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putInt("index", index);
    state.putLong("position", player == null ? 0 : player.getCurrentPosition());
    state.putBoolean("playing", player == null || playOnResume);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    loadGeneration++;
    app.main.removeCallbacks(ticker);
    app.main.removeCallbacks(hideHud);
    releasePlayer();
    image.setImageDrawable(null);
    recycleImage();
  }

  @Override
  public void navigate(int direction) {
    if (items.isEmpty() || index + direction < 0 || index + direction >= items.size()) return;
    index += direction;
    restorePosition = 0;
    playOnResume = true;
    showItem();
  }

  @Override
  public void tap() {
    setControls(!controls);
  }

  private void setControls(boolean visible) {
    controls = visible;
    Ui.systemBackground(root, Color.BLACK, visible ? Ui.GREEN : Color.BLACK);
    top.setVisibility(visible ? View.VISIBLE : View.GONE);
    bottom.setVisibility(visible ? View.VISIBLE : View.GONE);
    zoomReset.setVisibility(stage.zoom() > 1.01f && visible ? View.VISIBLE : View.GONE);
    if (visible) autoHide();
  }

  private void autoHide() {
    app.main.removeCallbacks(hideControls);
    if (player != null && player.isPlaying() && controls) app.main.postDelayed(hideControls, 4000);
  }

  @Override
  public void zoomChanged(float scale) {
    if (zoomReset != null)
      zoomReset.setVisibility(scale > 1.01f && controls ? View.VISIBLE : View.GONE);
  }

  @Override
  public void seek(int seconds) {
    if (player == null || player.getDuration() <= 0) return;
    long position =
        Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + seconds * 1000L));
    player.seekTo(position);
    showHud(seconds > 0 ? getString(R.string.seek_forward) : getString(R.string.seek_backward));
    updatePlayback();
    autoHide();
  }

  private void togglePlaying() {
    if (player == null) return;
    if (player.isPlaying()) {
      player.pause();
      playOnResume = false;
    } else {
      if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
      player.play();
      playOnResume = true;
    }
    updatePlayback();
    autoHide();
  }

  private void updatePlayback() {
    if (player == null) return;
    boolean playing = player.isPlaying();
    play.setImageDrawable(new Icons(playing ? "pause" : "play", Ui.GREEN));
    play.setContentDescription(
        playing ? getString(R.string.pause_video) : getString(R.string.play_video));
    long duration = Math.max(0, player.getDuration()),
        position = Math.max(0, player.getCurrentPosition());
    if (!seeking) {
      time.setText(Ui.time(position));
      seek.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
    }
    total.setText(Ui.time(duration));
  }

  @Override
  public void adjustmentStart() {
    float bright = getWindow().getAttributes().screenBrightness;
    if (bright < 0) {
      try {
        bright =
            Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
      } catch (Exception e) {
        bright = .5f;
      }
    }
    startBrightness = bright;
    startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
  }

  @Override
  public void adjust(boolean brightness, float delta) {
    if (brightness) {
      float value = Math.max(.03f, Math.min(1, startBrightness + delta));
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.screenBrightness = value;
      getWindow().setAttributes(attributes);
      showHud(getString(R.string.brightness_value, Math.round(value * 100)));
    } else {
      int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
          value = Math.max(0, Math.min(max, startVolume + Math.round(delta * max)));
      try {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
      } catch (SecurityException ignored) {
      }
      showHud(getString(R.string.volume_value, Math.round(value * 100f / Math.max(1, max))));
    }
    app.main.removeCallbacks(hideControls);
  }

  private void showHud(String text) {
    hud.setText(text);
    hud.setVisibility(View.VISIBLE);
    app.main.removeCallbacks(hideHud);
    app.main.postDelayed(hideHud, 900);
  }

  private void updateSaveButton() {
    MediaItem item = current();
    boolean isSaved = item != null && (item.saved || savedKeys.contains(item.key));
    saveButton.setImageDrawable(new Icons(isSaved ? "check" : "download", Ui.GREEN));
    saveButton.setContentDescription(
        saving
            ? getString(R.string.saving)
            : isSaved ? getString(R.string.saved_to_gallery) : getString(R.string.save_to_gallery));
    saveButton.setAlpha(saving ? .6f : 1);
    saveButton.setEnabled(!saving && !isSaved && item != null);
  }

  private void save() {
    MediaItem item = current();
    if (item == null || saving || item.saved || savedKeys.contains(item.key)) return;
    if (Build.VERSION.SDK_INT <= 28
        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 201);
      return;
    }
    saving = true;
    updateSaveButton();
    app.io.execute(
        () -> {
          try {
            app.repository.save(item);
            app.main.post(
                () -> {
                  saving = false;
                  savedKeys.add(item.key);
                  if (!isDestroyed()) {
                    updateSaveButton();
                    toast(getString(R.string.saved_to_gallery));
                  }
                });
          } catch (Exception e) {
            app.main.post(
                () -> {
                  saving = false;
                  if (!isDestroyed()) {
                    updateSaveButton();
                    toast(getString(R.string.error_save));
                  }
                });
          }
        });
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 201) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) save();
      else toast(getString(R.string.permission_storage));
    }
  }

  private void share() {
    MediaItem item = current();
    if (item == null || sharing) return;
    sharing = true;
    share.setAlpha(.5f);
    app.io.execute(
        () -> {
          try {
            Uri uri = app.repository.shareUri(item);
            app.main.post(
                () -> {
                  sharing = false;
                  if (isDestroyed()) return;
                  share.setAlpha(1);
                  Intent send =
                      new Intent(Intent.ACTION_SEND)
                          .setType(item.mime)
                          .putExtra(Intent.EXTRA_STREAM, uri)
                          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  send.setClipData(ClipData.newRawUri(getString(R.string.status_label), uri));
                  try {
                    startActivity(Intent.createChooser(send, getString(R.string.share_media)));
                  } catch (Exception e) {
                    toast(getString(R.string.error_no_share));
                  }
                });
          } catch (Exception e) {
            app.main.post(
                () -> {
                  sharing = false;
                  if (!isDestroyed()) {
                    share.setAlpha(1);
                    toast(getString(R.string.error_share));
                  }
                });
          }
        });
  }

  private void options() {
    MediaItem item = current();
    if (item == null) return;
    setControls(true);
    LinearLayout box = Ui.column(this);
    box.addView(Ui.text(this, getString(R.string.media_options), 22, Ui.TEXT, true));
    Ui.gap(box, 16);
    Dialog dialog = Ui.sheet(this, box);
    TextView gestures = Ui.button(this, getString(R.string.view_gestures), false);
    box.addView(gestures, Ui.lp(this, -1, 52));
    gestures.setOnClickListener(
        v -> {
          dialog.dismiss();
          gestureHelp();
        });
    if (item.saved) {
      Ui.gap(box, 10);
      TextView delete = Ui.button(this, getString(R.string.delete_saved), false);
      delete.setTextColor(0xffb3261e);
      box.addView(delete, Ui.lp(this, -1, 52));
      delete.setOnClickListener(
          v -> {
            dialog.dismiss();
            confirmDelete(item);
          });
    }
    dialog.show();
  }

  private void gestureHelp() {
    LinearLayout box = Ui.column(this);
    box.addView(Ui.text(this, getString(R.string.gestures_title), 22, Ui.TEXT, true));
    Ui.gap(box, 18);
    TextView text = Ui.text(this, getString(R.string.gestures_body), 14, Ui.MUTED, false);
    text.setLineSpacing(d(4), 1);
    box.addView(text);
    Ui.gap(box, 22);
    TextView done = Ui.button(this, getString(R.string.done), true);
    box.addView(done, Ui.lp(this, -1, 52));
    Dialog dialog = Ui.sheet(this, box);
    done.setOnClickListener(v -> dialog.dismiss());
    dialog.show();
  }

  private void confirmDelete(MediaItem item) {
    LinearLayout box = Ui.column(this);
    box.addView(Ui.text(this, getString(R.string.delete_title), 22, Ui.TEXT, true));
    Ui.gap(box, 12);
    TextView text = Ui.text(this, getString(R.string.delete_body), 14, Ui.MUTED, false);
    text.setLineSpacing(d(4), 1);
    box.addView(text);
    Ui.gap(box, 22);
    TextView remove = Ui.button(this, getString(R.string.delete_file), true);
    box.addView(remove, Ui.lp(this, -1, 52));
    Ui.gap(box, 10);
    TextView cancel = Ui.button(this, getString(R.string.cancel), false);
    box.addView(cancel, Ui.lp(this, -1, 52));
    Dialog dialog = Ui.sheet(this, box);
    cancel.setOnClickListener(v -> dialog.dismiss());
    remove.setOnClickListener(
        v -> {
          dialog.dismiss();
          app.io.execute(
              () -> {
                try {
                  app.repository.deleteSaved(item);
                  app.main.post(
                      () -> {
                        if (isDestroyed()) return;
                        savedKeys.remove(item.key);
                        items.remove(item);
                        if (items.isEmpty()) {
                          finish();
                          return;
                        }
                        index = Math.min(index, items.size() - 1);
                        showItem();
                        toast(getString(R.string.file_deleted));
                      });
                } catch (Exception e) {
                  app.main.post(() -> toast(getString(R.string.error_delete)));
                }
              });
        });
    dialog.show();
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
