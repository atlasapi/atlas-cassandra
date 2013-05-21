package org.atlasapi.media.content.schedule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.atlasapi.media.common.Serializer;
import org.atlasapi.media.content.BroadcastSerializer;
import org.atlasapi.media.content.ContentSerializer;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.util.ItemAndBroadcast;
import org.atlasapi.serialization.protobuf.ContentProtos;

public class ItemAndBroadcastSerializer implements Serializer<ItemAndBroadcast, byte[]> {

    private final ContentSerializer contentSerializer = new ContentSerializer();
    private final BroadcastSerializer broadcastSerializer = new BroadcastSerializer();
    
    @Override
    public byte[] serialize(ItemAndBroadcast src) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            serializeItem(src).writeDelimitedTo(output);
            serializeBroadcast(src).writeDelimitedTo(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ContentProtos.Broadcast serializeBroadcast(ItemAndBroadcast src) {
        return broadcastSerializer.serialize(src.getBroadcast()).build();
    }

    private ContentProtos.Content serializeItem(ItemAndBroadcast src) {
        return contentSerializer.serialize(src.getItem());
    }

    @Override
    public ItemAndBroadcast deserialize(byte[] dest) {
        try {
            InputStream input = new ByteArrayInputStream(dest);
            return new ItemAndBroadcast(deserializeItem(input), deserializeBroadcast(input));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Broadcast deserializeBroadcast(InputStream input) throws IOException {
        ContentProtos.Broadcast broadcastMsg = ContentProtos.Broadcast.parseDelimitedFrom(input);
        return broadcastSerializer.deserialize(broadcastMsg);
    }

    private Item deserializeItem(InputStream input) throws IOException {
        ContentProtos.Content contentMsg = ContentProtos.Content.parseDelimitedFrom(input);
        return (Item) contentSerializer.deserialize(contentMsg);
    }

}
