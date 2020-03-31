package com.plutonem.models;

public class Ex_Plutonem_MediaObject {

    private String commodity_name;
    private String commodity_price;
    private String media_url;
    private String thumbnail;
    private String description;

    public Ex_Plutonem_MediaObject(String commodity_name, String commodity_price, String media_url, String thumbnail, String description) {
        this.commodity_name = commodity_name;
        this.commodity_price = commodity_price;
        this.media_url = media_url;
        this.thumbnail = thumbnail;
        this.description = description;
    }

    public Ex_Plutonem_MediaObject() {
    }

    public String getCommodity_name() {
        return commodity_name;
    }

    public void setCommodity_name(String commodity_name) {
        this.commodity_name = commodity_name;
    }

    public String getCommodity_price() {
        return commodity_price;
    }

    public void setCommodity_price(String commodity_price) {
        this.commodity_price = commodity_price;
    }

    public String getMedia_url() {
        return media_url;
    }

    public void setMedia_url(String media_url) {
        this.media_url = media_url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}