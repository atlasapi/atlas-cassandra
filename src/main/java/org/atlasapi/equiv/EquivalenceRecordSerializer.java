package org.atlasapi.equiv;

import java.util.Collection;
import java.util.List;

import org.atlasapi.equiv.EquivalenceRecord;
import org.atlasapi.equiv.EquivalenceRef;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.common.Serializer;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;
import org.atlasapi.serialization.protobuf.EquivProtos;
import org.atlasapi.serialization.protobuf.EquivProtos.EquivRecord;
import org.joda.time.DateTime;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.metabroadcast.common.time.DateTimeZones;


public class EquivalenceRecordSerializer implements Serializer<EquivalenceRecord, byte[]> {

    @Override
    public byte[] serialize(EquivalenceRecord src) {
        return EquivProtos.EquivRecord.newBuilder()
                .setId(src.getId().longValue())
                .setSource(src.getPublisher().key())
                .setCreated(serialize(src.getCreated()))
                .setUpdated(serialize(src.getUpdated()))
                .addAllGenerated(serialize(src.getGeneratedAdjacents()))
                .addAllExplicit(serialize(src.getExplicitAdjacents()))
                .addAllEquivalents(serialize(src.getEquivalents()))
            .build()
            .toByteArray();
    }

    private List<Reference> serialize(Collection<EquivalenceRef> equivRefs) {
        List<Reference> refs = Lists.newArrayListWithCapacity(equivRefs.size());
        for (EquivalenceRef ref: equivRefs) {
            refs.add(CommonProtos.Reference.newBuilder()
                    .setId(ref.getId().longValue())
                    .setSource(ref.getPublisher().key())
                    .build());
        }
        return refs;
    }

    private CommonProtos.DateTime.Builder serialize(DateTime dateTime) {
        return CommonProtos.DateTime.newBuilder().setMillis(dateTime.toDateTime(DateTimeZones.UTC).getMillis());
    }

    @Override
    public EquivalenceRecord deserialize(byte[] dest) {
        try {
            return deserailize(EquivProtos.EquivRecord.parseFrom(dest));
        } catch (InvalidProtocolBufferException e) {
            throw Throwables.propagate(e);
        }
    }

    private EquivalenceRecord deserailize(EquivRecord rec) {
        EquivalenceRef selfRef = new EquivalenceRef(Id.valueOf(rec.getId()), 
                Publisher.fromKey(rec.getSource()).requireValue());
        return new EquivalenceRecord(selfRef, deserialize(rec.getGeneratedList()), 
                deserialize(rec.getExplicitList()), deserialize(rec.getEquivalentsList()), 
                deserialize(rec.getCreated()), deserialize(rec.getUpdated()));
    }

    private DateTime deserialize(CommonProtos.DateTime dateTime) {
        return new DateTime(dateTime.getMillis(), DateTimeZones.UTC);
    }

    private Iterable<EquivalenceRef> deserialize(List<Reference> refs) {
        ImmutableSet.Builder<EquivalenceRef> equivRefs = ImmutableSet.builder();
        for (Reference ref : refs) {
            equivRefs.add(new EquivalenceRef(Id.valueOf(ref.getId()), 
                    Publisher.fromKey(ref.getSource()).requireValue()));
        }
        return equivRefs.build();
    }

}
