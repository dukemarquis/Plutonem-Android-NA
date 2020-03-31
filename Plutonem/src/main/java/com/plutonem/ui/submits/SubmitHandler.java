package com.plutonem.ui.submits;

import androidx.annotation.NonNull;

public interface SubmitHandler<T> {
    void submit(@NonNull T object);

    boolean hasInProgressSubmits();

    void cancelInProgressSubmits();
}
