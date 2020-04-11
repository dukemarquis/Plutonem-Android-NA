package com.plutonem.xmpp.ui.util;

import java.util.regex.Pattern;

public class GeoHelper {

    public static Pattern GEO_URI = Pattern.compile("geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,-?\\d+(?:\\.\\d+)?)?(?:;crs=[\\w-]+)?(?:;u=\\d+(?:\\.\\d+)?)?(?:;[\\w-]+=(?:[\\w-_.!~*'()]|%[\\da-f][\\da-f])+)*(\\?z=\\d+)?", Pattern.CASE_INSENSITIVE);
}
