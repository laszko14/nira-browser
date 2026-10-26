# v1162 — inwentaryzacja statyczna aplikacji

Baza: a3fa1232265348cb3ac3405c6e56594a6634a3b0. Data: 2026-10-26 Europe/Warsaw.

Katalog nie oznacza wykonania wszystkich ekranów i kombinacji ustawień. Kontrola zachowania i ograniczenia są w ZAVRO_X_MPV_V1162_HANDOFF.md.

## Moduły i wejścia

Jeden moduł Gradle `:app`, 309 plików aplikacji w java/, 11 JS i 2 Lua. JNI/native z zewnętrznych AAR; brak własnych źródeł C/C++ w app.

| Obszar | Pliki |
|---|---:|
| (entrypoints) | 11 |
| addons | 13 |
| auth | 1 |
| browser | 81 |
| components | 21 |
| customtabs | 3 |
| downloads | 6 |
| ext | 7 |
| history | 5 |
| integration | 9 |
| media | 78 |
| middleware | 3 |
| onboarding | 1 |
| perf | 1 |
| permissions | 4 |
| preferences | 1 |
| privacy | 6 |
| request | 1 |
| search | 12 |
| settings | 18 |
| share | 1 |
| ssl | 3 |
| theme | 3 |
| toolbar | 1 |
| ui | 3 |
| utils | 7 |
| webapp | 9 |

### activity (14)

- `.BrowserActivity`
- `.media.ZavroMpvActivity`
- `.onboarding.OnboardingActivity`
- `.customtabs.ExternalAppBrowserActivity`
- `.addons.AddonsActivity`
- `.settings.activity.SettingsActivity`
- `.addons.AddonDetailsActivity`
- `.addons.InstalledAddonDetailsActivity`
- `.addons.PermissionsDetailsActivity`
- `.addons.AddonSettingsActivity`
- `.addons.NotYetSupportedAddonActivity`
- `.history.HistoryActivity`
- `.IntentReceiverActivity`
- `.webapp.WebAppActivity`

### service (4)

- `.media.MediaSessionService`
- `.downloads.DownloadService`
- `.downloads.ParallelDownloadService`
- `.customtabs.CustomTabsService`

### provider (1)

- `androidx.core.content.FileProvider`

### receiver (0)


## Ustawienia XML (76 kluczy / 7 ekranów)

Wszystkie odwołania kluczy do domyślnych string resources rozwiązały się. Licznik odniesień Kotlin jest wyłącznie wskazówką: klucze literalne i Compose działają bez R.string, a kategorie nie muszą mieć listenera. Zero odniesień nie oznacza martwej opcji. Dynamiczne ustawienia MPV/Lua/Compose obejmują osobne moduły i istniejące regresje importów, slotów, wartości i Undo.

| XML | Klucz | Domyślna wartość XML | Odniesienia symbolu Kotlin |
|---|---|---|---:|
| preferences_about.xml | `pref_version` | — | 0 |
| preferences_about.xml | `pref_version_geckoview` | — | 0 |
| preferences_about.xml | `pref_version_mozac` | — | 0 |
| preferences_advanced.xml | `remote_debugging` | false | 1 |
| preferences_advanced.xml | `trust_third_party_certs` | false | 1 |
| preferences_advanced.xml | `prompt_external_downloader` | false | 1 |
| preferences_advanced.xml | `sideload_xpi` | — | 2 |
| preferences_advanced.xml | `sideload_xpi_file` | — | 1 |
| preferences_advanced.xml | `use_custom_collection` | false | 1 |
| preferences_advanced.xml | `collection_user` | — | 4 |
| preferences_advanced.xml | `collection_name` | — | 4 |
| preferences_customization.xml | `app_theme_type` | — | 1 |
| preferences_customization.xml | `dynamic_colors` | true | 0 |
| preferences_customization.xml | `amoled_mode` | false | 0 |
| preferences_customization.xml | `web_theme_type` | — | 1 |
| preferences_customization.xml | `homepage_background_image` | — | 2 |
| preferences_customization.xml | `nira_toolbar_nav_section` | — | 0 |
| preferences_customization.xml | `nira_address_height_dp` | 56 | 0 |
| preferences_customization.xml | `nira_tab_strip_height_dp` | 48 | 0 |
| preferences_customization.xml | `nira_group_chips_visible` | true | 0 |
| preferences_customization.xml | `nira_group_bar_bottom` | false | 0 |
| preferences_customization.xml | `nira_toolbar_action_slot_dp` | 40 | 0 |
| preferences_customization.xml | `nira_address_min_width_dp` | 92 | 0 |
| preferences_customization.xml | `nira_toolbar_drag_hint` | — | 0 |
| preferences_customization.xml | `nira_restore_bar_heights` | — | 0 |
| preferences_customization.xml | `show_tab_group_bar` | true | 1 |
| preferences_customization.xml | `Toolbar Icon Size` | — | 2 |
| preferences_customization.xml | `show_tabs_in_grid` | true | 0 |
| preferences_customization.xml | `swap_drawers` | true | 0 |
| preferences_customization.xml | `stack_from_bottom` | true | 1 |
| preferences_customization.xml | `shortcuts_visible` | true | 1 |
| preferences_customization.xml | `load_shortcut_icons` | true | 1 |
| preferences_customization.xml | `keep_wallpaper_dimmed` | true | 0 |
| preferences_customization.xml | `show_url_protocol` | false | 1 |
| preferences_customization.xml | `auto_font_size` | true | 1 |
| preferences_customization.xml | `font_size_factor` | 100 | 2 |
| preferences_customization.xml | `Interface Font Size` | — | 2 |
| preferences_customization.xml | `nira_edge_panels_enabled` | true | 0 |
| preferences_customization.xml | `nira_edge_width_dp` | 36 | 0 |
| preferences_customization.xml | `swipe_refresh` | true | 1 |
| preferences_general.xml | `search_engine` | — | 1 |
| preferences_general.xml | `private_search_engine` | — | 1 |
| preferences_general.xml | `search_suggestions_enabled` | true | 1 |
| preferences_general.xml | `search_suggestion_count` | 5 | 1 |
| preferences_general.xml | `tracking_protection` | true | 1 |
| preferences_general.xml | `safe_browsing` | true | 1 |
| preferences_general.xml | `homepage_type` | — | 1 |
| preferences_general.xml | `desktop_mode_default` | false | 1 |
| preferences_general.xml | `translations_enabled` | true | 1 |
| preferences_general.xml | `javascript_enabled` | true | 1 |
| preferences_headers.xml | `addons_settings` | — | 0 |
| preferences_import_export.xml | `import_bookmarks` | — | 1 |
| preferences_import_export.xml | `export_bookmarks` | — | 1 |
| preferences_import_export.xml | `clear_bookmarks` | — | 1 |
| preferences_import_export.xml | `import_settings` | — | 1 |
| preferences_import_export.xml | `export_settings` | — | 1 |
| preferences_import_export.xml | `clear_settings` | — | 1 |
| preferences_privacy_and_security.xml | `etp_level` | — | 1 |
| preferences_privacy_and_security.xml | `etp_custom_categories` | — | 0 |
| preferences_privacy_and_security.xml | `etp_tracking_ads` | true | 0 |
| preferences_privacy_and_security.xml | `etp_tracking_content` | true | 0 |
| preferences_privacy_and_security.xml | `etp_cryptominers` | true | 0 |
| preferences_privacy_and_security.xml | `etp_fingerprinters` | true | 0 |
| preferences_privacy_and_security.xml | `etp_social_tracking` | true | 0 |
| preferences_privacy_and_security.xml | `etp_email_tracking` | true | 0 |
| preferences_privacy_and_security.xml | `https_only_mode` | — | 2 |
| preferences_privacy_and_security.xml | `doh_mode` | — | 6 |
| preferences_privacy_and_security.xml | `doh_provider_url` | — | 4 |
| preferences_privacy_and_security.xml | `global_privacy_control` | true | 1 |
| preferences_privacy_and_security.xml | `sleeping_tabs_mode` | — | 2 |
| preferences_privacy_and_security.xml | `clear_history` | — | 1 |
| preferences_privacy_and_security.xml | `clear_cookies` | — | 1 |
| preferences_privacy_and_security.xml | `clear_cache` | — | 1 |
| preferences_privacy_and_security.xml | `clear_tabs` | — | 1 |
| preferences_privacy_and_security.xml | `site_exceptions` | — | 0 |
| preferences_privacy_and_security.xml | `clear_permissions` | — | 1 |
