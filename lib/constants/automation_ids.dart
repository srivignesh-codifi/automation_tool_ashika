/// Accessibility identifiers the target UAT application publishes through
/// `Semantics(identifier: ...)`, and the checklist the Phase 1 login flow
/// produces.
///
/// The native driver in `android/app/src/main/kotlin/.../AutomationConfig.kt`
/// holds the authoritative copies — these exist so the Flutter UI can describe
/// the flow before a run starts and label a run that never reported back. Keep
/// the two in step.
library;

class AutomationIds {
  const AutomationIds._();

  static const String introLoginButton = 'intro_login_button';

  static const String clientIdScreen = 'client_id_screen';
  static const String clientIdInput = 'client_id_input';
  static const String clientIdContinueButton = 'client_id_continue_button';

  static const String otpScreen = 'otp_screen';
  static const String otpInput = 'otp_input';
  static const String otpSubmitButton = 'otp_submit_button';

  static const String mpinScreen = 'mpin_screen';
  static const String mpinInput = 'mpin_input';
  static const String mpinSubmitButton = 'mpin_submit_button';
  static const String mpinKeyPrefix = 'mpin_key_';

  static const String biometricScreen = 'biometric_screen';
  static const String biometricSkipButton = 'biometric_skip_button';

  static const String homeScreen = 'home_screen';
  static const String watchlistScreen = 'watchlist_screen';

  /// Raised over Home right after login; see the note in AutomationConfig.kt.
  static const String riskDisclosureDialog = 'risk_disclosure_dialog';

  /// Same identifier on every page of the disclosure dialog.
  static const String disclosureUnderstandButton = 'disclosure_understand_button';

  static const String bottomnavWatchlistTab = 'bottomnav_watchlist_tab';

  /// The tab immediately after "Discover" — the user's first watchlist.
  static const String watchlistFirstTab = 'watchlist_first_tab';

  /// Discover row's "+" icon. Same identifier every row.
  static const String discoverScripAddButton = 'discover_scrip_add_button';

  /// Positional prefix: row N's checkbox in the "Add scrip to" dialog.
  static const String watchlistCheckboxPrefix = 'watchlist_checkbox_';

  static const String watchlistAddSaveButton = 'watchlist_add_save_button';
  static const String watchlistEditButton = 'watchlist_edit_button';

  /// Edit Watchlist per-row delete icon. Same identifier every row.
  static const String editWatchlistDeleteButton = 'edit_watchlist_delete_button';
  static const String editWatchlistSaveButton = 'edit_watchlist_save_button';

  static const String watchlistSearchButton = 'watchlist_search_button';
  static const String searchScripInput = 'search_scrip_input';
  static const String searchFilterChipAll = 'search_filter_chip_all';
  static const String searchFilterChipFutures = 'search_filter_chip_futures';
  static const String searchFilterChipOptions = 'search_filter_chip_options';

  /// Search result row's "+" icon. Tagged only while not yet added.
  static const String searchScripAddButton = 'search_scrip_add_button';

  static const List<String> all = <String>[
    introLoginButton,
    clientIdScreen,
    clientIdInput,
    clientIdContinueButton,
    otpScreen,
    otpInput,
    otpSubmitButton,
    mpinScreen,
    mpinInput,
    mpinSubmitButton,
    biometricScreen,
    biometricSkipButton,
    homeScreen,
    watchlistScreen,
    riskDisclosureDialog,
    disclosureUnderstandButton,
    bottomnavWatchlistTab,
    watchlistFirstTab,
    discoverScripAddButton,
    watchlistAddSaveButton,
    watchlistEditButton,
    editWatchlistDeleteButton,
    editWatchlistSaveButton,
    watchlistSearchButton,
    searchScripInput,
    searchFilterChipAll,
    searchFilterChipFutures,
    searchFilterChipOptions,
    searchScripAddButton,
  ];
}

/// The checklist, in execution order. Mirrors `LoginAutomationFlow.steps`.
class AutomationChecklist {
  const AutomationChecklist._();

  static const List<({String id, String name})> steps = [
    (id: 'target_app_installed', name: 'Target application installed'),
    (id: 'accessibility_enabled', name: 'Accessibility permission enabled'),
    (id: 'target_app_launched', name: 'Target application launched'),
    (id: 'intro_login_tapped', name: 'Intro screen Login tapped'),
    (id: 'client_id_screen_displayed', name: 'Client ID screen displayed'),
    (id: 'client_id_input_found', name: 'Client ID input found'),
    (id: 'client_id_entered', name: 'Client ID entered'),
    (id: 'continue_tapped', name: 'Continue button tapped'),
    (id: 'otp_screen_displayed', name: 'OTP screen displayed'),
    (id: 'otp_input_found', name: 'OTP input found'),
    (id: 'otp_entered', name: 'OTP entered'),
    (id: 'otp_submitted', name: 'OTP submitted'),
    (id: 'mpin_screen_displayed', name: 'MPIN screen displayed'),
    (id: 'mpin_entered', name: 'MPIN entered'),
    (id: 'mpin_submitted', name: 'MPIN submitted'),
    (id: 'biometric_screen_checked', name: 'Biometric screen checked'),
    (id: 'biometric_skipped', name: 'Biometric skipped'),
    (id: 'home_displayed', name: 'Home or Watchlist displayed'),
    (id: 'disclosure_accepted', name: 'Disclosure dialog accepted'),
    (id: 'watchlist_tab_opened', name: 'Watchlist tab opened'),
    (id: 'discover_scrip_added', name: 'Discover scrip add dialog opened'),
    (id: 'watchlist_checkboxes_toggled', name: 'Watchlist checkboxes toggled'),
    (id: 'watchlist_add_saved', name: 'Add-to-watchlist saved'),
    (id: 'first_watchlist_tab_opened', name: 'First watchlist tab opened'),
    (id: 'watchlist_edit_opened', name: 'Watchlist edit screen opened'),
    (id: 'watchlist_scrips_trimmed', name: 'Watchlist scrips trimmed'),
    (id: 'search_opened', name: 'Search screen opened'),
    (id: 'search_tcs_added', name: 'TCS added from search'),
    (id: 'search_nifty_futures_added', name: 'NIFTY futures added from search'),
    (id: 'search_nifty_options_added', name: 'NIFTY options added from search'),
    (id: 'search_crudeoil_added', name: 'Crude oil added from search'),
    (id: 'search_closed', name: 'Search screen closed'),
  ];

  static const String testName = 'Phase 1 - Login Automation';
}
