package org.atlasapi.media.common;

import static org.atlasapi.media.common.ProtoBufUtils.deserializeDateTime;

import org.atlasapi.equiv.EquivalenceRef;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Alias;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;
import org.joda.time.DateTime;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;


public class IdentifiedSerializer {

    public CommonProtos.Identification.Builder serialize(Identified identified) {
        CommonProtos.Identification.Builder id = CommonProtos.Identification.newBuilder()
            .setType(identified.getClass().getSimpleName().toLowerCase());
        if (identified.getId() != null) {
            id.setId(identified.getId().longValue());
        }
        if (identified.getLastUpdated() != null) {
            id.setLastUpdated(ProtoBufUtils.serializeDateTime(identified.getLastUpdated()));
        }
        if (identified.getCanonicalUri() != null) {
            id.setUri(identified.getCanonicalUri());
        }
        for (String alias : identified.getAliases()) {
            id.addAliases(Alias.newBuilder().setValue(alias));
        }
        for (EquivalenceRef equivRef : identified.getEquivalentTo()) {
            id.addEquivs(CommonProtos.Reference.newBuilder()
                .setId(equivRef.getId().longValue())
                .setSource(equivRef.getPublisher().key())
            );
        }
        return id;
    }
    
    public <I extends Identified> I deserialize(CommonProtos.Identification msg, I identified) {
        if (msg.hasId()) {
            identified.setId(Id.valueOf(msg.getId()));
        }
        if (msg.hasUri()) {
            identified.setCanonicalUri(msg.getUri());
        }
        if (msg.hasLastUpdated()) {
            DateTime lastUpdated = deserializeDateTime(msg.getLastUpdated());
            identified.setLastUpdated(lastUpdated);
        }

        Builder<String> aliases = ImmutableSet.builder();
        for (Alias alias : msg.getAliasesList()) {
            aliases.add(alias.getValue());
        }
        identified.setAliases(aliases.build());
        
        ImmutableSet.Builder<EquivalenceRef> equivRefs = ImmutableSet.builder();
        for (Reference equivRef : msg.getEquivsList()) {
            equivRefs.add(new EquivalenceRef(Id.valueOf(equivRef.getId()),
                Publisher.fromKey(equivRef.getSource()).requireValue()
            ));
        }
        identified.setEquivalentTo(equivRefs.build());
        return identified;
    }
}
