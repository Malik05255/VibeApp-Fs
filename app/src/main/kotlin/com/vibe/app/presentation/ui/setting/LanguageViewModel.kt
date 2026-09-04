package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import com.vibe.app.data.preferences.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languageManager: LanguageManager
) : ViewModel() {

    /**
     * اللغة المطبقة حاليًا على التطبيق.
     */
    val language: StateFlow<String> =
        languageManager.language

    /**
     * اللغة المحددة مؤقتًا في واجهة المستخدم.
     *
     * اختيار العربية أو الإنجليزية هنا لا يغير لغة التطبيق
     * حتى يتم استدعاء confirmLanguage().
     */
    private val _selectedLanguage =
        MutableStateFlow(
            normalizeLanguage(
                languageManager.getCurrentLanguage()
            )
        )

    val selectedLanguage: StateFlow<String> =
        _selectedLanguage.asStateFlow()

    /**
     * تحديد اللغة مؤقتًا.
     *
     * لا يتم تطبيق اللغة على التطبيق في هذه المرحلة.
     */
    fun selectLanguage(
        language: String
    ) {
        _selectedLanguage.value =
            normalizeLanguage(language)
    }

    /**
     * تطبيق اللغة المحددة حاليًا.
     *
     * يجب استدعاء هذه الدالة عند الضغط على تأكيد/حفظ.
     */
    fun confirmLanguage() {
        val languageToApply =
            normalizeLanguage(
                _selectedLanguage.value
            )

        _selectedLanguage.value =
            languageToApply

        languageManager.setLanguage(
            languageToApply
        )
    }

    /**
     * تغيير اللغة وتطبيقها مباشرة.
     *
     * هذه الدالة موجودة للتوافق مع الاستدعاءات القديمة.
     */
    fun changeLanguage(
        language: String
    ) {
        val normalizedLanguage =
            normalizeLanguage(language)

        _selectedLanguage.value =
            normalizedLanguage

        languageManager.setLanguage(
            normalizedLanguage
        )
    }

    /**
     * دالة توافق مع الواجهات الحالية.
     *
     * تطبق اللغة مباشرة.
     *
     * إذا كان الاستدعاء من زر التأكيد، يفضل استخدام
     * confirmLanguage() أو selectLanguage() ثم confirmLanguage().
     */
    fun setLanguage(
        language: String
    ) {
        changeLanguage(language)
    }

    /**
     * إرجاع اللغة المحددة مؤقتًا.
     */
    fun getSelectedLanguage(): String {
        return _selectedLanguage.value
    }

    /**
     * التحقق من وجود لغة محفوظة ومطبقة.
     */
    fun isLanguageSelected(): Boolean {
        return languageManager.isLanguageSelected()
    }

    /**
     * توحيد قيم اللغة المقبولة.
     *
     * العربية = ar
     * الإنجليزية = en
     */
    private fun normalizeLanguage(
        language: String
    ): String {
        return when (
            language.trim().lowercase()
        ) {
            "ar",
            "arabic",
            "العربية" -> "ar"

            "en",
            "english",
            "الإنجليزية" -> "en"

            else -> "en"
        }
    }
}
