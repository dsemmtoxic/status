package com.toxic.whatssaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
  private SaverApp app;
  private int source, tab, generation, pickerSource;
  private boolean loading, opening, restoreScroll = true;
  private String loadError;
  private List<MediaItem> statuses = new ArrayList<>(), saved = new ArrayList<>();
  private Set<String> savedKeys = new HashSet<>();
  private final Parcelable[] positions = new Parcelable[3];
  private final TextView[] tabs = new TextView[3];
  private final View[] indicators = new View[3];
  private final MediaPage[] pages = new MediaPage[3];
  private LinearLayout root;
  private ViewPager2 pager;
  private ProgressBar progress;
  private static final int PICK_FOLDER = 101;

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(AppLanguages.wrap(base));
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (SaverApp) getApplication();
    source = getPreferences(0).getInt("source", 0);
    pickerSource = source;
    if (state != null) {
      tab = state.getInt("tab");
      pickerSource = state.getInt("pickerSource", source);
      for (int i = 0; i < 3; i++) positions[i] = state.getParcelable("scroll" + i);
    }
    build();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (root != null) refresh();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putInt("tab", tab);
    state.putInt("pickerSource", pickerSource);
    for (int i = 0; i < 3; i++) {
      positions[i] = pages[i].grid.getLayoutManager().onSaveInstanceState();
      state.putParcelable("scroll" + i, positions[i]);
    }
  }

  private int d(float value) {
    return Ui.dp(this, value);
  }

  private String sourceName() {
    return getString(source == 0 ? R.string.source_whatsapp : R.string.source_business);
  }

  private void build() {
    root = Ui.column(this);
    Ui.systemBackground(root, Color.WHITE, Ui.GREEN);
    setContentView(root);
    Ui.edgeToEdge(this, root, true);
    LinearLayout header = Ui.row(this);
    header.setBackgroundColor(Ui.GREEN);
    header.setPaddingRelative(d(20), 0, d(4), 0);
    TextView title = Ui.text(this, getString(R.string.app_name), 20, Color.WHITE, true);
    title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView menu =
        Ui.iconButton(this, "more", getString(R.string.more_options), Color.WHITE, Ui.GREEN);
    menu.setOnClickListener(this::showMenu);
    header.addView(menu, Ui.lp(this, 48, 48));
    root.addView(header, Ui.lp(this, -1, 56));

    LinearLayout tabRow = Ui.row(this);
    tabRow.setBackgroundColor(Ui.GREEN);
    int[] labels = {R.string.tab_images, R.string.tab_videos, R.string.tab_saved};
    for (int i = 0; i < 3; i++) {
      final int next = i;
      LinearLayout slot = Ui.column(this);
      TextView t = Ui.text(this, getString(labels[i]), 14, Color.WHITE, true);
      t.setGravity(Gravity.CENTER);
      t.setSingleLine(true);
      t.setAllCaps(true);
      t.setPadding(d(6), 0, d(6), 0);
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          t, 11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
      t.setContentDescription(getString(labels[i]));
      t.setFocusable(true);
      Ui.ripple(t, Ui.GREEN, 0);
      t.setOnClickListener(v -> pager.setCurrentItem(next, true));
      tabs[i] = t;
      slot.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
      View line = new View(this);
      line.setBackgroundColor(Color.WHITE);
      indicators[i] = line;
      slot.addView(line, Ui.lp(this, -1, 3));
      tabRow.addView(slot, new LinearLayout.LayoutParams(0, -1, 1));
    }
    root.addView(tabRow, Ui.lp(this, -1, 48));

    FrameLayout content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < 3; i++) pages[i] = new MediaPage(i);
    pager = new ViewPager2(this);
    pager.setId(R.id.media_pager);
    pager.setOffscreenPageLimit(2);
    pager.setAdapter(
        new RecyclerView.Adapter<PagerHolder>() {
          @Override
          public PagerHolder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout frame = new FrameLayout(MainActivity.this);
            frame.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            return new PagerHolder(frame);
          }

          @Override
          public void onBindViewHolder(PagerHolder holder, int position) {
            FrameLayout frame = (FrameLayout) holder.itemView;
            frame.removeAllViews();
            View page = pages[position].content;
            if (page.getParent() != null) ((ViewGroup) page.getParent()).removeView(page);
            frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
          }

          @Override
          public void onViewRecycled(PagerHolder holder) {
            ((FrameLayout) holder.itemView).removeAllViews();
          }

          @Override
          public int getItemCount() {
            return 3;
          }
        });
    pager.setCurrentItem(tab, false);
    pager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            tab = position;
            updateTabs();
          }
        });
    content.addView(pager, new FrameLayout.LayoutParams(-1, -1));
    progress = new ProgressBar(this);
    progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
    FrameLayout.LayoutParams spin =
        new FrameLayout.LayoutParams(d(24), d(24), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    spin.topMargin = d(12);
    content.addView(progress, spin);
    showContent();
  }

  private static final class PagerHolder extends RecyclerView.ViewHolder {
    PagerHolder(FrameLayout item) {
      super(item);
    }
  }

  private void refresh() {
    if (loading) return;
    loading = true;
    int request = ++generation, requestedSource = source;
    progress.setVisibility(View.VISIBLE);
    app.io.execute(
        () -> {
          List<MediaItem> media = new ArrayList<>();
          String error = null;
          try {
            media = app.repository.statuses(requestedSource);
          } catch (Exception e) {
            error =
                getString(
                    e instanceof SecurityException
                        ? R.string.error_access_expired
                        : R.string.error_status_read);
          }
          List<MediaItem> result = media, downloads = app.repository.saved();
          String message = error;
          app.main.post(
              () -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                if (request != generation) {
                  refresh();
                  return;
                }
                statuses = result;
                saved = downloads;
                savedKeys = new HashSet<>();
                for (MediaItem item : saved) savedKeys.add(item.key);
                loadError = message;
                progress.setVisibility(View.GONE);
                showContent();
                if (restoreScroll) {
                  restoreScroll = false;
                  for (int i = 0; i < 3; i++)
                    if (positions[i] != null)
                      pages[i].grid.getLayoutManager().onRestoreInstanceState(positions[i]);
                }
              });
        });
  }

  private void updateTabs() {
    for (int i = 0; i < 3; i++) {
      tabs[i].setTextColor(i == tab ? Color.WHITE : 0xffb8dfda);
      tabs[i].setSelected(i == tab);
      indicators[i].setVisibility(i == tab ? View.VISIBLE : View.INVISIBLE);
    }
  }

  private void showContent() {
    updateTabs();
    for (int i = 0; i < 3; i++) {
      List<MediaItem> items = new ArrayList<>();
      if (i == 2) items.addAll(saved);
      else for (MediaItem item : statuses) if (item.video == (i == 1)) items.add(item);
      pages[i].show(items);
    }
  }

  private void buildEmpty(LinearLayout empty, int page) {
    empty.removeAllViews();
    boolean needsAccess = page != 2 && (!app.repository.connected(source) || loadError != null);
    int heading =
        page == 2
            ? R.string.empty_saved_title
            : needsAccess ? R.string.access_title : R.string.empty_status_title;
    String detail =
        page == 2
            ? getString(R.string.empty_saved_body)
            : needsAccess
                ? loadError != null ? loadError : getString(R.string.access_body)
                : getString(R.string.empty_status_body);
    TextView title = Ui.text(this, getString(heading), 21, Ui.TEXT, true);
    title.setGravity(Gravity.CENTER);
    empty.addView(title);
    Ui.gap(empty, 12);
    TextView message = Ui.text(this, detail, 14, Ui.MUTED, false);
    message.setGravity(Gravity.CENTER);
    message.setLineSpacing(d(4), 1);
    empty.addView(message);
    if (page == 2) return;
    Ui.gap(empty, 24);
    TextView action =
        Ui.button(
            this, getString(needsAccess ? R.string.allow_access : R.string.open_whatsapp), true);
    action.setOnClickListener(
        v -> {
          if (needsAccess) pickFolder();
          else openWhatsApp();
        });
    empty.addView(action, Ui.lp(this, -2, -2));
    if (!needsAccess) {
      Ui.gap(empty, 8);
      TextView access = Ui.text(this, getString(R.string.update_access), 13, Ui.GREEN, true);
      access.setPadding(d(12), d(16), d(12), d(16));
      access.setFocusable(true);
      access.setOnClickListener(v -> pickFolder());
      empty.addView(access);
    }
  }

  private void showMenu(View anchor) {
    PopupMenu menu = new PopupMenu(this, anchor);
    menu.getMenu().add(0, 0, 0, R.string.refresh);
    menu.getMenu().add(0, 1, 1, R.string.folder_access);
    menu.getMenu().add(0, 2, 2, getString(R.string.source_label, sourceName()));
    menu.getMenu().add(0, 3, 3, R.string.language);
    menu.getMenu().add(0, 4, 4, R.string.help);
    menu.getMenu().add(0, 5, 5, R.string.about);
    menu.setOnMenuItemClickListener(
        item -> {
          switch (item.getItemId()) {
            case 0:
              refresh();
              break;
            case 1:
              pickFolder();
              break;
            case 2:
              chooseSource();
              break;
            case 3:
              AppLanguages.show(this);
              break;
            case 4:
              help();
              break;
            case 5:
              new AlertDialog.Builder(this)
                  .setTitle(R.string.app_name)
                  .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                  .setPositiveButton(R.string.close, null)
                  .show();
              break;
          }
          return true;
        });
    menu.show();
  }

  private void chooseSource() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.source_title)
        .setSingleChoiceItems(
            new String[] {getString(R.string.source_whatsapp), getString(R.string.source_business)},
            source,
            (dialog, which) -> {
              dialog.dismiss();
              if (source == which) return;
              source = which;
              getPreferences(0).edit().putInt("source", source).apply();
              statuses = new ArrayList<>();
              loadError = null;
              positions[0] = positions[1] = null;
              pages[0].grid.scrollToPosition(0);
              pages[1].grid.scrollToPosition(0);
              generation++;
              showContent();
              if (!loading) refresh();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.help_title)
        .setMessage(R.string.help_body)
        .setPositiveButton(R.string.done, null)
        .show();
  }

  private void pickFolder() {
    pickerSource = source;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
    if (Build.VERSION.SDK_INT >= 26)
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, StatusRepository.initialFolder(source));
    try {
      startActivityForResult(intent, PICK_FOLDER);
    } catch (ActivityNotFoundException e) {
      toast(getString(R.string.error_picker_unavailable));
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != PICK_FOLDER || result != RESULT_OK || data == null || data.getData() == null)
      return;
    Uri tree = data.getData();
    int selected = pickerSource;
    boolean held = false;
    for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions())
      if (p.getUri().equals(tree) && p.isReadPermission()) held = true;
    try {
      getContentResolver()
          .takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (SecurityException e) {
      toast(getString(R.string.error_persist_permission));
      return;
    }
    final boolean releaseOnFailure = !held;
    app.io.execute(
        () -> {
          String error = null;
          try {
            app.repository.connect(selected, tree);
          } catch (Exception e) {
            error = getString(R.string.error_folder_access);
            if (releaseOnFailure)
              try {
                getContentResolver()
                    .releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
              } catch (Exception ignored) {
              }
          }
          String message = error;
          app.main.post(
              () -> {
                if (isDestroyed()) return;
                if (message != null) toast(message);
                else {
                  generation++;
                  if (!loading) refresh();
                }
              });
        });
  }

  private void openWhatsApp() {
    Intent launch =
        getPackageManager()
            .getLaunchIntentForPackage(source == 0 ? "com.whatsapp" : "com.whatsapp.w4b");
    if (launch == null) toast(getString(R.string.error_whatsapp_missing));
    else startActivity(launch);
  }

  private void open(List<MediaItem> items, int index) {
    if (opening || index < 0 || index >= items.size()) return;
    opening = true;
    List<MediaItem> snapshot = new ArrayList<>(items);
    app.io.execute(
        () -> {
          try {
            String session = app.repository.writeSession(snapshot);
            app.main.post(
                () -> {
                  opening = false;
                  if (isDestroyed()) return;
                  startActivity(
                      new Intent(this, ViewerActivity.class)
                          .putExtra("session", session)
                          .putExtra("index", index));
                });
          } catch (Exception e) {
            app.main.post(
                () -> {
                  opening = false;
                  if (!isDestroyed()) toast(getString(R.string.error_open_file));
                });
          }
        });
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private final class MediaPage {
    final int index;
    final FrameLayout content;
    final RecyclerView grid;
    final ScrollView emptyScroll;
    final LinearLayout empty;
    final MediaAdapter adapter;
    List<MediaItem> items = new ArrayList<>();
    Set<String> keys = new HashSet<>();

    MediaPage(int index) {
      this.index = index;
      content = new FrameLayout(MainActivity.this);
      grid = new RecyclerView(MainActivity.this);
      grid.setLayoutManager(new GridLayoutManager(MainActivity.this, 2));
      grid.setPadding(d(3), d(3), d(3), d(3));
      grid.setClipToPadding(false);
      grid.setItemAnimator(null);
      grid.addItemDecoration(
          new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(
                android.graphics.Rect out,
                View view,
                RecyclerView parent,
                RecyclerView.State state) {
              out.set(d(3), d(3), d(3), d(3));
            }
          });
      adapter = new MediaAdapter(this);
      grid.setAdapter(adapter);
      content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
      emptyScroll = new ScrollView(MainActivity.this);
      emptyScroll.setFillViewport(true);
      empty = Ui.column(MainActivity.this);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(d(32), d(28), d(32), d(28));
      emptyScroll.addView(empty, new ScrollView.LayoutParams(-1, -1));
      content.addView(emptyScroll, new FrameLayout.LayoutParams(-1, -1));
    }

    void show(List<MediaItem> after) {
      List<MediaItem> before = items;
      Set<String> beforeKeys = keys;
      items = after;
      keys = savedKeys;
      DiffUtil.calculateDiff(
              new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                  return before.size();
                }

                @Override
                public int getNewListSize() {
                  return after.size();
                }

                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                  return before.get(oldPos).uri.equals(after.get(newPos).uri);
                }

                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                  MediaItem old = before.get(oldPos), next = after.get(newPos);
                  return old.modified == next.modified
                      && old.size == next.size
                      && old.saved == next.saved
                      && beforeKeys.contains(old.key) == keys.contains(next.key);
                }
              })
          .dispatchUpdatesTo(adapter);
      grid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
      emptyScroll.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
      if (items.isEmpty()) buildEmpty(empty, index);
    }
  }

  private final class MediaAdapter extends RecyclerView.Adapter<MediaHolder> {
    final MediaPage page;

    MediaAdapter(MediaPage page) {
      this.page = page;
    }

    @Override
    public MediaHolder onCreateViewHolder(ViewGroup parent, int type) {
      return new MediaHolder(new FrameLayout(MainActivity.this));
    }

    @Override
    public void onBindViewHolder(MediaHolder holder, int position) {
      MediaItem item = page.items.get(position);
      boolean isSaved = item.saved || page.keys.contains(item.key);
      holder.itemView.setContentDescription(
          getString(item.video ? R.string.video_item : R.string.image_item, position + 1)
              + (isSaved ? ", " + getString(R.string.already_saved) : ""));
      if (isSaved && page.index != 2) {
        GradientDrawable border = Ui.bg(Color.TRANSPARENT, 12, MainActivity.this);
        border.setStroke(d(1.5f), Ui.GREEN);
        holder.itemView.setForeground(border);
      } else holder.itemView.setForeground(null);
      holder.play.setVisibility(item.video ? View.VISIBLE : View.GONE);
      holder.duration.setVisibility(View.GONE);
      app.thumbnails.load(
          item,
          holder.preview,
          (bitmap, duration) -> {
            if (item.video && duration > 0) {
              holder.duration.setText(Ui.time(duration));
              holder.duration.setVisibility(View.VISIBLE);
              holder.itemView.setContentDescription(
                  getString(R.string.video_item, position + 1)
                      + ", "
                      + getString(R.string.duration_description, Ui.time(duration))
                      + (isSaved ? ", " + getString(R.string.already_saved) : ""));
            }
          });
      holder.itemView.setOnClickListener(
          v -> {
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) open(page.items, p);
          });
    }

    @Override
    public void onViewRecycled(MediaHolder holder) {
      holder.preview.setTag(null);
      holder.preview.setImageDrawable(null);
    }

    @Override
    public int getItemCount() {
      return page.items.size();
    }
  }

  private final class MediaHolder extends RecyclerView.ViewHolder {
    final ImageView preview, play;
    final TextView duration;

    @android.annotation.SuppressLint("RtlHardcoded")
    MediaHolder(FrameLayout card) {
      super(card);
      int width = (getResources().getDisplayMetrics().widthPixels - d(18)) / 2;
      card.setLayoutParams(new RecyclerView.LayoutParams(-1, Math.round(width * 1.18f)));
      card.setBackground(Ui.bg(Ui.CARD, 12, MainActivity.this));
      card.setClipToOutline(true);
      card.setFocusable(true);
      preview = new ImageView(MainActivity.this);
      preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
      card.addView(preview, new FrameLayout.LayoutParams(-1, -1));
      play = new ImageView(MainActivity.this);
      play.setImageDrawable(new Icons("play", Color.WHITE));
      play.setPadding(d(8), d(8), d(8), d(8));
      play.setBackground(Ui.bg(0x28000000, 28, MainActivity.this));
      play.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      card.addView(play, new FrameLayout.LayoutParams(d(54), d(54), Gravity.CENTER));
      duration = Ui.text(MainActivity.this, "", 11, Color.WHITE, true);
      duration.setTextDirection(View.TEXT_DIRECTION_LTR);
      duration.setPadding(d(5), d(3), d(5), d(3));
      duration.setBackground(Ui.bg(0xb3000000, 4, MainActivity.this));
      duration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      // Duration stays in the physical lower-right corner in every language.
      FrameLayout.LayoutParams timeLp =
          new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
      timeLp.setMargins(d(7), d(7), d(7), d(7));
      card.addView(duration, timeLp);
    }
  }
}
