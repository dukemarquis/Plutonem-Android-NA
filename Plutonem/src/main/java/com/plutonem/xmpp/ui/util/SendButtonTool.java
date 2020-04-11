package com.plutonem.xmpp.ui.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;

import com.plutonem.R;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Presence;
import com.plutonem.xmpp.ui.ConversationFragment;

public class SendButtonTool {

    public static SendButtonAction getAction(final Activity activity, final Conversation c, final String text) {
        if (activity == null) {
            return SendButtonAction.TEXT;
        }
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean empty = text.length() == 0;
        if (empty) {
            String setting = preferences.getString("quick_action", activity.getResources().getString(R.string.quick_action));
            if ("recent".equals(setting)) {
                setting = preferences.getString(ConversationFragment.RECENTLY_USED_QUICK_ACTION, SendButtonAction.TEXT.toString());
                return SendButtonAction.valueOfOrDefault(setting);
            } else {
                return SendButtonAction.valueOfOrDefault(setting);
            }
        } else {
            return SendButtonAction.TEXT;
        }
    }

    public static int getSendButtonImageResource(Activity activity, SendButtonAction action, Presence.Status status) {
        if (action == SendButtonAction.TEXT) {
            switch (status) {
                case CHAT:
                case ONLINE:
                    return R.drawable.ic_send_text_online;
                case AWAY:
                    return R.drawable.ic_send_text_away;
                case XA:
                case DND:
                    return R.drawable.ic_send_text_dnd;
                default:
                    return getThemeResource(activity, R.attr.ic_send_text_offline, R.drawable.ic_send_text_offline);
            }
        }
        return getThemeResource(activity, R.attr.ic_send_text_offline, R.drawable.ic_send_text_offline);
    }

    private static int getThemeResource(Activity activity, int r_attr_name, int r_drawable_def) {
        int[] attrs = {r_attr_name};
        TypedArray ta = activity.getTheme().obtainStyledAttributes(attrs);

        int res = ta.getResourceId(0, r_drawable_def);
        ta.recycle();

        return res;
    }
}
