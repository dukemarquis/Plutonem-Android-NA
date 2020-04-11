package com.plutonem.xmpp.ui.text;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Toast;

import com.plutonem.R;
import com.plutonem.xmpp.ui.ConversationsActivity;

import java.util.Arrays;

public class FixedURLSpan extends URLSpan {

    private FixedURLSpan(String url) {
        super(url);
    }

    public static void fix(final Editable editable) {
        for (final URLSpan urlspan : editable.getSpans(0, editable.length() - 1, URLSpan.class)) {
            final int start = editable.getSpanStart(urlspan);
            final int end = editable.getSpanEnd(urlspan);
            editable.removeSpan(urlspan);
            editable.setSpan(new FixedURLSpan(urlspan.getURL()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void onClick(View widget) {
        final Uri uri = Uri.parse(getURL());
        final Context context = widget.getContext();
        final boolean candidateToProcessDirectly = "xmpp".equals(uri.getScheme()) || ("https".equals(uri.getScheme()) && "conversations.im".equals(uri.getHost()) && uri.getPathSegments().size() > 1 && Arrays.asList("j","i").contains(uri.getPathSegments().get(0)));
        if (candidateToProcessDirectly && context instanceof ConversationsActivity) {
            // skip Open Conversation URL part
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        //intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        try {
            context.startActivity(intent);
            widget.playSoundEffect(0);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show();
        }
    }
}
