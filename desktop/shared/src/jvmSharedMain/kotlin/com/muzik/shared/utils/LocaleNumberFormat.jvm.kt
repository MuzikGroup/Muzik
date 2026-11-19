package com.muzik.shared.utils

import java.text.DecimalFormatSymbols

internal actual fun localeDecimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
