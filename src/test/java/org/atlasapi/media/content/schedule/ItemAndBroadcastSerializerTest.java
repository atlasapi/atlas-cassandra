package org.atlasapi.media.content.schedule;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.util.ItemAndBroadcast;
import org.joda.time.DateTime;
import org.junit.Test;

import com.metabroadcast.common.time.DateTimeZones;


public class ItemAndBroadcastSerializerTest {

    private final ItemAndBroadcastSerializer serializer = new ItemAndBroadcastSerializer();
    
    @Test
    public void testDeSerialization() {
        
        Item item = new Episode("episode", "episode", Publisher.BBC);
        item.setId(1);
        Broadcast broadcast = new Broadcast("channel", DateTime.now(DateTimeZones.UTC), DateTime.now(DateTimeZones.UTC));
        ItemAndBroadcast iab = new ItemAndBroadcast(item, broadcast);
        
        byte[] serialized = serializer.serialize(iab);
        ItemAndBroadcast deserialized = serializer.deserialize(serialized);
        
        assertThat(deserialized.getItem(), is(item));
        assertThat(deserialized.getBroadcast(), is(broadcast));
        
    }

}
