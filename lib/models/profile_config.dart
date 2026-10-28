/// One check of the Profile module, as offered on the module page.
class ProfileCheck {
  const ProfileCheck(this.id, this.code, this.label, this.hint);

  /// The driver's step id (ProfileSuite.CASES).
  final String id;
  final String code;
  final String label;

  /// What the check does, and whether it changes (and restores) anything.
  final String hint;
}

/// The settings of the Profile module: which of its checks a run makes. Stored as the checks switched off, so a check
/// added later starts switched on.
class ProfileConfig {
  const ProfileConfig({this.off = const <String>{}});

  factory ProfileConfig.defaults() => const ProfileConfig();

  final Set<String> off;

  /// The Profile tab of the app's Settings screen.
  static const List<ProfileCheck> profileTab = <ProfileCheck>[
    ProfileCheck(
      'profile_account_loaded',
      'PF-01',
      'Profile tab loads',
      'The profile card and every row are shown.',
    ),
    ProfileCheck(
      'profile_details_opened',
      'PF-02',
      'Account Details',
      'Email, phone, PAN, Demat filled; segment statuses; bank details sheet. Values are never recorded.',
    ),
    ProfileCheck(
      'profile_switch_account_sheet',
      'PF-03',
      'Switch Account',
      'The sheet opens and closes; no account is chosen.',
    ),
    ProfileCheck(
      'profile_funds_opened',
      'PF-04',
      'Funds',
      'Funds loads with Add Funds and Withdraw; Withdraw opens and is left.',
    ),
    ProfileCheck(
      'profile_add_funds_opened',
      'PF-05',
      'Add Funds',
      'Opens and is left; nothing is paid.',
    ),
    ProfileCheck(
      'profile_exchange_messages',
      'PF-06',
      'Exchange Messages',
      'Load test: every exchange tab shows its first 10 messages from the server (the rest skipped), then a rapid pass through all tabs.',
    ),
    ProfileCheck(
      'profile_market_status',
      'PF-07',
      'Markets Status',
      'Every exchange shows a status.',
    ),
    ProfileCheck(
      'profile_customer_support',
      'PF-08',
      'Customer Support',
      'Call Us, Email Us, Company page; no call, mail or web link is opened.',
    ),
    ProfileCheck(
      'profile_rate_app',
      'PF-09',
      'Rate Our App',
      'Opens with Submit; nothing is submitted.',
    ),
    ProfileCheck(
      'profile_footer_info',
      'PF-10',
      'Company and version details',
      'Registration lines, app and build version, follow-us links.',
    ),
    ProfileCheck(
      'profile_qr_scan',
      'PF-21',
      'QR login scanner',
      'The header scan icon opens the scanner. Needs the camera allowed (the tester decides).',
    ),
    ProfileCheck(
      'logged_out',
      'PF-22',
      'Logout',
      'Runs last: Logout asks for confirmation, Yes signs out and the sign-in screen follows. Off: the app is left signed in.',
    ),
  ];

  /// The Settings tab of the app's Settings screen.
  static const List<ProfileCheck> settingsTab = <ProfileCheck>[
    ProfileCheck(
      'profile_trade_settings_loaded',
      'PF-11',
      'Settings tab loads',
      'Every switch and row is shown.',
    ),
    ProfileCheck(
      'profile_toggle_fixed_header',
      'PF-12',
      'Fixed Headers switch',
      'Switched over and back (changes and restores the setting).',
    ),
    ProfileCheck(
      'profile_toggle_notification',
      'PF-13',
      'Notifications switch',
      'Switched over and back; saved on the server each time.',
    ),
    ProfileCheck(
      'profile_toggle_order_preview',
      'PF-14',
      'Order preview switch',
      'Switched over and back; saved on the server each time.',
    ),
    ProfileCheck(
      'profile_toggle_theme',
      'PF-15',
      'Dark Theme switch',
      'Switched over and back; screenshots taken at once confirm the Settings, Profile and Exchange Messages screens really turn dark and back.',
    ),
    ProfileCheck(
      'profile_font_size',
      'PF-16',
      'Font sizes',
      'Another size is chosen, then the original restored.',
    ),
    ProfileCheck(
      'profile_security_settings',
      'PF-17',
      'Security Settings',
      'Change Password refuses empty fields; nothing is sent.',
    ),
    ProfileCheck(
      'profile_trade_preferences',
      'PF-18',
      'Trade Settings (order preferences)',
      'Segment tabs, quantity + / − and the Save state; nothing is saved.',
    ),
    ProfileCheck(
      'profile_accessibility_settings',
      'PF-19',
      'Accessibility Settings',
      'Every section; four switches changed and restored.',
    ),
    ProfileCheck(
      'profile_guarded_rows',
      'PF-20',
      'TOTP, Freeze, Developer Console',
      'Checked for presence and state only; never tapped.',
    ),
  ];

  static List<ProfileCheck> get all => <ProfileCheck>[
    ...profileTab,
    ...settingsTab,
  ];

  bool isOn(String id) => !off.contains(id);

  int get onCount => all.where((c) => isOn(c.id)).length;

  ProfileConfig withCheck(String id, bool on) {
    final next = Set<String>.of(off);
    on ? next.remove(id) : next.add(id);
    return ProfileConfig(off: next);
  }

  /// Switches every check of [checks] on or off at once.
  ProfileConfig withAll(List<ProfileCheck> checks, bool on) {
    final next = Set<String>.of(off);
    for (final c in checks) {
      on ? next.remove(c.id) : next.add(c.id);
    }
    return ProfileConfig(off: next);
  }

  /// "All 22 checks", "None", or "15 of 22 checks: Profile tab 12, Settings tab 3".
  String get summary {
    final total = all.length;
    if (onCount == total) return 'All $total checks';
    if (onCount == 0) return 'None: every Profile check is switched off';
    final p = profileTab.where((c) => isOn(c.id)).length;
    final s = settingsTab.where((c) => isOn(c.id)).length;
    return '$onCount of $total checks: Profile tab $p, Settings tab $s';
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'off': (off.toList()..sort()),
  };

  factory ProfileConfig.fromJson(Object? json) {
    if (json is! Map) return ProfileConfig.defaults();
    final raw = json['off'];
    final known = all.map((c) => c.id).toSet();
    return ProfileConfig(
      off: raw is List
          ? raw.whereType<String>().where(known.contains).toSet()
          : const <String>{},
    );
  }
}
