package com.plutonem.utilities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;

public class AniUtils {
    public enum Duration {
        SHORT,
        MEDIUM,
        LONG;

        public long toMillis(Context context) {
            switch (this) {
                case LONG:
                    return context.getResources().getInteger(android.R.integer.config_longAnimTime);
                case MEDIUM:
                    return context.getResources().getInteger(android.R.integer.config_mediumAnimTime);
                default:
                    return context.getResources().getInteger(android.R.integer.config_shortAnimTime);
            }
        }
    }

    private AniUtils() {
        throw new AssertionError();
    }

    public static void startAnimation(View target, int aniResId) {
        startAnimation(target, aniResId, null);
    }

    public static void startAnimation(View target, int aniResId, Animation.AnimationListener listener) {
        if (target == null) {
            return;
        }

        Animation animation = AnimationUtils.loadAnimation(target.getContext(), aniResId);
        if (animation != null) {
            if (listener != null) {
                animation.setAnimationListener(listener);
            }
            target.startAnimation(animation);
        }
    }

    /*
     * used when animating a toolbar in/out
     */
    public static void animateTopBar(View view, boolean show) {
        animateBar(view, show, true, Duration.SHORT);
    }

    public static void animateBottomBar(View view, boolean show) {
        animateBar(view, show, false, Duration.SHORT);
    }

    private static void animateBar(View view,
                                   boolean show,
                                   boolean isTopBar,
                                   Duration duration) {
        int newVisibility = (show ? View.VISIBLE : View.GONE);
        if (view == null || view.getVisibility() == newVisibility) {
            return;
        }

        float fromY;
        float toY;
        if (isTopBar) {
            fromY = (show ? -1f : 0f);
            toY = (show ? 0f : -1f);
        } else {
            fromY = (show ? 1f : 0f);
            toY = (show ? 0f : 1f);
        }
        Animation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, fromY,
                Animation.RELATIVE_TO_SELF, toY);

        long durationMillis = duration.toMillis(view.getContext());
        animation.setDuration(durationMillis);

        if (show) {
            animation.setInterpolator(new DecelerateInterpolator());
        } else {
            animation.setInterpolator(new AccelerateInterpolator());
        }

        view.clearAnimation();
        view.startAnimation(animation);
        view.setVisibility(newVisibility);
    }

    private static ObjectAnimator getFadeInAnim(final View target, Duration duration) {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(target, View.ALPHA, 0.0f, 1.0f);
        fadeIn.setDuration(duration.toMillis(target.getContext()));
        fadeIn.setInterpolator(new LinearInterpolator());
        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                target.setVisibility(View.VISIBLE);
            }
        });
        return fadeIn;
    }

    private static ObjectAnimator getFadeOutAnim(final View target, Duration duration, final int endVisibility) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(target, View.ALPHA, 1.0f, 0.0f);
        fadeOut.setDuration(duration.toMillis(target.getContext()));
        fadeOut.setInterpolator(new LinearInterpolator());
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                target.setVisibility(endVisibility);
            }
        });
        return fadeOut;
    }

    public static void fadeIn(final View target, Duration duration) {
        if (target != null && duration != null) {
            getFadeInAnim(target, duration).start();
        }
    }

    public static void fadeOut(final View target, Duration duration) {
        fadeOut(target, duration, View.GONE);
    }

    public static void fadeOut(final View target, Duration duration, int endVisibility) {
        if (target != null && duration != null) {
            getFadeOutAnim(target, duration, endVisibility).start();
        }
    }
}
