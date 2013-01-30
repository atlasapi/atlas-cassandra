package org.atlasapi.media.common;

public interface Serializer<F, T> {

    T serialize(F src);

    F deserialize(T dest);

}