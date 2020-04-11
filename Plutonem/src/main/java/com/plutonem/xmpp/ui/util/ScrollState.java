package com.plutonem.xmpp.ui.util;

import android.os.Parcel;
import android.os.Parcelable;

public class ScrollState implements Parcelable {

    public final int position;
    public final int offset;

    private ScrollState(Parcel in) {
        position = in.readInt();
        offset = in.readInt();
    }

    public ScrollState(int position, int offset) {
        this.position = position;
        this.offset = offset;
    }

    public static final Creator<ScrollState> CREATOR = new Creator<ScrollState>() {
        @Override
        public ScrollState createFromParcel(Parcel in) {
            return new ScrollState(in);
        }

        @Override
        public ScrollState[] newArray(int size) {
            return new ScrollState[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(position);
        dest.writeInt(offset);
    }
}
