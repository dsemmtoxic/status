package com.toxic.status;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
  static final int BG = Color.WHITE, SURFACE = Color.WHITE, CARD = 0xffedf0ef;
  static final int TEXT = 0xff202725, MUTED = 0xff747d79;
  static final int GREEN = 0xff008476, DEEP = 0xffe8f3f1, BORDER = 0xffdde5e2;

  static int dp(Context c, float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable bg(int color, float radius, Context c) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static GradientDrawable outline(int color, int stroke, float radius, Context c) {
    GradientDrawable d = bg(color, radius, c);
    d.setStroke(dp(c, 1), stroke);
    return d;
  }

  static void ripple(View view, int color, int radius) {
    view.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x25008476), bg(color, radius, view.getContext()), null));
  }

  static TextView text(Context c, String value, float size, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    t.setIncludeFontPadding(false);
    return t;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static LinearLayout.LayoutParams lp(Context c, int w, int h) {
    return new LinearLayout.LayoutParams(w < 0 ? w : dp(c, w), h < 0 ? h : dp(c, h));
  }

  static ImageView iconButton(Context c, String icon, String description) {
    return iconButton(c, icon, description, TEXT, SURFACE);
  }

  static ImageView iconButton(Context c, String icon, String description, int tint, int surface) {
    ImageView button = new ImageView(c);
    button.setImageDrawable(new Icons(icon, tint));
    button.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    button.setContentDescription(description);
    ripple(button, surface, 0);
    button.setFocusable(true);
    return button;
  }

  static TextView button(Context c, String label, boolean primary) {
    TextView t = text(c, label, 15, primary ? BG : GREEN, true);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(c, 48));
    t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
    ripple(t, primary ? GREEN : DEEP, 8);
    t.setFocusable(true);
    return t;
  }

  static Dialog sheet(Activity activity, LinearLayout content) {
    Dialog dialog = new Dialog(activity);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
    dialog.setContentView(scroll);
    Window w = dialog.getWindow();
    if (w != null) {
      w.setBackgroundDrawable(bg(SURFACE, 16, activity));
      w.setDimAmount(.4f);
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      w.setGravity(Gravity.BOTTOM);
      w.setLayout(-1, -2);
    }
    content.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 28));
    return dialog;
  }

  static void gap(LinearLayout parent, int height) {
    parent.addView(new View(parent.getContext()), lp(parent.getContext(), 1, height));
  }

  static void edgeToEdge(Activity activity, View root) {
    edgeToEdge(activity, root, false);
  }

  static void edgeToEdge(Activity activity, View root, boolean lightNavigation) {
    Window window = activity.getWindow();
    if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    else
      window
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(
        lightNavigation && Build.VERSION.SDK_INT < 26 ? Color.BLACK : Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
    if (Build.VERSION.SDK_INT >= 30) {
      android.view.WindowInsetsController controller = window.getInsetsController();
      if (controller != null)
        controller.setSystemBarsAppearance(
            lightNavigation
                ? android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                : 0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    } else if (Build.VERSION.SDK_INT >= 26 && lightNavigation) {
      View decor = window.getDecorView();
      decor.setSystemUiVisibility(
          decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(i.left, i.top, i.right, i.bottom);
          } else
            view.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.requestApplyInsets();
  }

  static void systemBackground(View root, int body, int status) {
    root.setBackground(
        new Drawable() {
          final Paint paint = new Paint();

          @Override
          public void draw(Canvas canvas) {
            canvas.drawColor(body);
            paint.setColor(status);
            canvas.drawRect(0, 0, getBounds().right, root.getPaddingTop(), paint);
          }

          @Override
          public void setAlpha(int alpha) {}

          @Override
          public void setColorFilter(ColorFilter filter) {}

          @Override
          public int getOpacity() {
            return PixelFormat.OPAQUE;
          }
        });
  }

  static String time(long durationMs) {
    long seconds = Math.max(0, durationMs / 1000);
    return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }
}
