package com.toxic.whatssaver;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;

public final class StatusRepository {
  private final Context context;
  private final ContentResolver resolver;
  private final SharedPreferences prefs;
  private final SavedDb db;
  private static final String[] DOC_COLUMNS = {
    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_SIZE
  };

  StatusRepository(Context context) {
    this.context = context;
    resolver = context.getContentResolver();
    prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE);
    db = new SavedDb(context);
  }

  public Uri tree(int source) {
    String value = prefs.getString("tree" + source, null);
    return value == null ? null : Uri.parse(value);
  }

  public boolean connected(int source) {
    Uri uri = tree(source);
    if (uri == null) return false;
    for (UriPermission p : resolver.getPersistedUriPermissions())
      if (p.getUri().equals(uri) && p.isReadPermission()) return true;
    return false;
  }

  public void connect(int source, Uri tree) throws Exception {
    List<Uri> statuses = findStatuses(tree, source);
    String path = DocumentsContract.getTreeDocumentId(tree).toLowerCase(Locale.ROOT);
    String name = documentName(root(tree));
    if (statuses.isEmpty()
        && !"WhatsApp".equalsIgnoreCase(name)
        && !"WhatsApp Business".equalsIgnoreCase(name)
        && !"com.whatsapp".equalsIgnoreCase(name)
        && !"com.whatsapp.w4b".equalsIgnoreCase(name)
        && !(path.contains("/whatsapp/") || path.contains("/whatsapp business/")))
      throw new IOException(AppLanguages.text(context, R.string.error_wrong_folder));
    prefs.edit().putString("tree" + source, tree.toString()).apply();
  }

  /** Start at the app folder, so one grant includes existing and future WhatsApp accounts. */
  static Uri initialFolder(int source) {
    String path = Build.VERSION.SDK_INT >= 29 ? "Android/media/" : "";
    path += source == 0 ? "com.whatsapp/WhatsApp" : "com.whatsapp.w4b/WhatsApp Business";
    if (Build.VERSION.SDK_INT < 29) path = source == 0 ? "WhatsApp" : "WhatsApp Business";
    return DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:" + path);
  }

  private Uri root(Uri tree) {
    return DocumentsContract.buildDocumentUriUsingTree(
        tree, DocumentsContract.getTreeDocumentId(tree));
  }

  private String documentName(Uri uri) throws IOException {
    try (Cursor c = resolver.query(uri, DOC_COLUMNS, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(1);
    }
    throw new IOException(AppLanguages.text(context, R.string.error_folder_unavailable));
  }

  private Uri childrenUri(Uri directory) {
    return DocumentsContract.buildChildDocumentsUriUsingTree(
        directory, DocumentsContract.getDocumentId(directory));
  }

  /** Visits only known layouts; no recursive scan of unrelated phone folders. */
  private final class FolderScan {
    private final Map<Uri, Map<String, Uri>> cache = new LinkedHashMap<>();
    private final Set<Uri> matches = new LinkedHashSet<>();

    Map<String, Uri> directories(Uri directory) throws IOException {
      if (cache.containsKey(directory)) return cache.get(directory);
      Map<String, Uri> result = new LinkedHashMap<>();
      try (Cursor c = resolver.query(childrenUri(directory), DOC_COLUMNS, null, null, null)) {
        if (c == null)
          throw new IOException(AppLanguages.text(context, R.string.error_folder_read));
        while (c.moveToNext()) {
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)))
            result.put(
                c.getString(1).toLowerCase(Locale.ROOT),
                DocumentsContract.buildDocumentUriUsingTree(directory, c.getString(0)));
        }
      }
      cache.put(directory, result);
      return result;
    }

    Uri follow(Uri directory, String... names) throws IOException {
      Uri current = directory;
      for (String name : names) {
        if (current == null) return null;
        current = directories(current).get(name.toLowerCase(Locale.ROOT));
      }
      return current;
    }

    void media(Uri directory) throws IOException {
      if (directory == null) return;
      Uri direct = follow(directory, ".Statuses");
      Uri nested = follow(directory, "Media", ".Statuses");
      if (direct != null) matches.add(direct);
      if (nested != null) matches.add(nested);
    }

    void accounts(Uri directory) throws IOException {
      if (directory == null) return;
      for (Uri account : directories(directory).values()) {
        try {
          media(account);
        } catch (IOException | SecurityException ignored) {
          // One unavailable account must not hide statuses from the others.
        }
      }
    }

    void whatsapp(Uri directory) throws IOException {
      if (directory == null) return;
      media(directory);
      accounts(follow(directory, "accounts"));
    }
  }

  private List<Uri> findStatuses(Uri tree, int source) throws IOException {
    Uri directory = root(tree);
    String name = documentName(directory);
    FolderScan scan = new FolderScan();
    if (".Statuses".equalsIgnoreCase(name)) scan.matches.add(directory);
    else {
      scan.whatsapp(directory);
      if ("accounts".equalsIgnoreCase(name)) scan.accounts(directory);
      String appName = source == 0 ? "WhatsApp" : "WhatsApp Business";
      String packageName = source == 0 ? "com.whatsapp" : "com.whatsapp.w4b";
      scan.whatsapp(scan.follow(directory, appName));
      scan.whatsapp(scan.follow(directory, packageName, appName));
      scan.whatsapp(scan.follow(directory, "Android", "media", packageName, appName));
    }
    return new ArrayList<>(scan.matches);
  }

  public List<MediaItem> statuses(int source) throws Exception {
    ArrayList<MediaItem> items = new ArrayList<>();
    if (!connected(source)) return items;
    List<Uri> directories = findStatuses(tree(source), source);
    Exception failure = null;
    int read = 0;
    for (Uri directory : directories) {
      try (Cursor c = resolver.query(childrenUri(directory), DOC_COLUMNS, null, null, null)) {
        if (c == null)
          throw new IOException(AppLanguages.text(context, R.string.error_status_read));
        read++;
        while (c.moveToNext()) {
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) continue;
          String name = c.getString(1), mime = normalizedMime(c.getString(2), c.getString(1));
          if (name == null || name.startsWith(".") || mime == null) continue;
          long modified = c.getLong(3), size = c.isNull(4) ? -1 : c.getLong(4);
          if (size == 0) continue;
          Uri uri = DocumentsContract.buildDocumentUriUsingTree(directory, c.getString(0));
          String key = fingerprint(source + "|" + name + "|" + modified + "|" + size);
          items.add(new MediaItem(uri, name, mime, modified, size, key, false));
        }
      } catch (IOException | SecurityException e) {
        failure = e;
      }
    }
    if (!directories.isEmpty() && read == 0 && failure != null) throw failure;
    items.sort(
        Comparator.comparingLong((MediaItem i) -> i.modified)
            .reversed()
            .thenComparing(i -> i.name));
    return items;
  }

  static String normalizedMime(String mime, String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (n.endsWith(".mp4")) return "video/mp4";
    if (n.endsWith(".3gp")) return "video/3gpp";
    if (n.endsWith(".webm")) return "video/webm";
    if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
    if (n.endsWith(".png")) return "image/png";
    if (n.endsWith(".webp")) return "image/webp";
    if (n.endsWith(".gif")) return "image/gif";
    if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) return mime;
    return null;
  }

  private static String fingerprint(String value) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", digest[i]));
    return result.toString();
  }

  public List<MediaItem> saved() {
    ArrayList<MediaItem> items = new ArrayList<>();
    ArrayList<String> missing = new ArrayList<>();
    try (Cursor c =
        db.getReadableDatabase().query("saved", null, null, null, null, null, "saved_at DESC")) {
      while (c.moveToNext()) {
        Uri uri = Uri.parse(c.getString(c.getColumnIndexOrThrow("uri")));
        String key = c.getString(c.getColumnIndexOrThrow("source_key"));
        if (!exists(uri)) {
          missing.add(key);
          continue;
        }
        items.add(
            new MediaItem(
                uri,
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("mime")),
                c.getLong(c.getColumnIndexOrThrow("saved_at")),
                c.getLong(c.getColumnIndexOrThrow("size")),
                key,
                true));
      }
    }
    for (String key : missing)
      db.getWritableDatabase().delete("saved", "source_key=?", new String[] {key});
    return items;
  }

  public Set<String> savedKeys() {
    HashSet<String> keys = new HashSet<>();
    for (MediaItem i : saved()) keys.add(i.key);
    return keys;
  }

  private boolean exists(Uri uri) {
    try (ParcelFileDescriptor fd = resolver.openFileDescriptor(uri, "r")) {
      return fd != null;
    } catch (Exception e) {
      return false;
    }
  }

  public synchronized MediaItem save(MediaItem item) throws Exception {
    if (item.saved) return item;
    for (MediaItem saved : saved()) if (saved.key.equals(item.key)) return saved;
    String extension = extension(item.mime);
    String name =
        "WhatsSaver_" + System.currentTimeMillis() + "_" + item.key.substring(0, 8) + extension;
    Uri destination = null;
    File legacy = null;
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, item.mime);
        values.put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            (item.video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES)
                + "/Whats Saver Status");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection =
            item.video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        destination = resolver.insert(collection, values);
        if (destination == null)
          throw new IOException(AppLanguages.text(context, R.string.error_create_file));
      } else {
        File directory =
            new File(
                Environment.getExternalStoragePublicDirectory(
                    item.video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES),
                "Whats Saver Status");
        if (!directory.isDirectory() && !directory.mkdirs())
          throw new IOException(AppLanguages.text(context, R.string.error_save_folder));
        legacy = new File(directory, name);
        destination =
            FileProvider.getUriForFile(context, context.getPackageName() + ".files", legacy);
      }
      long written;
      try (InputStream input = resolver.openInputStream(item.uri);
          OutputStream output =
              legacy == null
                  ? resolver.openOutputStream(destination, "w")
                  : new FileOutputStream(legacy)) {
        if (input == null || output == null)
          throw new IOException(AppLanguages.text(context, R.string.error_file_unavailable));
        written = copy(input, output);
      }
      if (written <= 0 || (item.size > 0 && written != item.size))
        throw new IOException(AppLanguages.text(context, R.string.error_file_changed));
      if (Build.VERSION.SDK_INT >= 29) {
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (resolver.update(destination, ready, null, null) == 0)
          throw new IOException(AppLanguages.text(context, R.string.error_save_complete));
      }
      long now = System.currentTimeMillis();
      ContentValues record = new ContentValues();
      record.put("source_key", item.key);
      record.put("uri", destination.toString());
      record.put("name", name);
      record.put("mime", item.mime);
      record.put("saved_at", now);
      record.put("size", written);
      db.getWritableDatabase().insertOrThrow("saved", null, record);
      if (legacy != null)
        MediaScannerConnection.scanFile(
            context, new String[] {legacy.getAbsolutePath()}, new String[] {item.mime}, null);
      return new MediaItem(destination, name, item.mime, now, written, item.key, true);
    } catch (Exception e) {
      if (legacy != null) legacy.delete();
      else if (destination != null)
        try {
          resolver.delete(destination, null, null);
        } catch (Exception ignored) {
        }
      throw e;
    }
  }

  static long copy(InputStream in, OutputStream out) throws IOException {
    byte[] bytes = new byte[65536];
    long count = 0;
    int n;
    while ((n = in.read(bytes)) != -1) {
      out.write(bytes, 0, n);
      count += n;
    }
    out.flush();
    return count;
  }

  public synchronized void deleteSaved(MediaItem item) throws IOException {
    if (!item.saved) return;
    try {
      int removed = resolver.delete(item.uri, null, null);
      if (removed == 0 && exists(item.uri))
        throw new IOException(AppLanguages.text(context, R.string.error_delete));
      db.getWritableDatabase().delete("saved", "source_key=?", new String[] {item.key});
    } catch (SecurityException e) {
      throw new IOException(AppLanguages.text(context, R.string.error_delete), e);
    }
  }

  public Uri shareUri(MediaItem item) throws Exception {
    if (item.saved) return item.uri;
    File directory = new File(context.getCacheDir(), "shared");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException(AppLanguages.text(context, R.string.error_share));
    File file = new File(directory, item.key + extension(item.mime));
    if (!file.isFile() || file.length() == 0) {
      File temporary = new File(directory, item.key + ".partial");
      try (InputStream input = resolver.openInputStream(item.uri);
          OutputStream output = new FileOutputStream(temporary)) {
        if (input == null) throw new FileNotFoundException();
        long n = copy(input, output);
        if (n == 0 || (item.size > 0 && n != item.size))
          throw new IOException(AppLanguages.text(context, R.string.error_file_unavailable));
      } catch (Exception e) {
        temporary.delete();
        throw e;
      }
      if (!temporary.renameTo(file)) {
        temporary.delete();
        throw new IOException(AppLanguages.text(context, R.string.error_prepare_file));
      }
    }
    return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
  }

  static String extension(String mime) {
    return switch (mime) {
      case "video/3gpp" -> ".3gp";
      case "video/webm" -> ".webm";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      default -> mime.startsWith("video/") ? ".mp4" : ".jpg";
    };
  }

  public String writeSession(List<MediaItem> items) throws Exception {
    File directory = new File(context.getCacheDir(), "sessions");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException(AppLanguages.text(context, R.string.error_open_file));
    String id = UUID.randomUUID().toString();
    JSONArray array = new JSONArray();
    for (MediaItem item : items) array.put(item.json());
    try (FileOutputStream output = new FileOutputStream(new File(directory, id + ".json"))) {
      output.write(array.toString().getBytes(StandardCharsets.UTF_8));
    }
    return id;
  }

  public List<MediaItem> readSession(String id) throws Exception {
    if (id == null || !id.matches("[a-f0-9\\-]{36}"))
      throw new IOException(AppLanguages.text(context, R.string.error_session));
    File file = new File(new File(context.getCacheDir(), "sessions"), id + ".json");
    byte[] bytes;
    try (InputStream input = new java.io.FileInputStream(file);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
      copy(input, output);
      bytes = output.toByteArray();
    }
    JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
    ArrayList<MediaItem> items = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) items.add(MediaItem.fromJson(array.getJSONObject(i)));
    return items;
  }

  void cleanTemporaryFiles() {
    long cutoff = System.currentTimeMillis() - 86400000L;
    for (String name : new String[] {"sessions", "shared"}) {
      File[] files = new File(context.getCacheDir(), name).listFiles();
      if (files != null) for (File file : files) if (file.lastModified() < cutoff) file.delete();
    }
  }

  private static final class SavedDb extends SQLiteOpenHelper {
    SavedDb(Context context) {
      super(context, "saved.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(
          "CREATE TABLE saved (source_key TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT NOT NULL,"
              + " mime TEXT NOT NULL, saved_at INTEGER NOT NULL, size INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
  }
}
