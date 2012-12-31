package org.atlasapi.media.content;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Restriction;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.segment.SegmentEvent;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Content.Builder;
import org.joda.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

public class VersionsSerializer {

    private BroadcastSerializer broadcastSerializer = new BroadcastSerializer();
    private EncodingSerializer encodingSerializer = new EncodingSerializer();
    private SegmentEventSerializer segmentEventSerializer = new SegmentEventSerializer();

    public ContentProtos.Content serialize(Iterable<Version> versions) {
        Builder builder = ContentProtos.Content.newBuilder();
        for (final Version version : versions) {
            builder.addAllBroadcasts(serializeBroadcasts(version, version.getBroadcasts()));
            builder.addAllEncodings(serializeEncoding(version, version.getManifestedAs()));
            builder.addAllSegmentEvents(serializeSegmentEvents(version, version.getSegmentEvents()));
            builder.addVersions(serialize(version));
        }
        return builder.buildPartial();
    }

    private Iterable<ContentProtos.SegmentEvent> serializeSegmentEvents(Version version, List<SegmentEvent> segmentEvents) {
        final String versionId = version.getCanonicalUri();
        return Iterables.transform(segmentEvents, new Function<SegmentEvent, ContentProtos.SegmentEvent>() {
            @Override
            public ContentProtos.SegmentEvent apply(SegmentEvent segmentEvent) {
                ContentProtos.SegmentEvent.Builder message = segmentEventSerializer.serialize(segmentEvent);
                if (versionId != null) {
                    message.setVersion(versionId);
                }
                return message.build();
            }
        });
    }

    private Iterable<ContentProtos.Broadcast> serializeBroadcasts(Version version, Set<Broadcast> broadcasts) {
        final String versionId = version.getCanonicalUri();
        return Iterables.transform(broadcasts, new Function<Broadcast, ContentProtos.Broadcast>() {
            @Override
            public ContentProtos.Broadcast apply(Broadcast broadcast) {
                ContentProtos.Broadcast.Builder message = broadcastSerializer.serialize(broadcast);
                if (versionId != null) {
                    message.setVersion(versionId);
                }
                return message.build();
            }
        });
    }

    private Iterable<ContentProtos.Encoding> serializeEncoding(Version version, Set<Encoding> encodings) {
        final String versionId = version.getCanonicalUri();
        return Iterables.transform(encodings, new Function<Encoding, ContentProtos.Encoding>() {
            @Override
            public ContentProtos.Encoding apply(Encoding encoding) {
                ContentProtos.Encoding.Builder message = encodingSerializer.serialize(encoding);
                if (versionId != null) {
                    message.setVersion(versionId);
                }
                return message.build();
            }
        });
    }
    
    private ContentProtos.Version serialize(Version version) {
        ContentProtos.Version.Builder message = ContentProtos.Version.newBuilder();
        if (version.getCanonicalUri() != null) {
            message.setId(version.getCanonicalUri());
        }
        if (version.getDuration() != null) {
            message.setDuration(version.getDuration());
        }
        if (version.is3d() != null) {
            message.setIsThreeD(version.is3d());
        }
        if (version.getPublishedDuration() != null) {
            message.setPublishedDuration(version.getPublishedDuration());
        }
        if (version.getRestriction() != null) {
            Restriction restriction = version.getRestriction();
            if (restriction.getMinimumAge() != null) {
                message.setMinimumAge(restriction.getMinimumAge());
            }
            if (restriction.isRestricted() != null) {
                message.setRestricted(restriction.isRestricted());
            }
            if (restriction.getMessage() != null) {
                message.setRestrictionMessage(restriction.getMessage());
            }
        }
        return message.build();
    }

    public Set<Version> deserialize(ContentProtos.Content message) {
        Set<Version> versions = Sets.newHashSet();
        for (ContentProtos.Version version : message.getVersionsList()) {
            versions.add(deserialize(version));
        }
        for (int i = 0; i < message.getBroadcastsCount(); i++) {
            ContentProtos.Broadcast broadcast = message.getBroadcasts(i);
            findVersion(versions, broadcast.getVersion())
                .addBroadcast(broadcastSerializer.deserialize(broadcast));
        }
        for (int i = 0; i < message.getEncodingsCount(); i++) {
            ContentProtos.Encoding encoding = message.getEncodings(i);
            findVersion(versions, encoding.getVersion())
                .addManifestedAs(encodingSerializer.deserialize(encoding));
        }
        SetMultimap<String, SegmentEvent> segmentEvents = deserializeSegmentEvents(message.getSegmentEventsList());
        for (Entry<String, Collection<SegmentEvent>> versionEvents : segmentEvents.asMap().entrySet()) {
            findVersion(versions, versionEvents.getKey()).setSegmentEvents(versionEvents.getValue());
        }
        return ImmutableSet.copyOf(versions);
    }

    private SetMultimap<String, SegmentEvent> deserializeSegmentEvents(List<ContentProtos.SegmentEvent> segmentEvents) {
        HashMultimap<String, SegmentEvent> segmentEventsIndex = HashMultimap.create();
        for (ContentProtos.SegmentEvent segmentEvent : segmentEvents) {
            segmentEventsIndex.put(segmentEvent.getVersion(), segmentEventSerializer.deserialize(segmentEvent));
        }
        return segmentEventsIndex;
    }

    private Version findVersion(Set<Version> versions, String versionId) {
        if (Strings.emptyToNull(versionId) != null) {
            for (Version version : versions) {
                if (versionId.equals(version.getCanonicalUri())) {
                    return version;
                }
            }
        }
        Version version = Iterables.getFirst(versions, new Version());
        versions.add(version);
        return version;
    }


    private Version deserialize(ContentProtos.Version message) {
        Version version = new Version();
        version.setCanonicalUri(message.hasId() ? message.getId() : null);
        if (message.hasDuration()) {
            version.setDuration(Duration.standardSeconds(message.getDuration()));
        }
        version.set3d(message.hasIsThreeD() ? message.getIsThreeD() : null);
        version.setPublishedDuration(message.hasPublishedDuration() ? message.getPublishedDuration() : null);
        version.setRestriction(restrictionFrom(message));
        return version;
    }

    private Restriction restrictionFrom(ContentProtos.Version message) {
        Restriction restriction = new Restriction();
        restriction.setMinimumAge(message.hasMinimumAge() ? message.getMinimumAge() : null);
        restriction.setRestricted(message.hasRestricted() ? message.getRestricted() : null);
        restriction.setMessage(message.hasRestrictionMessage() ? message.getRestrictionMessage() : null);
        return restriction;
    }
    
}
