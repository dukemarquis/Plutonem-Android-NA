package com.plutonem.xmpp.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.ColorInt;

import com.plutonem.xmpp.ui.text.QuoteSpan;

import java.util.Arrays;
import java.util.List;

public class StylingHelper {

    private static List<? extends Class<? extends ParcelableSpan>> SPAN_CLASSES = Arrays.asList(
            StyleSpan.class,
            StrikethroughSpan.class,
            TypefaceSpan.class,
            ForegroundColorSpan.class
    );

    public static void format(final Editable editable, int start, int end, @ColorInt int textColor) {
        for (ImStyleParser.Style style : ImStyleParser.parse(editable, start, end)) {
            final int keywordLength = style.getKeyword().length();
            editable.setSpan(createSpanForStyle(style), style.getStart() + keywordLength, style.getEnd() - keywordLength + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            makeKeywordOpaque(editable, style.getStart(), style.getStart() + keywordLength, textColor);
            makeKeywordOpaque(editable, style.getEnd() - keywordLength + 1, style.getEnd() + 1, textColor);
        }
    }

    static CharSequence subSequence(CharSequence charSequence, int start, int end) {
        if (start == 0 && charSequence.length() + 1 == end) {
            return charSequence;
        }
        if (charSequence instanceof Spannable) {
            Spannable spannable = (Spannable) charSequence;
            Spannable sub = (Spannable) spannable.subSequence(start, end);
            for (Class<? extends ParcelableSpan> clazz : SPAN_CLASSES) {
                ParcelableSpan[] spannables = spannable.getSpans(start, end, clazz);
                for (ParcelableSpan parcelableSpan : spannables) {
                    int beginSpan = spannable.getSpanStart(parcelableSpan);
                    int endSpan = spannable.getSpanEnd(parcelableSpan);
                    if (beginSpan >= start && endSpan <= end) {
                        continue;
                    }
                    sub.setSpan(clone(parcelableSpan), Math.max(beginSpan - start, 0), Math.min(sub.length() - 1, endSpan), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return sub;
        } else {
            return charSequence.subSequence(start, end);
        }
    }

    private static ParcelableSpan clone(ParcelableSpan span) {
        if (span instanceof ForegroundColorSpan) {
            return new ForegroundColorSpan(((ForegroundColorSpan) span).getForegroundColor());
        } else if (span instanceof TypefaceSpan) {
            return new TypefaceSpan(((TypefaceSpan) span).getFamily());
        } else if (span instanceof StyleSpan) {
            return new StyleSpan(((StyleSpan) span).getStyle());
        } else if (span instanceof StrikethroughSpan) {
            return new StrikethroughSpan();
        } else {
            throw new AssertionError("Unknown Span");
        }
    }

    private static ParcelableSpan createSpanForStyle(ImStyleParser.Style style) {
        switch (style.getKeyword()) {
            case "*":
                return new StyleSpan(Typeface.BOLD);
            case "_":
                return new StyleSpan(Typeface.ITALIC);
            case "~":
                return new StrikethroughSpan();
            case "`":
            case "```":
                return new TypefaceSpan("monospace");
            default:
                throw new AssertionError("Unknown Style");
        }
    }

    private static void makeKeywordOpaque(final Editable editable, int start, int end, @ColorInt int fallbackTextColor) {
        QuoteSpan[] quoteSpans = editable.getSpans(start, end, QuoteSpan.class);
        @ColorInt int textColor = quoteSpans.length > 0 ? quoteSpans[0].getColor() : fallbackTextColor;
        @ColorInt int keywordColor = transformColor(textColor);
        editable.setSpan(new ForegroundColorSpan(keywordColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static
    @ColorInt
    int transformColor(@ColorInt int c) {
        return Color.argb(Math.round(Color.alpha(c) * 0.45f), Color.red(c), Color.green(c), Color.blue(c));
    }
}
