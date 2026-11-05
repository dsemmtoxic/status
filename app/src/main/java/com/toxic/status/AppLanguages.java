package com.toxic.status;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

/** Follows the device until a language is explicitly chosen. */
final class AppLanguages {
  static final String[] CODES = {
    "", "en", "pt", "fr", "de", "it", "es", "ar", "zh", "ru", "tr", "hi", "id"
  };
  private static final String[] NAMES = {
    "",
    "English",
    "Português",
    "Français",
    "Deutsch",
    "Italiano",
    "Español",
    "العربية",
    "中文",
    "Русский",
    "Türkçe",
    "हिन्दी",
    "Bahasa Indonesia"
  };

  static String selected(Context context) {
    if (Build.VERSION.SDK_INT >= 33) {
      LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
      return locales.isEmpty() ? "" : locales.get(0).toLanguageTag().split("-")[0];
    }
    return context.getSharedPreferences("language", Context.MODE_PRIVATE).getString("code", "");
  }

  static Context wrap(Context base) {
    if (Build.VERSION.SDK_INT >= 33) return base;
    String code = selected(base);
    Configuration config = new Configuration(base.getResources().getConfiguration());
    LocaleList locales =
        code.isEmpty()
            ? Resources.getSystem().getConfiguration().getLocales()
            : LocaleList.forLanguageTags(code);
    config.setLocales(locales);
    config.setLayoutDirection(locales.get(0));
    return base.createConfigurationContext(config);
  }

  static String text(Context context, int resource) {
    return wrap(context).getString(resource);
  }

  static void select(Activity activity, String code) {
    if (Build.VERSION.SDK_INT >= 33) {
      activity
          .getSystemService(LocaleManager.class)
          .setApplicationLocales(LocaleList.forLanguageTags(code));
    } else {
      activity
          .getSharedPreferences("language", Context.MODE_PRIVATE)
          .edit()
          .putString("code", code)
          .apply();
      activity.recreate();
    }
  }

  static void show(Activity activity) {
    String[] labels = NAMES.clone();
    labels[0] = activity.getString(R.string.system_language);
    String code = selected(activity);
    int checked = 0;
    for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) checked = i;
    new AlertDialog.Builder(activity)
        .setTitle(R.string.language)
        .setSingleChoiceItems(
            labels,
            checked,
            (dialog, which) -> {
              dialog.dismiss();
              if (!CODES[which].equals(code)) select(activity, CODES[which]);
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }
}
