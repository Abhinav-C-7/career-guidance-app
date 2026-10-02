package app.foreway.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.foreway.R
import app.foreway.domain.model.BoardCodes
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

/** Every class we ask about, in order. */
internal val classOptions = listOf(
    SchoolClass.CLASS_8 to R.string.class_8,
    SchoolClass.CLASS_9 to R.string.class_9,
    SchoolClass.CLASS_10 to R.string.class_10,
    SchoolClass.CLASS_11 to R.string.class_11,
    SchoolClass.CLASS_12 to R.string.class_12,
    SchoolClass.PASSED_12 to R.string.class_passed_12,
)

internal val boardOptions = listOf(
    BoardCodes.CBSE to R.string.board_cbse,
    BoardCodes.CISCE to R.string.board_cisce,
    BoardCodes.STATE to R.string.board_state,
    BoardCodes.NIOS to R.string.board_nios,
    BoardCodes.INTERNATIONAL to R.string.board_international,
    BoardCodes.OTHER to R.string.board_other,
)

/**
 * Day, month, year as three number fields, the way the date is written on an Indian
 * certificate. Shared by onboarding and by "You" when an answer is changed.
 */
@Composable
internal fun DateOfBirthFields(
    day: String,
    month: String,
    year: String,
    problem: DateOfBirthInput.Result?,
    onChange: (day: String, month: String, year: String) -> Unit,
    onDone: () -> Unit,
) {
    val monthFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DigitField(
                label = stringResource(R.string.onb_dob_day),
                value = day,
                onValueChange = { v ->
                    onChange(v, month, year)
                    if (v.length == 2) monthFocus.requestFocus()
                },
                modifier = Modifier.weight(1f),
            )
            DigitField(
                label = stringResource(R.string.onb_dob_month),
                value = month,
                onValueChange = { v ->
                    onChange(day, v, year)
                    if (v.length == 2) yearFocus.requestFocus()
                },
                modifier = Modifier.weight(1f).focusRequester(monthFocus),
            )
            DigitField(
                label = stringResource(R.string.onb_dob_year),
                value = year,
                onValueChange = { v -> onChange(day, month, v) },
                imeAction = ImeAction.Done,
                onDone = onDone,
                modifier = Modifier.weight(1.5f).focusRequester(yearFocus),
            )
        }

        val message = when (problem) {
            DateOfBirthInput.Result.NotADate -> R.string.onb_dob_not_a_date
            DateOfBirthInput.Result.InTheFuture -> R.string.onb_dob_future
            DateOfBirthInput.Result.Implausible -> R.string.onb_dob_implausible
            else -> null
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            // Bad input is one of the few places the error colour is allowed (DESIGN.md).
            Text(stringResource(it), style = ForewayTypography.bodyLarge, color = ForewayColors.StateError)
        }
    }
}

@Composable
private fun DigitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    Column(modifier) {
        Text(label, style = ForewayTypography.labelSmall, color = ForewayColors.InkMuted)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = ForewayTypography.titleLarge.copy(color = ForewayColors.Ink),
            cursorBrush = SolidColor(ForewayColors.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

