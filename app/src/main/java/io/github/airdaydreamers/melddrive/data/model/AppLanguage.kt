package io.github.airdaydreamers.melddrive.data.model

data class AppLanguage(val code: String, val displayName: String) {
    companion object {
        val supportedLanguages = listOf(
            AppLanguage("en", "English"),
            AppLanguage("ar", "العربية (Arabic)"),
            AppLanguage("bn", "বাংলা (Bengali)"),
            AppLanguage("cs", "Čeština (Czech)"),
            AppLanguage("de", "Deutsch (German)"),
            AppLanguage("es", "Español (Spanish)"),
            AppLanguage("fa", "فارسی (Persian)"),
            AppLanguage("fr", "Français (French)"),
            AppLanguage("gu", "ગુજરાતી (Gujarati)"),
            AppLanguage("hi", "हिन्दी (Hindi)"),
            AppLanguage("id", "Bahasa Indonesia (Indonesian)"),
            AppLanguage("it", "Italiano (Italian)"),
            AppLanguage("ja", "日本語 (Japanese)"),
            AppLanguage("jv", "Basa Jawa (Javanese)"),
            AppLanguage("kn", "ಕನ್ನಡ (Kannada)"),
            AppLanguage("ko", "한국어 (Korean)"),
            AppLanguage("ml", "മലയാളം (Malayalam)"),
            AppLanguage("mr", "मराठी (Marathi)"),
            AppLanguage("my", "မြန်မာစာ (Burmese)"),
            AppLanguage("nl", "Nederlands (Dutch)"),
            AppLanguage("pl", "Polski (Polish)"),
            AppLanguage("pt", "Português (Portuguese)"),
            AppLanguage("ro", "Română (Romanian)"),
            AppLanguage("ru", "Русский (Russian)"),
            AppLanguage("sv", "Svenska (Swedish)"),
            AppLanguage("ta", "தமிழ் (Tamil)"),
            AppLanguage("te", "తెలుగు (Telugu)"),
            AppLanguage("th", "ไทย (Thai)"),
            AppLanguage("tr", "Türkçe (Turkish)"),
            AppLanguage("uk", "Українська (Ukrainian)"),
            AppLanguage("ur", "اردو (Urdu)"),
            AppLanguage("vi", "Tiếng Việt (Vietnamese)"),
            AppLanguage("zh", "中文 (Chinese)"),
        )
    }
}
