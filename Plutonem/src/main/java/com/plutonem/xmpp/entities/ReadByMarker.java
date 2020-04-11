package com.plutonem.xmpp.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import rocks.xmpp.addr.Jid;

public class ReadByMarker {

    private ReadByMarker() {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReadByMarker marker = (ReadByMarker) o;

        if (fullJid != null ? !fullJid.equals(marker.fullJid) : marker.fullJid != null)
            return false;
        return realJid != null ? realJid.equals(marker.realJid) : marker.realJid == null;

    }

    @Override
    public int hashCode() {
        int result = fullJid != null ? fullJid.hashCode() : 0;
        result = 31 * result + (realJid != null ? realJid.hashCode() : 0);
        return result;
    }


    private Jid fullJid;
    private Jid realJid;

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        if (fullJid != null) {
            try {
                jsonObject.put("fullJid", fullJid.toString());
            } catch (JSONException e) {
                //ignore
            }
        }
        if (realJid != null) {
            try {
                jsonObject.put("realJid", realJid.toString());
            } catch (JSONException e) {
                //ignore
            }
        }
        return jsonObject;
    }

    public static Set<ReadByMarker> fromJson(JSONArray jsonArray) {
        HashSet<ReadByMarker> readByMarkers = new HashSet<>();
        for(int i = 0; i < jsonArray.length(); ++i) {
            try {
                readByMarkers.add(fromJson(jsonArray.getJSONObject(i)));
            } catch (JSONException e) {
                //ignored
            }
        }
        return readByMarkers;
    }

    public static ReadByMarker fromJson(JSONObject jsonObject) {
        ReadByMarker marker = new ReadByMarker();
        try {
            marker.fullJid = Jid.of(jsonObject.getString("fullJid"));
        } catch (JSONException | IllegalArgumentException e) {
            marker.fullJid = null;
        }
        try {
            marker.realJid = Jid.of(jsonObject.getString("realJid"));
        } catch (JSONException | IllegalArgumentException e) {
            marker.realJid = null;
        }
        return marker;
    }

    public static Set<ReadByMarker> fromJsonString(String json) {
        try {
            return fromJson(new JSONArray(json));
        } catch (JSONException | NullPointerException e) {
            return new HashSet<>();
        }
    }

    public static JSONArray toJson(Set<ReadByMarker> readByMarkers) {
        JSONArray jsonArray = new JSONArray();
        for(ReadByMarker marker : readByMarkers) {
            jsonArray.put(marker.toJson());
        }
        return jsonArray;
    }
}
