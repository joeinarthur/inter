package com.internshipuncle.domain.model

import kotlin.math.roundToInt

data class MockInterviewAnswerFeedback(
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val missingPoints: List<String> = emptyList(),
    val followUp: String? = null,
    val improvedAnswer: String? = null
)

data class MockInterviewAnswerEvaluation(
    val questionId: String,
    val answerText: String,
    val score: Int,
    val feedback: MockInterviewAnswerFeedback = MockInterviewAnswerFeedback(),
    val improvedAnswer: String? = null
)

data class MockInterviewQuestionProgress(
    val id: String,
    val question: String,
    val category: String? = null,
    val sequenceNo: Int,
    val expectedPoints: List<String> = emptyList(),
    val answer: MockInterviewAnswerEvaluation? = null
) {
    val isAnswered: Boolean
        get() = answer != null
}

data class MockInterviewSessionDetail(
    val id: String,
    val targetJobId: String? = null,
    val roleName: String? = null,
    val difficulty: String? = null,
    val mode: String? = null,
    val overallScore: Int? = null,
    val createdAt: String? = null,
    val questions: List<MockInterviewQuestionProgress> = emptyList()
) {
    val answeredCount: Int
        get() = questions.count(MockInterviewQuestionProgress::isAnswered)

    val totalQuestions: Int
        get() = questions.size

    val unansweredQuestions: List<MockInterviewQuestionProgress>
        get() = questions.filterNot(MockInterviewQuestionProgress::isAnswered)
}

data class MockInterviewPracticeSummary(
    val overallScore: Int?,
    val strongestArea: String,
    val weakestArea: String,
    val nextStepSuggestions: List<String>,
    val answeredCount: Int,
    val skippedCount: Int,
    val totalQuestions: Int
)

fun MockInterviewSessionDetail.toPracticeSummary(skippedCount: Int): MockInterviewPracticeSummary {
    val answeredQuestions = questions.filter { it.answer != null }
    val scores = answeredQuestions.mapNotNull { it.answer?.score }
    val overall = overallScore ?: scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    val categoryScores = answeredQuestions.mapNotNull { question ->
        val category = question.category?.trim().orEmpty()
        val score = question.answer?.score ?: return@mapNotNull null
        if (category.isBlank()) return@mapNotNull null
        category to score
    }

    val strongestArea = categoryScores
        .groupBy({ it.first }, { it.second })
        .maxByOrNull { entry -> entry.value.average() }
        ?.key
        ?: answeredQuestions.maxByOrNull { it.answer?.score ?: Int.MIN_VALUE }
            ?.category
            ?.takeIf(String::isNotBlank)
        ?: "Answer structure"

    val weakestArea = categoryScores
        .groupBy({ it.first }, { it.second })
        .minByOrNull { entry -> entry.value.average() }
        ?.key
        ?: answeredQuestions.minByOrNull { it.answer?.score ?: Int.MAX_VALUE }
            ?.category
            ?.takeIf(String::isNotBlank)
        ?: "Answer structure"

    val weakestQuestion = answeredQuestions.minByOrNull { it.answer?.score ?: Int.MAX_VALUE }
    val suggestions = buildList {
        if (weakestQuestion?.answer?.feedback?.missingPoints?.isNotEmpty() == true) {
            addAll(weakestQuestion.answer!!.feedback.missingPoints.take(2))
        }
        if (weakestQuestion?.answer?.feedback?.weaknesses?.isNotEmpty() == true) {
            addAll(weakestQuestion.answer!!.feedback.weaknesses.take(2))
        }
        if (skippedCount > 0) {
            add("Revisit the skipped questions before running the session again.")
        }
        if (isEmpty()) {
            add("Practice the weakest area again with a shorter answer and one concrete example.")
            add("Run the same mode once more to see if your score stabilizes.")
        }
    }.distinct()

    return MockInterviewPracticeSummary(
        overallScore = overall,
        strongestArea = strongestArea.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        weakestArea = weakestArea.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
        nextStepSuggestions = suggestions,
        answeredCount = answeredQuestions.size,
        skippedCount = skippedCount,
        totalQuestions = totalQuestions
    )
}
