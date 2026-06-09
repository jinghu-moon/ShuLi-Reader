package com.shuli.reader.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface AppStrings {
    val common: CommonStrings
    val bookshelf: BookshelfStrings
    val reader: ReaderStrings
    val settings: SettingsStrings
    val sync: SyncStrings
    val encryption: EncryptionStrings

    data object ZhHans : AppStrings {
        override val common: CommonStrings = ZhHansCommon
        override val bookshelf: BookshelfStrings = ZhHansBookshelf
        override val reader: ReaderStrings = ZhHansReader
        override val settings: SettingsStrings = ZhHansSettings
        override val sync: SyncStrings = ZhHansSync
        override val encryption: EncryptionStrings = ZhHansEncryption
    }

    data object ZhHant : AppStrings {
        override val common: CommonStrings = ZhHantCommon
        override val bookshelf: BookshelfStrings = ZhHantBookshelf
        override val reader: ReaderStrings = ZhHantReader
        override val settings: SettingsStrings = ZhHantSettings
        override val sync: SyncStrings = ZhHantSync
        override val encryption: EncryptionStrings = ZhHantEncryption
    }

    data object En : AppStrings {
        override val common: CommonStrings = EnCommon
        override val bookshelf: BookshelfStrings = EnBookshelf
        override val reader: ReaderStrings = EnReader
        override val settings: SettingsStrings = EnSettings
        override val sync: SyncStrings = EnSync
        override val encryption: EncryptionStrings = EnEncryption
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { AppStrings.ZhHans }
