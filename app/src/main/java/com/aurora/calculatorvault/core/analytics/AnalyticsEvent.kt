package com.aurora.calculatorvault.core.analytics

/**
 * Published event IDs are a long-lived data contract. Do not rename them when UI copy changes,
 * otherwise historical series in Umeng will be split.
 */
enum class AnalyticsEvent(val id: String) {
    AppDisguiseClick("app_disguise_click"),
    AppLockClick("app_lock_click"),
    AppLockEnable("app_lock_enable"),
    PrivateAlbumClick("private_album_click"),
    PrivateMediaImportClick("private_media_import_click"),
    PrivateFolderCreateClick("private_folder_create_click"),
    PrivateMediaRestoreClick("private_media_restore_click"),
    ChangePasswordClick("change_password_click"),
    PermissionSettingsClick("permission_settings_click"),
    PrivacyDocumentsClick("privacy_documents_click"),
}
