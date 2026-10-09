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

  /// "N/50 Scrips" label on a user watchlist tab.
  static const String watchlistScripCount = 'watchlist_scrip_count';
  static const String watchlistViewToggleButton = 'watchlist_view_toggle_button';
  static const String watchlistListView = 'watchlist_list_view';
  static const String watchlistGridView = 'watchlist_grid_view';
  static const String watchlistEmptyState = 'watchlist_empty_state';
  static const String watchlistScripRow = 'watchlist_scrip_row';
  static const String watchlistFilterButton = 'watchlist_filter_button';
  static const String watchlistFilterSheet = 'watchlist_filter_sheet';

  /// Screen markers published from the target app's router.
  static const String editWatchlistScreen = 'edit_watchlist_screen';
  static const String searchScreen = 'search_screen';
  static const String scripDetailsScreen = 'scrip_details_screen';
  static const String indexDetailsScreen = 'index_details_screen';

  /// Edit Watchlist per-row delete icon. Same identifier every row.
  static const String editWatchlistDeleteButton = 'edit_watchlist_delete_button';
  static const String editWatchlistSaveButton = 'edit_watchlist_save_button';

  static const String watchlistSearchButton = 'watchlist_search_button';
  static const String searchScripInput = 'search_scrip_input';
  static const String searchBackButton = 'search_back_button';
  static const String searchFilterChipAll = 'search_filter_chip_all';
  static const String searchFilterChipStock = 'search_filter_chip_stock';
  static const String searchFilterChipFutures = 'search_filter_chip_futures';
  static const String searchFilterChipOptions = 'search_filter_chip_options';
  static const String searchFilterChipCommodity = 'search_filter_chip_commodity';
  static const String searchFilterChipMutualFunds = 'search_filter_chip_mutual_funds';
  static const String searchLoading = 'search_loading';
  static const String searchResultsList = 'search_results_list';

  /// Search result row's "+" icon. Tagged only while not yet added.
  static const String searchScripAddButton = 'search_scrip_add_button';

  /// The tick that replaces "+" once a result is in the watchlist.
  static const String searchScripAddedIcon = 'search_scrip_added_icon';
  static const String mfSearchResults = 'mf_search_results';

  /// Bottom tabs: `bottomnav_<label>_tab` (sip replaces research in MF mode).
  static const String bottomnavHomeTab = 'bottomnav_home_tab';
  static const String bottomnavResearchTab = 'bottomnav_research_tab';
  static const String bottomnavPortfolioTab = 'bottomnav_portfolio_tab';
  static const String bottomnavOrderTab = 'bottomnav_order_tab';
  static const String dashboardScreen = 'dashboard_screen';
  static const String researchScreen = 'research_screen';
  static const String portfolioScreen = 'portfolio_screen';
  static const String orderbookScreen = 'orderbook_screen';

  /// Inner tab prefixes; suffix is the label in lower case, `_` for the rest.
  static const String homeTabPrefix = 'home_tab_';
  static const String portfolioTabPrefix = 'portfolio_tab_';
  static const String holdingsTabPrefix = 'holdings_tab_';
  static const String orderbookTabPrefix = 'orderbook_tab_';
  static const String ordersTabPrefix = 'orders_tab_';

  /// Load-state markers on the shared shimmer / spinner, empty and retry widgets.
  static const String uiLoading = 'ui_loading';
  static const String uiEmpty = 'ui_empty';
  static const String uiError = 'ui_error';

  static const String profileButton = 'profile_button';
  static const String profileScreen = 'profile_screen';
  static const String profileLogoutButton = 'profile_logout_button';
  static const String logoutConfirmButton = 'logout_confirm_button';

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
    watchlistScripCount,
    watchlistViewToggleButton,
    watchlistListView,
    watchlistGridView,
    watchlistEmptyState,
    watchlistScripRow,
    watchlistFilterButton,
    watchlistFilterSheet,
    editWatchlistScreen,
    searchScreen,
    scripDetailsScreen,
    indexDetailsScreen,
    editWatchlistDeleteButton,
    editWatchlistSaveButton,
    watchlistSearchButton,
    searchScripInput,
    searchBackButton,
    searchFilterChipAll,
    searchFilterChipStock,
    searchFilterChipFutures,
    searchFilterChipOptions,
    searchFilterChipCommodity,
    searchFilterChipMutualFunds,
    searchLoading,
    searchResultsList,
    searchScripAddButton,
    searchScripAddedIcon,
    mfSearchResults,
    bottomnavHomeTab,
    bottomnavResearchTab,
    bottomnavPortfolioTab,
    bottomnavOrderTab,
    dashboardScreen,
    researchScreen,
    portfolioScreen,
    orderbookScreen,
    uiLoading,
    uiEmpty,
    uiError,
    profileButton,
    profileScreen,
    profileLogoutButton,
    logoutConfirmButton,
  ];
}

/// The checklist, in execution order. Mirrors `LoginAutomationFlow.steps`.
class AutomationChecklist {
  const AutomationChecklist._();

  static const List<({String id, String name})> steps = [
    (id: 'target_app_installed', name: 'Target application installed'),
    (id: 'accessibility_enabled', name: 'Accessibility permission enabled'),
    (id: 'target_app_data_cleared', name: 'Target application data cleared'),
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
    (id: 'search_filter_chips_listed', name: 'Search filter chips listed'),
    (id: 'search_tcs_added', name: 'TCS added (Stock filter)'),
    (id: 'search_nifty_futures_added', name: 'NIFTY futures added (Futures filter)'),
    (id: 'search_nifty_options_added', name: 'NIFTY options added (Options filter)'),
    (id: 'search_crudeoil_added', name: 'Crude oil added (Commodity filter)'),
    (id: 'search_all_filter_results', name: 'All filter lists results'),
    (id: 'search_mutual_fund_results', name: 'Mutual fund results listed'),
    (id: 'search_closed', name: 'Search screen closed'),
    (id: 'watchlist_count_updated', name: 'Watchlist count reflects the adds'),
    (id: 'watchlist_view_toggled', name: 'Heat map view toggled and restored'),
    (id: 'watchlist_filter_sheet_opened', name: 'Filter & Sorting sheet opened and closed'),
    (id: 'scrip_details_opened', name: 'Scrip details opened and closed'),
    (id: 'home_overview_loaded', name: 'Home › Overview loaded'),
    (id: 'home_stocks_loaded', name: 'Home › Stocks loaded'),
    (id: 'home_fno_loaded', name: 'Home › F&O loaded'),
    (id: 'home_mutual_funds_loaded', name: 'Home › Mutual Funds loaded'),
    (id: 'home_commodity_loaded', name: 'Home › Commodity loaded'),
    (id: 'research_loaded', name: 'Research loaded'),
    (id: 'holdings_overview_loaded', name: 'Portfolio › Holdings › Overview loaded'),
    (id: 'holdings_equity_loaded', name: 'Portfolio › Holdings › Equity loaded'),
    (id: 'holdings_thematic_loaded', name: 'Portfolio › Holdings › Thematic Basket loaded'),
    (id: 'holdings_mutual_funds_loaded', name: 'Portfolio › Holdings › Mutual Funds loaded'),
    (id: 'portfolio_my_wealth_loaded', name: 'Portfolio › My Wealth loaded'),
    (id: 'orders_open_loaded', name: 'Orders › Open loaded'),
    (id: 'orders_executed_loaded', name: 'Orders › Executed loaded'),
    (id: 'orders_gtt_loaded', name: 'Orders › GTT loaded'),
    (id: 'orders_sip_loaded', name: 'Orders › SIP loaded'),
    (id: 'orders_basket_loaded', name: 'Orders › Basket loaded'),
    (id: 'orders_alerts_loaded', name: 'Orders › Alerts loaded'),
    (id: 'positions_loaded', name: 'Orders › Positions loaded'),
    (id: 'profile_opened', name: 'Profile opened'),
    (id: 'logged_out', name: 'Logged out'),
  ];

  static const String testName = 'Phase 1 - Login Automation';
}
