package com.plutonem.utilities

import java.util.*
import javax.inject.Inject

class LocaleManagerWrapper
@Inject constructor() {
    fun getLocale(): Locale = Locale.getDefault()
    fun getCurrentCalendar(): Calendar = Calendar.getInstance(getLocale())
}