package org.atlasapi.media.content;

public interface Serializer<F, T> {

    T serialize(F content);

    F deserialize(T p);

}