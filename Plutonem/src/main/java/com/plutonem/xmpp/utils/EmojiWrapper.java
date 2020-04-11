package com.plutonem.xmpp.utils;

import androidx.emoji.text.EmojiCompat;

public class EmojiWrapper {

    public static CharSequence transform(CharSequence input) {
        try {
            if (EmojiCompat.get().getLoadState() == EmojiCompat.LOAD_STATE_SUCCEEDED) {
                return EmojiCompat.get().process(input);
            } else {
                return input;
            }
        } catch (IllegalStateException e) {
            return input;
        }
    }
}
