package dev.chungjungsoo.gptmobile.presentation.ui.chat

import kotlin.math.roundToInt

data class ModelAnswer(
    val label: String,
    val text: String
)

data class ComparedAnswer(
    val label: String,
    val wordCount: Int,
    val uniqueSentences: List<String>
)

data class ModelComparison(
    val overlapPercent: Int,
    val answers: List<ComparedAnswer>
)

fun labeledModelAnswers(labelAndText: List<Pair<String, String>>): List<ModelAnswer> {
    val usable = labelAndText.filter { (_, text) -> text.isNotBlank() }
    val counts = usable.groupingBy { it.first.ifBlank { "Model" } }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return usable.map { (rawLabel, text) ->
        val label = rawLabel.ifBlank { "Model" }
        val index = seen.getOrDefault(label, 0) + 1
        seen[label] = index
        val uniqueLabel = if (counts.getValue(label) == 1) label else "$label $index"
        ModelAnswer(label = uniqueLabel, text = text)
    }
}

fun compareModelAnswers(answers: List<ModelAnswer>): ModelComparison? {
    val usable = answers.filter { it.label.isNotBlank() && it.text.isNotBlank() }
    if (usable.size < 2) return null
    val wordSets = usable.map { wordsOf(it.text) }
    val overlapPercent = (averageJaccard(wordSets) * 100).roundToInt()
    return ModelComparison(
        overlapPercent = overlapPercent,
        answers = usable.map { answer ->
            val others = usable.filter { it != answer }.map { it.text }
            ComparedAnswer(
                label = answer.label,
                wordCount = wordCount(answer.text),
                uniqueSentences = uniqueSentences(answer.text, others)
            )
        }
    )
}

internal fun wordsOf(text: String): Set<String> = text.lowercase()
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.length > 2 }
    .toSet()

internal fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

internal fun uniqueSentences(answer: String, others: List<String>): List<String> {
    val otherText = others.joinToString(separator = "\n") { normalizeSentence(it) }
    return sentencesOf(answer).filter { sentence ->
        val normalized = normalizeSentence(sentence)
        normalized.isNotEmpty() && !otherText.contains(normalized)
    }.take(MAX_UNIQUE_SENTENCES)
}

private fun sentencesOf(text: String): List<String> = text
    .split(Regex("(?<=[.!?])\\s+|\\n+"))
    .map { it.trim() }
    .filter { it.length >= MIN_SENTENCE_LENGTH }
    .distinct()

private fun normalizeSentence(sentence: String): String = sentence.lowercase().replace(Regex("\\s+"), " ").trim()

private fun averageJaccard(sets: List<Set<String>>): Double {
    if (sets.size < 2) return 0.0
    var total = 0.0
    var pairs = 0
    for (left in sets.indices) {
        for (right in left + 1 until sets.size) {
            total += jaccard(sets[left], sets[right])
            pairs++
        }
    }
    return if (pairs == 0) 0.0 else total / pairs
}

private fun jaccard(left: Set<String>, right: Set<String>): Double {
    if (left.isEmpty() && right.isEmpty()) return 1.0
    val union = (left + right).size
    if (union == 0) return 0.0
    return left.intersect(right).size.toDouble() / union
}

private const val MIN_SENTENCE_LENGTH = 20
private const val MAX_UNIQUE_SENTENCES = 4
