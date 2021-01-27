package org.restheart.graphql.models;

import org.bson.types.ObjectId;

public class AppDescriptor {

    private String appName;
    private Boolean enabled;
    private String description;
    private String uri;

    private AppDescriptor(String appName, Boolean enabled, String description, String uri) {
        this.appName = appName;
        this.enabled = enabled;
        this.description = description;
        this.uri = uri;
    }

    public static Builder newBuilder(){
        return new Builder();
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
        return uri;
    }

    public void setUrl(String uri) {
        this.uri = uri;
    }


    public static class Builder{

        private String appName;
        private Boolean enabled;
        private String description;
        private String uri;

        private Builder(){ }

        public Builder appName(String appName){
            this.appName = appName;
            return this;
        }


        public Builder enabled(Boolean enabled){
            this.enabled = enabled;
            return this;
        }

        public Builder description(String description){
            this.description = description;
            return this;
        }

        public Builder uri(String uri){
            this.uri = uri;
            return this;
        }

        public AppDescriptor build(){

            if (appName == null && uri == null){
                throw new IllegalStateException(
                        "At least one of 'appName' and 'uri' must be not null!"
                );
            }

            return new AppDescriptor(this.appName, this.enabled, this.description, this.uri);
        }

        private static void throwIllegalException(String varName){

            throw  new IllegalStateException(
                    varName + "could not be null!"
            );

        }

    }

}
