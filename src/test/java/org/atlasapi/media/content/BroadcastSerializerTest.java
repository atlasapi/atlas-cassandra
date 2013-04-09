package org.atlasapi.media.content;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.metabroadcast.common.time.DateTimeZones;

public class BroadcastSerializerTest {
    
    private final BroadcastSerializer serializer = new BroadcastSerializer();
    
    @Test
    public void testDeSerializeBroadcast() {
        DateTime start = new DateTime(DateTimeZones.UTC);
        DateTime end = start.plusHours(1);
        Broadcast broadcast = new Broadcast("channel", start, end);
        broadcast.setId(Id.valueOf(1234));
        broadcast.setCanonicalUri("uri");
        broadcast.setAliases(ImmutableSet.of(new Alias("a","alias1"),new Alias("b","alias2")));
        broadcast.setLastUpdated(start);
        
        broadcast.setScheduleDate(null);
        broadcast.withId("sourceId");
        broadcast.setIsActivelyPublished(null);
        broadcast.setRepeat(true);
        broadcast.setSubtitled(false);
        broadcast.setSigned(false);
        broadcast.setAudioDescribed(true);
        broadcast.setHighDefinition(false);
        broadcast.setWidescreen(true);
        broadcast.setSurround(false);
        broadcast.setLive(true);
        broadcast.setNewSeries(false);
        broadcast.setPremiere(true);
        
        ContentProtos.Broadcast serialized = serializer.serialize(broadcast).build();
        
        Broadcast deserialized = serializer.deserialize(serialized);
        
        checkBroadcast(deserialized, broadcast);
     
    }
    
    @Test
    public void testDeSerializeBroadcastWithScheduleDate() {
        DateTime start = new DateTime(DateTimeZones.UTC);
        DateTime end = start.plusHours(1);
        Broadcast broadcast = new Broadcast("channel", start, end);
        broadcast.setScheduleDate(new LocalDate(DateTimeZones.UTC));
        
        ContentProtos.Broadcast serialized = serializer.serialize(broadcast).build();
        
        Broadcast deserialised = serializer.deserialize(serialized);
        
        checkBroadcast(deserialised, broadcast);
        
    }

    private void checkBroadcast(Broadcast actual, Broadcast expected) {
        assertThat(actual.getBroadcastOn(), is(expected.getBroadcastOn()));
        assertThat(actual.getTransmissionTime(), is(expected.getTransmissionTime()));
        assertThat(actual.getTransmissionEndTime(), is(expected.getTransmissionEndTime()));
        
        assertThat(actual.getId(), is(expected.getId()));
        assertThat(actual.getCanonicalUri(), is(expected.getCanonicalUri()));
        assertThat(actual.getAliases(), is(expected.getAliases()));
        assertThat(actual.getEquivalentTo(), is(expected.getEquivalentTo()));
        assertThat(actual.getLastUpdated(), is(expected.getLastUpdated()));
        
        assertThat(actual.getScheduleDate(), is(expected.getScheduleDate()));
        assertThat(actual.getSourceId(), is(expected.getSourceId()));
        assertThat(actual.isActivelyPublished(), is(expected.isActivelyPublished()));
        assertThat(actual.getRepeat(), is(expected.getRepeat()));
        assertThat(actual.getSubtitled(), is(expected.getSubtitled()));
        assertThat(actual.getSigned(), is(expected.getSigned()));
        assertThat(actual.getAudioDescribed(), is(expected.getAudioDescribed()));
        assertThat(actual.getHighDefinition(), is(expected.getHighDefinition()));
        assertThat(actual.getWidescreen(), is(expected.getWidescreen()));
        assertThat(actual.getSurround(), is(expected.getSurround()));
        assertThat(actual.getLive(), is(expected.getLive()));
        assertThat(actual.getNewSeries(), is(expected.getNewSeries()));
        assertThat(actual.getPremiere(), is(expected.getPremiere()));
    }

}
