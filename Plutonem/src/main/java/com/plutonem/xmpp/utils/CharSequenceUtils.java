package com.plutonem.xmpp.utils;

import android.text.Spannable;

import java.util.ArrayList;
import java.util.List;

public class CharSequenceUtils {

    private static int getStartIndex(CharSequence input) {
        int length = input.length();
        int index = 0;
        while (Character.isWhitespace(input.charAt(index))) {
            ++index;
            if (index >= length) {
                break;
            }
        }
        return index;
    }

    private static int getEndIndex(CharSequence input) {
        int index = input.length() - 1;
        while (Character.isWhitespace(input.charAt(index))) {
            --index;
            if (index < 0) {
                break;
            }
        }
        return index;
    }

    public static CharSequence trim(CharSequence input) {
        int begin = getStartIndex(input);
        int end = getEndIndex(input);
        if (begin > end) {
            return "";
        } else {
            return StylingHelper.subSequence(input, begin, end + 1);
        }
    }

    public static List<CharSequence> split(Spannable charSequence, char c) {
        List<CharSequence> out = new ArrayList<>();
        int begin = 0;
        for (int i = 0; i < charSequence.length(); ++i) {
            if (charSequence.charAt(i) == c) {
                out.add(StylingHelper.subSequence(charSequence, begin, i));
                begin = ++i;
            }
        }
        if (begin < charSequence.length()) {
            out.add(StylingHelper.subSequence(charSequence, begin, charSequence.length()));
        }
        return out;
    }
}
