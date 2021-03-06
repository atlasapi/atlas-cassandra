package org.atlasapi.media.content;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.atlasapi.media.entity.Description;
import org.atlasapi.media.segment.SegmentEvent;
import org.atlasapi.media.segment.SegmentRef;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.joda.time.Duration;
import org.junit.Test;

public class SegmentEventSerializerTest {

    private final SegmentEventSerializer serializer = new SegmentEventSerializer();
    
    @Test
    public void testDeSerializeSegmentEvent() throws Exception {
        SegmentEvent segmentEvent = new SegmentEvent();
        segmentEvent.setCanonicalUri("uri");
        segmentEvent.setIsChapter(true);
        segmentEvent.setOffset(Duration.standardMinutes(5));
        segmentEvent.setPosition(5);
        segmentEvent.setSegment(new SegmentRef("segment"));
        segmentEvent.setDescription(new Description("title", "desc", "img", "thmb"));
        
        byte[] bytes = serializer.serialize(segmentEvent).build().toByteArray();
        
        SegmentEvent deserialized = serializer.deserialize(ContentProtos.SegmentEvent.parseFrom(bytes));
        
        assertThat(deserialized.getCanonicalUri(), is(segmentEvent.getCanonicalUri()));
        assertThat(deserialized.getIsChapter(), is(segmentEvent.getIsChapter()));
        assertThat(deserialized.getOffset(), is(segmentEvent.getOffset()));
        assertThat(deserialized.getPosition(), is(segmentEvent.getPosition()));
        assertThat(deserialized.getSegment(), is(segmentEvent.getSegment()));
        assertThat(deserialized.getDescription(), is(segmentEvent.getDescription()));
        
    }


}
