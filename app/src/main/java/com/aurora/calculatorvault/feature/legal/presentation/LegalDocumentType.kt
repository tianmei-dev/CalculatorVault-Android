package com.aurora.calculatorvault.feature.legal.presentation

import androidx.annotation.StringRes
import com.aurora.calculatorvault.R

enum class LegalDocumentType(
    val routeValue: String,
    @StringRes val titleRes: Int,
    val assetPath: String,
) {
    PrivacyPolicy(
        routeValue = "privacy_policy",
        titleRes = R.string.privacy_policy,
        assetPath = "legal/privacy_policy.txt",
    ),
    UserAgreement(
        routeValue = "user_agreement",
        titleRes = R.string.user_agreement,
        assetPath = "legal/user_agreement.txt",
    ),
    PersonalInformationList(
        routeValue = "personal_information_list",
        titleRes = R.string.legal_personal_information_list,
        assetPath = "legal/personal_information_list.txt",
    ),
    ThirdPartySdkList(
        routeValue = "third_party_sdk_list",
        titleRes = R.string.legal_third_party_sdk_list,
        assetPath = "legal/third_party_sdk_list.txt",
    ),
    PermissionDescription(
        routeValue = "permission_description",
        titleRes = R.string.legal_permission_description,
        assetPath = "legal/permission_description.txt",
    );

    companion object {
        fun fromRouteValue(value: String?): LegalDocumentType =
            entries.firstOrNull { it.routeValue == value } ?: PrivacyPolicy
    }
}
