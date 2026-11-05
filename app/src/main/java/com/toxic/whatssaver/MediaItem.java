package com.toxic.whatssaver;

import android.net.Uri;
import org.json.JSONException;
import org.json.JSONObject;

/** Metadata only: media bytes are always read through the ContentResolver. */
public final class MediaItem {
  public final Uri uri;
  public final String name, mime, key;
  public final long modified, size;
  public final boolean video, saved;

  public MediaItem(
      Uri uri, String name, String mime, long modified, long size, String key, boolean saved) {
    this.uri = uri;
    this.name = name;
    this.mime = mime;
    this.modified = modified;
    this.size = size;
    this.key = key;
    this.video = mime.startsWith("video/");
    this.saved = saved;
  }

  JSONObject json() throws JSONException {
    return new JSONObject()
        .put("uri", uri.toString())
        .put("name", name)
        .put("mime", mime)
        .put("modified", modified)
        .put("size", size)
        .put("key", key)
        .put("saved", saved);
  }

  static MediaItem fromJson(JSONObject j) throws JSONException {
    return new MediaItem(
        Uri.parse(j.getString("uri")),
        j.getString("name"),
        j.getString("mime"),
        j.getLong("modified"),
        j.getLong("size"),
        j.getString("key"),
        j.getBoolean("saved"));
  }
}
