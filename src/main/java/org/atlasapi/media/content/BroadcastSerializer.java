package org.atlasapi.media.content;

import static org.atlasapi.media.common.ProtoBufUtils.deserializeDateTime;
import static org.atlasapi.media.common.ProtoBufUtils.serializeDateTime;

import org.atlasapi.media.common.IdentifiedSerializer;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Broadcast.Builder;

public class BroadcastSerializer {

    private final IdentifiedSerializer identifiedSerializer = new IdentifiedSerializer();
    
    public ContentProtos.Broadcast.Builder serialize(Broadcast broadcast) {
        Builder builder = ContentProtos.Broadcast.newBuilder();
        builder.setIdentification(identifiedSerializer.serialize(broadcast));
        builder.setChannelUri(broadcast.getBroadcastOn());
        builder.setTransmissionTime(serializeDateTime(broadcast.getTransmissionTime()));
        builder.setTransmissionEndTime(serializeDateTime(broadcast.getTransmissionEndTime()));
        if (broadcast.getScheduleDate() != null) {
            builder.setScheduleDate(serializeDateTime(broadcast.getScheduleDate().toDateTimeAtStartOfDay()));
        }
        if (broadcast.getSourceId() != null) {
            builder.setSourceId(broadcast.getSourceId());
        }
        if (broadcast.isActivelyPublished() != null) {
            builder.setActivelyPublished(broadcast.isActivelyPublished());
        }
        if (broadcast.getRepeat() != null) {
            builder.setRepeat(broadcast.getRepeat());
        }
        if (broadcast.getSubtitled() != null) {
            builder.setSubtitled(broadcast.getSubtitled());
        }
        if (broadcast.getSigned() != null) {
            builder.setSigned(broadcast.getSigned());
        }
        if (broadcast.getAudioDescribed() != null) {
            builder.setAudioDescribed(broadcast.getAudioDescribed());
        }
        if (broadcast.getHighDefinition() != null) {
            builder.setHighDefinition(broadcast.getHighDefinition());
        }
        if (broadcast.getWidescreen() != null) {
            builder.setWidescreen(broadcast.getWidescreen());
        }
        if (broadcast.getSurround() != null) {
            builder.setSurround(broadcast.getSurround());
        }
        if (broadcast.getLive() != null) {
            builder.setLive(broadcast.getLive());
        }
        if (broadcast.getNewSeries() != null) {
            builder.setNewSeries(broadcast.getNewSeries());
        }
        if (broadcast.getPremiere() != null) {
            builder.setPremiere(broadcast.getPremiere());
        }
        return builder;
    }
    

    public Broadcast deserialize(ContentProtos.Broadcast msg) {
        Broadcast broadcast = new Broadcast(msg.getChannelUri(),
            deserializeDateTime(msg.getTransmissionTime()),
            deserializeDateTime(msg.getTransmissionEndTime()));
        identifiedSerializer.deserialize(msg.getIdentification(), broadcast);
        if (msg.hasScheduleDate()) {
            broadcast.setScheduleDate(deserializeDateTime(msg.getScheduleDate()).toLocalDate());
        }
        broadcast.withId(msg.hasSourceId() ? msg.getSourceId() : null);
        broadcast.setIsActivelyPublished(msg.hasActivelyPublished() ? msg.getActivelyPublished() : null);
        broadcast.setRepeat(msg.hasRepeat() ? msg.getRepeat() : null);
        broadcast.setSubtitled(msg.hasSubtitled() ? msg.getSubtitled() : null);
        broadcast.setSigned(msg.hasSigned() ? msg.getSigned() : null);
        broadcast.setAudioDescribed(msg.hasAudioDescribed() ? msg.getAudioDescribed() : null);
        broadcast.setHighDefinition(msg.hasHighDefinition() ? msg.getHighDefinition() : null);
        broadcast.setWidescreen(msg.hasWidescreen() ? msg.getWidescreen() : null);
        broadcast.setSurround(msg.hasSurround() ? msg.getSurround() : null);
        broadcast.setLive(msg.hasLive() ? msg.getLive() : null);
        broadcast.setNewSeries(msg.hasNewSeries() ? msg.getNewSeries() : null);
        broadcast.setPremiere(msg.hasPremiere() ? msg.getPremiere() : null);
        return broadcast;
    }
    
}
