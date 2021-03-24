package org.restheart.graphql.models;

public class DataLoaderSettings {

    private Boolean batching;
    private Integer max_batch_size;
    private Boolean caching;

    private DataLoaderSettings(Boolean batching, Boolean caching, Integer max_batch_size){
        this.batching = batching;
        this.caching = caching;
        this.max_batch_size = max_batch_size;
    }

    public static Builder newBuilder(){
        return new Builder();
    }


    public Boolean getBatching() {
        return batching;
    }

    public Boolean getCaching(){ return  caching;}

    public Integer getMax_batch_size() {
        return max_batch_size;
    }

    public void setBatching(Boolean enabled) {
        this.batching = enabled;
    }

    public void setCaching(Boolean enabled) {
        this.caching = enabled;
    }


    public void setMax_batch_size(Integer max_batch_size) {
        this.max_batch_size = max_batch_size;
    }

    public static class Builder{

        private Boolean batching;
        private Boolean caching;
        private Integer max_batch_size;

        private Builder(){}

        public Builder batching(Boolean enable){
            this.batching = enable;
            return this;
        }

        public Builder caching(Boolean enable){
            this.caching = enable;
            return this;
        }

        public Builder max_batch_size(Integer size){
            this.max_batch_size = size;
            return this;
        }

        public DataLoaderSettings build(){

            if (this.batching == null){
                this.batching = false;
            }

            if (this.caching == null){
                this.caching = false;
            }

            if (this.max_batch_size == null){
                this.max_batch_size = 0;
            }

            return new DataLoaderSettings(this.batching, this.caching, this.max_batch_size);

        }

    }

}
