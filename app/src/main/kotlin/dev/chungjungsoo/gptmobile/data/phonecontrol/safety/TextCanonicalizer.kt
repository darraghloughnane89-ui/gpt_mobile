package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

import java.text.Normalizer

object TextCanonicalizer {
    private val confusables = mapOf(
        '\u0430' to 'a',
        '\u0410' to 'A',
        '\u0435' to 'e',
        '\u0415' to 'E',
        '\u043E' to 'o',
        '\u041E' to 'O',
        '\u0440' to 'p',
        '\u0420' to 'P',
        '\u0441' to 'c',
        '\u0421' to 'C',
        '\u0443' to 'y',
        '\u0423' to 'Y',
        '\u0445' to 'x',
        '\u0425' to 'X',
        '\u0456' to 'i',
        '\u0406' to 'I',
        '\u0455' to 's',
        '\u0405' to 'S',
        '\u0458' to 'j',
        '\u03BF' to 'o',
        '\u039F' to 'O',
        '\u03B1' to 'a',
        '\u0391' to 'A',
        '\u03B5' to 'e',
        '\u0395' to 'E',
        '\u03C1' to 'p',
        '\u03A1' to 'P'
    )

    fun fold(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val builder = StringBuilder(normalized.length)
        for (character in normalized) {
            when {
                character == '\n' || character == '\t' -> builder.append(character)
                character.category == CharCategory.FORMAT || character.isISOControl() -> Unit
                character.isWhitespace() -> builder.append(' ')
                else -> builder.append(confusables[character] ?: character)
            }
        }
        return builder.toString()
    }
}
