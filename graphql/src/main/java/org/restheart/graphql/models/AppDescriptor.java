package org.restheart.graphql.models;

public class AppDescriptor {

    private String appName;
    private Boolean enabled;
    private String description;
    private String url;

    public AppDescriptor() {}

    public AppDescriptor(String appName, Boolean enabled, String description, String url) {
        this.appName = appName;
        this.enabled = enabled;
        this.description = description;
        this.url = url;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    public static class Builder{
        private String appName;
        private Boolean enabled;
        private String description;
        private String url;

        private Builder(String appName, Boolean enabled){
            this.appName = appName;
            this.enabled = enabled;
        }

        public Builder newBuilder(String appName, Boolean enabled){
            return new Builder(appName, enabled);
        }

        public Builder description(String description){
            this.description = description;
            return this;
        }

        public Builder url(String url){
            this.url = url;
            return this;
        }

        public AppDescriptor build(){
            return new AppDescriptor(this.appName, this.enabled, this.description, this.url);
        }

    }

}
