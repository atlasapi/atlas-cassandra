package org.atlasapi.media.content;

import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.serialization.protobuf.ContentProtos;

public class KeyPhraseSerializer {

    public KeyPhrase deserialize(ContentProtos.KeyPhrase phrase) {
        return new KeyPhrase(
            phrase.getPhrase(),
            null,
            phrase.hasWeighting() ? phrase.getWeighting() : null);
    }

    public ContentProtos.KeyPhrase serialize(KeyPhrase keyPhrase) {
        ContentProtos.KeyPhrase.Builder phrase = ContentProtos.KeyPhrase.newBuilder()
            .setPhrase(keyPhrase.getPhrase());
        if (keyPhrase.getWeighting() != null) {
            phrase.setWeighting(keyPhrase.getWeighting());
        }
        return phrase.build();
    }
}
