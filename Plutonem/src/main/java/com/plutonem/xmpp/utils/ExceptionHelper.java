package com.plutonem.xmpp.utils;

import android.content.Context;

import java.io.IOException;
import java.io.OutputStream;

public class ExceptionHelper {

    private static final String FILENAME = "stacktrace.txt";

    public static void init(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(
                    context));
        }
    }

    static void writeToStacktraceFile(Context context, String msg) {
        try {
            OutputStream os = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            os.write(msg.getBytes());
            os.flush();
            os.close();
        } catch (IOException ignored) {
        }
    }
}
