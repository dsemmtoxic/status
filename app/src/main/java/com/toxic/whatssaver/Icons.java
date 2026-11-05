package com.toxic.whatssaver;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small, consistent stroke icons. No icon font or network resources. */
final class Icons extends Drawable {
  private final String name;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  Icons(String name, int color) {
    this.name = name;
    p.setColor(color);
    p.setStrokeWidth(1.8f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
  }

  private void line(Canvas c, float... xy) {
    Path path = new Path();
    path.moveTo(xy[0], xy[1]);
    for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
    c.drawPath(path, p);
  }

  @Override
  public void draw(Canvas c) {
    Rect b = getBounds();
    int save = c.save();
    c.translate(b.left, b.top);
    c.scale(b.width() / 24f, b.height() / 24f);
    if (("back".equals(name) || "next".equals(name))
        && getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL) {
      c.translate(24, 0);
      c.scale(-1, 1);
    }
    p.setStyle(Paint.Style.STROKE);
    switch (name) {
      case "brand":
        for (int i = 0; i < 4; i++)
          c.drawArc(new RectF(3, 3, 21, 21), i * 90 + 8, sixty(), false, p);
        line(c, 12, 7, 12, 16);
        line(c, 8.5f, 12.5f, 12, 16, 15.5f, 12.5f);
        break;
      case "image":
        c.drawRoundRect(3, 4, 21, 20, 3, 3, p);
        c.drawCircle(8, 9, 1.3f, p);
        line(c, 4, 17, 10, 12, 13, 15, 16, 11, 21, 16);
        break;
      case "video":
        c.drawRoundRect(3, 4, 21, 20, 3, 3, p);
        p.setStyle(Paint.Style.FILL);
        Path triangle = new Path();
        triangle.moveTo(10, 8);
        triangle.lineTo(16, 12);
        triangle.lineTo(10, 16);
        triangle.close();
        c.drawPath(triangle, p);
        break;
      case "download":
        line(c, 12, 3, 12, 15);
        line(c, 7, 10, 12, 15, 17, 10);
        line(c, 4, 16, 4, 20, 20, 20, 20, 16);
        break;
      case "saved":
        line(c, 6, 3, 18, 3, 18, 21, 12, 17, 6, 21, 6, 3);
        line(c, 9, 9, 11, 11, 15, 7);
        break;
      case "check":
        line(c, 5, 12, 10, 17, 19, 7);
        break;
      case "refresh":
        c.drawArc(new RectF(4, 4, 20, 20), 45, 285, false, p);
        line(c, 20, 4, 20, 10, 14, 10);
        break;
      case "folder":
        line(c, 3, 8, 3, 5, 9, 5, 11, 8, 21, 8, 21, 20, 3, 20, 3, 8);
        break;
      case "back":
        line(c, 14, 5, 7, 12, 14, 19);
        break;
      case "next":
        line(c, 9, 5, 16, 12, 9, 19);
        break;
      case "chevron":
        line(c, 7, 10, 12, 15, 17, 10);
        break;
      case "close":
        line(c, 6, 6, 18, 18);
        line(c, 18, 6, 6, 18);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) c.drawCircle(12, 5 + i * 7, 1.6f, p);
        break;
      case "info":
        c.drawCircle(12, 12, 9, p);
        line(c, 12, 11, 12, 17);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12, 7, 1, p);
        break;
      case "share":
        line(c, 7, 11, 17, 6);
        line(c, 7, 13, 17, 18);
        c.drawCircle(5, 12, 2.5f, p);
        c.drawCircle(19, 5, 2.5f, p);
        c.drawCircle(19, 19, 2.5f, p);
        break;
      case "trash":
        line(c, 4, 6, 20, 6);
        line(c, 7, 6, 8, 21, 16, 21, 17, 6);
        line(c, 9, 6, 9, 3, 15, 3, 15, 6);
        line(c, 10, 10, 10, 17);
        line(c, 14, 10, 14, 17);
        break;
      case "play":
        p.setStyle(Paint.Style.FILL);
        Path t = new Path();
        t.moveTo(8, 5);
        t.lineTo(20, 12);
        t.lineTo(8, 19);
        t.close();
        c.drawPath(t, p);
        break;
      case "pause":
        line(c, 9, 5, 9, 19);
        line(c, 16, 5, 16, 19);
        break;
      case "sun":
        c.drawCircle(12, 12, 4, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          line(
              c,
              (float) (12 + 7 * Math.cos(a)),
              (float) (12 + 7 * Math.sin(a)),
              (float) (12 + 10 * Math.cos(a)),
              (float) (12 + 10 * Math.sin(a)));
        }
        break;
      case "volume":
        line(c, 3, 9, 7, 9, 12, 4, 12, 20, 7, 15, 3, 15, 3, 9);
        c.drawArc(new RectF(10, 5, 23, 19), -65, 130, false, p);
        break;
      case "lock":
        c.drawRoundRect(5, 10, 19, 21, 3, 3, p);
        c.drawArc(new RectF(8, 2, 16, 15), 180, 180, false, p);
        break;
      case "zoom":
        c.drawCircle(10, 10, 6, p);
        line(c, 15, 15, 21, 21);
        line(c, 7, 10, 13, 10);
        line(c, 10, 7, 10, 13);
        break;
      case "open":
        line(c, 14, 3, 21, 3, 21, 10);
        line(c, 21, 3, 11, 13);
        line(c, 10, 4, 4, 4, 4, 20, 20, 20, 20, 14);
        break;
    }
    c.restoreToCount(save);
  }

  private float sixty() {
    return 64f;
  }

  @Override
  public void setAlpha(int alpha) {
    p.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    p.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth() {
    return 24;
  }

  @Override
  public int getIntrinsicHeight() {
    return 24;
  }
}
