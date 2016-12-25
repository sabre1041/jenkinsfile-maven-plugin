package com.andyserver.maven.plugins;

public class Crumb {

    private String crumbRequestField;
    private String crumb;

    public Crumb() {
    }

    public Crumb(String crumbRequestField, String crumb) {
        this.crumbRequestField = crumbRequestField;
        this.crumb = crumb;
    }

    public String getCrumbRequestField() {
        return crumbRequestField;
    }

    public void setCrumbRequestField(String crumbRequestField) {
        this.crumbRequestField = crumbRequestField;
    }

    public String getCrumb() {
        return crumb;
    }

    public void setCrumb(String crumb) {
        this.crumb = crumb;
    }

}
