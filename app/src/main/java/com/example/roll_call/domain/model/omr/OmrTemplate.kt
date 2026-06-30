package com.example.roll_call.domain.model.omr

import java.io.Serializable

/** Bubble coordinates are expressed in the warped template coordinate space. */
data class BubbleRegion(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val radius: Float
) : Serializable

data class DigitBubbleColumn(
    val columnIndex: Int,
    val bubblesByDigit: Map<Int, BubbleRegion>
) : Serializable

data class AnswerBubbleRow(
    val questionNumber: Int,
    val bubblesByOption: Map<String, BubbleRegion>
) : Serializable

data class OmrTemplate(
    val name: String,
    val warpedWidth: Int,
    val warpedHeight: Int,
    val filledThreshold: Double,
    val blankThreshold: Double,
    val uncertainDelta: Double,
    val minSharpnessVariance: Double,
    val examCodeColumns: List<DigitBubbleColumn>,
    val studentIdColumns: List<DigitBubbleColumn>,
    val answerRows: List<AnswerBubbleRow>
) : Serializable

object FixedOmrTemplate {
    val default: OmrTemplate = buildDefaultTemplate()

    private fun buildDefaultTemplate(): OmrTemplate {
        val digitRowsY = listOf(
            273f, 304f, 335f, 366f, 397f,
            428f, 459f, 489f, 520f, 550f
        )
        val examCodeX = listOf(88f, 122f, 156f)
        val studentIdX = listOf(256f, 289f, 322f, 356f, 390f, 424f)

        val answerRowsY = digitRowsY
        val optionGroupsX = listOf(
            listOf(527f, 562f, 596f, 631f),
            listOf(697f, 731f, 764f, 798f),
            listOf(866f, 899f, 934f, 967f),
            listOf(1035f, 1069f, 1103f, 1137f)
        )
        val options = listOf("A", "B", "C", "D")

        return OmrTemplate(
            name = "fixed_40_question_sheet_v1",
            warpedWidth = 1200,
            warpedHeight = 850,
            filledThreshold = 0.28,
            blankThreshold = 0.12,
            uncertainDelta = 0.08,
            minSharpnessVariance = 35.0,
            examCodeColumns = buildDigitColumns("exam", examCodeX, digitRowsY, 12f),
            studentIdColumns = buildDigitColumns("student", studentIdX, digitRowsY, 12f),
            answerRows = optionGroupsX.flatMapIndexed { groupIndex, xs ->
                answerRowsY.mapIndexed { rowIndex, y ->
                    val questionNumber = groupIndex * 10 + rowIndex + 1
                    AnswerBubbleRow(
                        questionNumber = questionNumber,
                        bubblesByOption = options.mapIndexed { optionIndex, option ->
                            option to BubbleRegion(
                                id = "q${questionNumber}_$option",
                                centerX = xs[optionIndex],
                                centerY = y,
                                radius = 12f
                            )
                        }.toMap()
                    )
                }
            }
        )
    }

    private fun buildDigitColumns(
        prefix: String,
        xs: List<Float>,
        ys: List<Float>,
        radius: Float
    ): List<DigitBubbleColumn> {
        return xs.mapIndexed { columnIndex, x ->
            DigitBubbleColumn(
                columnIndex = columnIndex,
                bubblesByDigit = ys.mapIndexed { digit, y ->
                    digit to BubbleRegion(
                        id = "${prefix}_${columnIndex}_$digit",
                        centerX = x,
                        centerY = y,
                        radius = radius
                    )
                }.toMap()
            )
        }
    }
}
