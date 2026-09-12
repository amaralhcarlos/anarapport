package com.anarapport.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central lookup for every user-facing string, backed by
 * {@code src/main/resources/i18n/messages*.properties}. Default language is
 * English (the base {@code messages.properties} bundle); other languages are
 * selected via {@link #setLocale(Locale)}, which also notifies listeners so
 * the UI can rebuild its text in place, live, without restarting the app.
 */
public final class Messages {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    // Order here is the order languages appear in the "Idioma"/"Language" menu.
    private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, new Locale("pt"));

    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private static volatile Locale currentLocale = Locale.ENGLISH;
    private static volatile ResourceBundle bundle = loadBundle(currentLocale);

    private Messages() {
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String get(String key, Object... args) {
        return String.format(currentLocale, get(key), args);
    }

    public static synchronized void setLocale(Locale locale) {
        if (locale.equals(currentLocale)) {
            return;
        }
        currentLocale = locale;
        bundle = loadBundle(locale);
        for (Runnable listener : List.copyOf(LISTENERS)) {
            listener.run();
        }
    }

    public static Locale getLocale() {
        return currentLocale;
    }

    public static List<Locale> getSupportedLocales() {
        return SUPPORTED_LOCALES;
    }

    /** Registers a callback invoked (on whatever thread called setLocale) after every language change. */
    public static synchronized void addChangeListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    private static ResourceBundle loadBundle(Locale locale) {
        // PropertyResourceBundle reads properties files as UTF-8 by default since Java 9,
        // so accented characters can be written directly, no \\uXXXX escaping needed.
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
    }
}
