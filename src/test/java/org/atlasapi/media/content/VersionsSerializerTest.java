package org.atlasapi.media.content;

import static org.atlasapi.media.segment.SegmentEvent.TO_REF;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.Set;

import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Restriction;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.segment.SegmentEvent;
import org.atlasapi.media.segment.SegmentRef;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.time.DateTimeZones;


public class VersionsSerializerTest {

    private final VersionsSerializer serializer = new VersionsSerializer();
    
    @Test
    public void testTopLevelVersionProperties() throws Exception {
        

        Version version = new Version();
        version.setCanonicalUri("version1");       
        version.set3d(true);
        version.setRestriction(Restriction.from(14, "old"));
        version.setDuration(Duration.standardMinutes(4));
        version.setPublishedDuration(600);
        
        Iterable<Version> versions = ImmutableSet.of(version);
        byte[] bytes = serializer.serialize(versions).toByteArray();
        
        Set<Version> deserialized = serializer.deserialize(ContentProtos.Content.parseFrom(bytes));
        
        Version deserializedVersion1 = Iterables.getFirst(deserialized, null);
        assertThat(deserializedVersion1.getCanonicalUri(), is(version.getCanonicalUri()));
        assertThat(deserializedVersion1.is3d(), is(version.is3d()));
        assertThat(deserializedVersion1.getRestriction().getMessage(), is(version.getRestriction().getMessage()));
        assertThat(deserializedVersion1.getRestriction().isRestricted(), is(version.getRestriction().isRestricted()));
        assertThat(deserializedVersion1.getRestriction().getMinimumAge(), is(version.getRestriction().getMinimumAge()));
        assertThat(deserializedVersion1.getDuration(), is(version.getDuration()));
        assertThat(deserializedVersion1.getPublishedDuration(), is(version.getPublishedDuration()));
        
    }
    
    @Test
    public void testDeSerializeVersionsWithVersionIds() throws Exception {

        Version version1 = new Version();
        version1.setCanonicalUri("version1");
        version1.setBroadcasts(ImmutableSet.of(
            new Broadcast("one",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC)),
            new Broadcast("two",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC))
        ));
        version1.setManifestedAs(ImmutableSet.of(
            encoding("one")
        ));
        version1.setSegmentEvents(ImmutableSet.of(
            segmentEvent("one")
        ));
        
        Version version2 = new Version();
        version2.setCanonicalUri("version2");
        version2.setBroadcasts(ImmutableSet.of(
            new Broadcast("three",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC))
        ));
        version2.setManifestedAs(ImmutableSet.of(
            encoding("two"),
            encoding("three")
        ));
        version2.setSegmentEvents(ImmutableSet.of(
            segmentEvent("two"),
            segmentEvent("three")
        ));
        
        Iterable<Version> versions = ImmutableSet.of(version1, version2);
        ContentProtos.Content serialized = serializer.serialize(versions);
        
        Set<Version> deserialized = serializer.deserialize(serialized);
        
        Version deserializedVersion1 = versionWithUri(deserialized, version1.getCanonicalUri());
        assertThat(deserializedVersion1.getCanonicalUri(), is(version1.getCanonicalUri()));
        assertThat(deserializedVersion1.getBroadcasts().size(), is(2));
        assertThat(Iterables.getFirst(deserializedVersion1.getBroadcasts(), null).getBroadcastOn(), is("one"));
        assertThat(Iterables.getLast(deserializedVersion1.getBroadcasts(), null).getBroadcastOn(), is("two"));
        assertThat(deserializedVersion1.getManifestedAs().size(), is(1));
        assertThat(Iterables.getOnlyElement(deserializedVersion1.getManifestedAs()).getSource(), is("one"));
        assertThat(deserializedVersion1.getSegmentEvents().size(), is(1));
        assertThat(Iterables.getOnlyElement(deserializedVersion1.getSegmentEvents()).getSegment(), is(new SegmentRef("one")));
        
        Version deserializedVersion2 = versionWithUri(deserialized, version2.getCanonicalUri());
        assertThat(deserializedVersion2.getCanonicalUri(), is(version2.getCanonicalUri()));
        assertThat(deserializedVersion2.getBroadcasts().size(), is(1));
        assertThat(Iterables.getOnlyElement(deserializedVersion2.getBroadcasts()).getBroadcastOn(), is("three"));
        assertThat(deserializedVersion2.getManifestedAs().size(), is(2));
        assertThat(Iterables.getFirst(deserializedVersion2.getManifestedAs(), null).getSource(), is("two"));
        assertThat(Iterables.getLast(deserializedVersion2.getManifestedAs()).getSource(), is("three"));
        assertThat(deserializedVersion2.getSegmentEvents().size(), is(2));
        assertThat(Iterables.transform(deserializedVersion2.getSegmentEvents(), TO_REF), 
                hasItems(new SegmentRef("two"), new SegmentRef("three")));
        
    }

    @Test
    public void testDeSerializeVersionsWithoutVersionIds() throws Exception {
        
        Version version1 = new Version();
        version1.set3d(true);
        version1.setBroadcasts(ImmutableSet.of(
            new Broadcast("one",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC)),
            new Broadcast("two",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC))
        ));
        version1.setManifestedAs(ImmutableSet.of(
            encoding("one")
        ));
        version1.setSegmentEvents(ImmutableSet.of(
            segmentEvent("one")
        ));
        
        Version version2 = new Version();
        version2.set3d(false);
        version2.setBroadcasts(ImmutableSet.of(
            new Broadcast("three",new DateTime(DateTimeZones.UTC),new DateTime(DateTimeZones.UTC))
        ));
        version2.setManifestedAs(ImmutableSet.of(
            encoding("two"),
            encoding("three")
        ));
        version2.setSegmentEvents(ImmutableSet.of(
            segmentEvent("two"),
            segmentEvent("three")
        ));
        
        Iterable<Version> versions = ImmutableSet.of(version1, version2);
        ContentProtos.Content serialized = serializer.serialize(versions);
        
        Set<Version> deserialized = serializer.deserialize(serialized);
        
        Version deserializedVersion1 = versionWith3d(deserialized, version1.is3d());
        assertThat(deserializedVersion1.getBroadcasts().size(), either(is(3)).or(is(0)));
        assertThat(deserializedVersion1.getManifestedAs().size(), either(is(3)).or(is(0)));
        assertThat(deserializedVersion1.getSegmentEvents().size(), either(is(3)).or(is(0)));
        
        Version deserializedVersion2 = versionWith3d(deserialized, version2.is3d());
        assertThat(deserializedVersion2.getBroadcasts().size(), either(is(3)).or(is(0)));
        assertThat(deserializedVersion2.getManifestedAs().size(), either(is(3)).or(is(0)));
        assertThat(deserializedVersion2.getSegmentEvents().size(), either(is(3)).or(is(0)));
        
        assertNotEqualSize(deserializedVersion1.getBroadcasts(), deserializedVersion2.getBroadcasts());
        assertNotEqualSize(deserializedVersion1.getManifestedAs(), deserializedVersion2.getManifestedAs());
        assertNotEqualSize(deserializedVersion1.getSegmentEvents(), deserializedVersion2.getSegmentEvents());
    }

    private <T> void assertNotEqualSize(Collection<T> c1, Collection<T> c2) {
        assertNotEquals(c1.size(), c2.size());
    }

    private Version versionWith3d(Set<Version> versions, Boolean is3d) {
        for (Version version : versions) {
            if (is3d.equals(version.is3d())) {
                return version;
            }
        }
        return null;
    }

    private Version versionWithUri(Iterable<Version> versions, String canonicalUri) {
        for (Version version : versions) {
            if (canonicalUri.equals(version.getCanonicalUri())) {
                return version;
            }
        }
        return null;
    }

    private SegmentEvent segmentEvent(String segment) {
        SegmentEvent event = new SegmentEvent();
        event.setSegment(new SegmentRef(segment));
        return event;
    }

    private Encoding encoding(String uri) {
        Encoding encoding = new Encoding();
        encoding.setSource(uri);
        return encoding;
    }

}
