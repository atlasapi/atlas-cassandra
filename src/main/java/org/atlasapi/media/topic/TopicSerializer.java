package org.atlasapi.media.topic;

import org.atlasapi.media.common.Serializer;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.topic.Topic.Type;
import org.atlasapi.serialization.protobuf.TopicProtos;
import org.atlasapi.serialization.protobuf.TopicProtos.Topic.Builder;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;

public class TopicSerializer implements Serializer<Topic, byte[]> {

    @Override
    public byte[] serialize(Topic topic) {
        Builder msg = TopicProtos.Topic.newBuilder()
            .setId(topic.getId().longValue())
            .setSource(topic.getPublisher().key())
            .setType(topic.getType().key());
        for (String alias : topic.getAliases()) {
            msg.addAliasesBuilder().setValue(alias);
        }
        if (topic.getTitle() != null) {
            msg.addTitleBuilder().setValue(topic.getTitle());
        }
        if (topic.getDescription() != null) {
            msg.addDescriptionBuilder().setValue(topic.getDescription());
        }
        if (topic.getImage() != null) {
            msg.addImage(topic.getImage());
        }
        if (topic.getThumbnail() != null) {
            msg.addThumbnail(topic.getThumbnail());
        }
        if (topic.getNamespace() != null) {
            msg.setNamespace(topic.getNamespace());
        }
        if (topic.getValue() != null) {
            msg.setValue(topic.getValue());
        }
        return msg.build().toByteArray();
    }

    @Override
    public Topic deserialize(byte[] bytes) {
        try {
            TopicProtos.Topic msg;
            msg = TopicProtos.Topic.parseFrom(bytes);
            Topic topic = new Topic(msg.getId());
            topic.setPublisher(Publisher.fromKey(msg.getSource()).requireValue());
            topic.setType(Type.fromKey(msg.getType()));
            ImmutableList.Builder<String> aliases = ImmutableList.builder();
            for (int i = 0; i < msg.getAliasesCount(); i++) {
                aliases.add(msg.getAliases(i).getValue());
            }
            topic.setAliases(aliases.build());
            if (msg.getTitleCount() > 0) {
                topic.setTitle(msg.getTitle(0).getValue());
            }
            if (msg.getDescriptionCount() > 0) {
                topic.setDescription(msg.getDescription(0).getValue());
            }
            if (msg.getImageCount() > 0) {
                topic.setImage(msg.getImage(0));
            }
            if (msg.getThumbnailCount() > 0) {
                topic.setThumbnail(msg.getThumbnail(0));
            }
            if (msg.hasNamespace()) {
                topic.setNamespace(msg.getNamespace());
            }
            if (msg.hasValue()) {
                topic.setValue(msg.getValue());
            }
            topic.setMediaType(null);
            return topic;
        } catch (InvalidProtocolBufferException e) {
            throw Throwables.propagate(e);
        }
    }

}
