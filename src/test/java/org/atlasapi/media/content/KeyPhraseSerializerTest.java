package org.atlasapi.media.content;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.atlasapi.media.entity.KeyPhrase;
import org.junit.Test;


public class KeyPhraseSerializerTest {

    private final KeyPhraseSerializer serializer = new KeyPhraseSerializer();
    
    @Test
    public void testDeSerializingUnweightedKeyPhrase() {
        KeyPhrase phrase = new KeyPhrase("phrase",null,null);
        KeyPhrase deserialized = serializer.deserialize(serializer.serialize(phrase));
        assertThat(deserialized.getPhrase(), is(phrase.getPhrase()));
        assertNull(deserialized.getWeighting());
    }

    @Test
    public void testDeSerializingWeightedKeyPhrase() {
        KeyPhrase phrase = new KeyPhrase("phrase",null,1.0);
        KeyPhrase deserialized = serializer.deserialize(serializer.serialize(phrase));
        assertThat(deserialized.getPhrase(), is(phrase.getPhrase()));
        assertThat(deserialized.getWeighting(), is(phrase.getWeighting()));
    }

}
