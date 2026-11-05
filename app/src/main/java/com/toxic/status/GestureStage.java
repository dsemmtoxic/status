package com.toxic.status;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** One gesture surface for both images and TextureView video. */
final class GestureStage extends FrameLayout {
  interface Listener {
    void tap();

    void seek(int seconds);

    void navigate(int direction);

    void adjustmentStart();

    void adjust(boolean brightness, float delta);

    void zoomChanged(float scale);
  }

  final FrameLayout media;
  private final GestureDetector taps;
  private Listener listener;
  private float scale = 1, panX, panY, downX, downY, aspect = 1;
  private float pinchSpan, pinchFocusX, pinchFocusY;
  private boolean video, multiple, adjusting, brightness;
  private final int slop;

  GestureStage(Context context) {
    super(context);
    setClipChildren(true);
    setClipToPadding(true);
    setClickable(true);
    media = new FrameLayout(context);
    addView(media, new LayoutParams(-1, -1));
    slop = ViewConfiguration.get(context).getScaledTouchSlop();
    taps =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!multiple && listener != null) performClick();
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                if (listener == null) return true;
                if (video && (e.getX() < getWidth() * .3f || e.getX() > getWidth() * .7f))
                  listener.seek(e.getX() < getWidth() / 2f ? -5 : 5);
                else toggleZoom(e.getX(), e.getY());
                return true;
              }

              @Override
              public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (multiple) return true;
                if (scale > 1.01f) {
                  panX -= dx;
                  panY -= dy;
                  apply();
                  return true;
                }
                float totalX = current.getX() - downX, totalY = current.getY() - downY;
                if ((video || downX < getWidth() / 2f)
                    && (adjusting
                        || (Math.abs(totalY) > slop * 2
                            && Math.abs(totalY) > Math.abs(totalX) * 1.25f))) {
                  if (!adjusting) {
                    adjusting = true;
                    brightness = downX < getWidth() / 2f;
                  }
                  if (listener != null)
                    listener.adjust(brightness, -totalY / Math.max(1, getHeight()) * 1.8f);
                }
                return true;
              }
            });
  }

  void configure(boolean video, Listener listener) {
    this.video = video;
    this.listener = listener;
    reset();
  }

  void aspect(float aspect) {
    this.aspect = aspect > 0 ? aspect : 1;
    apply();
  }

  float zoom() {
    return scale;
  }

  void reset() {
    scale = 1;
    panX = panY = 0;
    apply();
  }

  private void toggleZoom(float x, float y) {
    if (scale > 1.01f) {
      reset();
      return;
    }
    scale = 2.5f;
    panX = (getWidth() / 2f - x) * (scale - 1);
    panY = (getHeight() / 2f - y) * (scale - 1);
    apply();
  }

  private void apply() {
    float w = getWidth(), h = getHeight();
    float fittedW = Math.min(w, h * aspect), fittedH = fittedW / aspect;
    float maxX = Math.max(0, (fittedW * scale - w) / 2f),
        maxY = Math.max(0, (fittedH * scale - h) / 2f);
    panX = Math.max(-maxX, Math.min(maxX, panX));
    panY = Math.max(-maxY, Math.min(maxY, panY));
    media.setPivotX(w / 2f);
    media.setPivotY(h / 2f);
    media.setScaleX(scale);
    media.setScaleY(scale);
    media.setTranslationX(panX);
    media.setTranslationY(panY);
    if (listener != null) listener.zoomChanged(scale);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged(w, h, oldW, oldH);
    apply();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    return true;
  }

  @Override
  @android.annotation.SuppressLint(
      "ClickableViewAccessibility") // GestureDetector calls performClick.
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      downX = event.getX();
      downY = event.getY();
      multiple = false;
      adjusting = false;
      pinchSpan = 0;
      if (listener != null) listener.adjustmentStart();
    }
    if (event.getPointerCount() > 1) {
      multiple = true;
      adjusting = false;
      float span = (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
      float fx = (event.getX(0) + event.getX(1) - getWidth()) / 2f;
      float fy = (event.getY(0) + event.getY(1) - getHeight()) / 2f;
      if (action == MotionEvent.ACTION_MOVE && pinchSpan > 0 && span > 0) {
        float next = Math.max(1, Math.min(5, scale * span / pinchSpan));
        float factor = next / scale;
        panX = fx - (pinchFocusX - panX) * factor;
        panY = fy - (pinchFocusY - panY) * factor;
        scale = next;
        apply();
      }
      pinchSpan = action == MotionEvent.ACTION_POINTER_UP ? 0 : span;
      pinchFocusX = fx;
      pinchFocusY = fy;
    }
    taps.onTouchEvent(event);
    if (action == MotionEvent.ACTION_UP) {
      pinchSpan = 0;
      float dx = event.getX() - downX, dy = event.getY() - downY;
      if (!multiple
          && !adjusting
          && scale <= 1.01f
          && Math.abs(dx) > Ui.dp(getContext(), 76)
          && Math.abs(dx) > Math.abs(dy) * 1.3f
          && listener != null) listener.navigate(dx < 0 ? 1 : -1);
    }
    if (action == MotionEvent.ACTION_CANCEL) pinchSpan = 0;
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    if (listener != null) listener.tap();
    return true;
  }
}
