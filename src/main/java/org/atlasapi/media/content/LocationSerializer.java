package org.atlasapi.media.content;

import static org.atlasapi.media.common.ProtoBufUtils.deserializeDateTime;
import static org.atlasapi.media.common.ProtoBufUtils.serializeDateTime;

import java.util.Currency;

import org.atlasapi.media.TransportSubType;
import org.atlasapi.media.TransportType;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.Policy;
import org.atlasapi.media.entity.Policy.Platform;
import org.atlasapi.media.entity.Policy.RevenueContract;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Location.Builder;

import com.metabroadcast.common.currency.Price;
import com.metabroadcast.common.intl.Countries;


public class LocationSerializer {

    public ContentProtos.Location.Builder serialize(Location location) {
        Builder builder = ContentProtos.Location.newBuilder();
        if (location.getEmbedCode() != null) { builder.setEmbedCode(location.getEmbedCode()); }
        if (location.getEmbedId() != null) { builder.setEmbedId(location.getEmbedId()); }
        if (location.getTransportIsLive() != null) { builder.setTransportIsLive(location.getTransportIsLive()); }
        if (location.getTransportSubType() != null) {
            builder.setTransportSubType(location.getTransportSubType().toString());
        }
        if (location.getTransportType() != null) {
            builder.setTransportType(location.getTransportType().toString());
        }
        if (location.getUri() != null) { builder.setUri(location.getUri()); }
        
        Policy policy = location.getPolicy();
        if (policy == null) {
            return builder;
        }
        
        if (policy.getActualAvailabilityStart() != null) {
            builder.setActualAvailabilityStart(serializeDateTime(policy.getActualAvailabilityStart()));
        }
        if (policy.getAvailabilityStart() != null) {
            builder.setAvailabilityStart(serializeDateTime(policy.getAvailabilityStart()));
        }
        if (policy.getAvailabilityEnd() != null) {
            builder.setAvailabilityEnd(serializeDateTime(policy.getAvailabilityEnd()));
        }
        if (policy.getAvailabilityLength() != null) { builder.setAvailabilityLength(policy.getAvailabilityLength()); }
        builder.addAllAvailableCountries(Countries.toCodes(policy.getAvailableCountries()));

        if (policy.getDrmPlayableFrom() != null) {
            builder.setDrmPlayableFrom(serializeDateTime(policy.getDrmPlayableFrom()));
        }
        if (policy.getNetwork() != null) {
            builder.setNetwork(policy.getNetwork().key());
        }
        if (policy.getPlatform() != null) {
            builder.setPlatform(policy.getPlatform().key());
        }
        if (policy.getPrice() != null) {
            builder.setCurrency(policy.getPrice().getCurrency().getCurrencyCode());
            builder.setAmount(policy.getPrice().getAmount());
        }
        if (policy.getRevenueContract() != null) {
            builder.setRevenueContract(policy.getRevenueContract().key());
        }
        
        return builder;
    }
    
    public Location deserialize(ContentProtos.Location msg) {
        Location location = new Location();
        location.setEmbedCode(msg.hasEmbedCode() ? msg.getEmbedCode() : null);
        location.setEmbedId(msg.hasEmbedId() ? msg.getEmbedId() : null);
        location.setTransportIsLive(msg.hasTransportIsLive() ? msg.getTransportIsLive() : null);
        location.setUri(msg.hasUri() ? msg.getUri() : null);
        if (msg.hasTransportType()) {
            location.setTransportType(TransportType.fromString(msg.getTransportType()));
        }
        if (msg.hasTransportSubType()) {
            location.setTransportSubType(TransportSubType.fromString(msg.getTransportSubType()));
        }
        
        Policy policy = new Policy();
        
        if (msg.hasActualAvailabilityStart()) {
            policy.setActualAvailabilityStart(deserializeDateTime(msg.getActualAvailabilityStart()));
        }
        if (msg.hasAvailabilityStart()) {
            policy.setAvailabilityStart(deserializeDateTime(msg.getAvailabilityStart()));
        }
        if (msg.hasAvailabilityEnd()) {
            policy.setAvailabilityEnd(deserializeDateTime(msg.getAvailabilityEnd()));
        }
        policy.setAvailabilityLength(msg.hasAvailabilityLength() ? msg.getAvailabilityLength() : null);
        policy.setAvailableCountries(Countries.fromCodes(msg.getAvailableCountriesList()));

        if (msg.hasDrmPlayableFrom()) {
            policy.setDrmPlayableFrom(deserializeDateTime(msg.getDrmPlayableFrom()));
        }
        if (msg.hasNetwork()) {
            policy.setNetwork(Policy.Network.fromKey(msg.getNetwork()));
        }
        if (msg.hasPlatform()) {
            policy.setPlatform(Platform.fromKey(msg.getPlatform()));
        }
        if (msg.hasAmount() && msg.hasCurrency()) {
            policy.setPrice(new Price(
                Currency.getInstance(msg.getCurrency()), 
                msg.getAmount()
            ));
        }
        if (msg.hasRevenueContract()) {
            policy.setRevenueContract(RevenueContract.fromKey(msg.getRevenueContract()));
        }
        location.setPolicy(policy);
        return location;
    }
    
}
