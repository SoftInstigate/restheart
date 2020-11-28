package org.bson;

public class BsonWrapper extends BsonValue {

    private BsonValue wrapped;

    public BsonWrapper(BsonValue bsonValue){
        if(bsonValue.isDocument()){
            this.wrapped = new BsonDocument();
            bsonValue.asDocument().forEach(
                    ((s, value) ->  wrapped.asDocument().put(s, new BsonWrapper(value)))
            );
        }
        else {
            this.wrapped = bsonValue;
        }
    }

    @Override
    public String toString() {
        return "BsonWrapper{" +
                "wrapped=" + wrapped +
                '}';
    }

    @Override
    public BsonType getBsonType() {
        return this.wrapped.getBsonType();
    }
}
