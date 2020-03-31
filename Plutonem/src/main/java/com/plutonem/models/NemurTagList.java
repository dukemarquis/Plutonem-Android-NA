package com.plutonem.models;

import java.util.ArrayList;

public class NemurTagList extends ArrayList<NemurTag> {
    private int indexOfTag(NemurTag tag) {
        if (tag == null || isEmpty()) {
            return -1;
        }

        for (int i = 0; i < this.size(); i++) {
            if (NemurTag.isSameTag(tag, this.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public boolean isSameList(NemurTagList otherList) {
        if (otherList == null || otherList.size() != this.size()) {
            return false;
        }

        for (NemurTag otherTag : otherList) {
            int i = this.indexOfTag(otherTag);
            if (i == -1) {
                return false;
            } else if (!otherTag.getEndpoint().equals(this.get(i).getEndpoint())) {
                return false;
            } else if (!otherTag.getTagTitle().equals(this.get(i).getTagTitle())) {
                return false;
            }
        }

        return true;
    }

    /*
     * returns a list of tags that are in this list but not in the passed list
     */
    public NemurTagList getDeletions(NemurTagList otherList) {
        NemurTagList deletions = new NemurTagList();
        if (otherList == null) {
            return deletions;
        }

        for (NemurTag thisTag : this) {
            if (otherList.indexOfTag(thisTag) == -1) {
                deletions.add(thisTag);
            }
        }

        return deletions;
    }
}
