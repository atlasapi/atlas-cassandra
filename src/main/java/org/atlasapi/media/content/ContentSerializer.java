package org.atlasapi.media.content;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Set;

import org.atlasapi.media.common.Serializer;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Song;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Content.Builder;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;

public final class ContentSerializer implements Serializer<Content, ContentProtos.Content> {

    private static final Set<Class<? extends Content>> supportedTypes = supportedTypes();

    private static Set<Class<? extends Content>> supportedTypes() {
        ImmutableSet.Builder<Class<? extends Content>> builder = ImmutableSet.builder();
        builder
            .add(Episode.class)
            .add(Item.class)
            .add(Brand.class)
            .add(Series.class)
            .add(Song.class)
            .add(Clip.class)
            .add(Film.class);
        return builder.build();
    }

    private ImmutableBiMap<String, Class<? extends Content>> typeNameMap;
    private static final ContentSerializationVisitor serializationVisitor = new ContentSerializationVisitor();

    public ContentSerializer() {
        ImmutableBiMap.Builder<String, Class<? extends Content>> typeNameMap = ImmutableBiMap.builder();
        for (Class<? extends Content> type : supportedTypes) {
            typeNameMap.put(typeString(type), type);
        }
        this.typeNameMap = typeNameMap.build();
    }

    /* (non-Javadoc)
     * @see org.atlasapi.media.content.Serializer#serialize(org.atlasapi.media.content.Content)
     */
    @Override
    public ContentProtos.Content serialize(Content content) {
        String type = typeString(content.getClass());
        checkArgument(typeNameMap.containsKey(type), "Unsupported type: " + type);
        Builder builder = content.accept(serializationVisitor);
        return builder.build();
    }

    static String typeString(Class<?> cls) {
        return cls.getSimpleName().toLowerCase();
    }

    /* (non-Javadoc)
     * @see org.atlasapi.media.content.Serializer#deserialize(org.atlasapi.serialization.protobuf.ContentProtos.Content)
     */
    @Override
    public Content deserialize(ContentProtos.Content p) {
        try {
            String type = p.getType();
            Class<? extends Content> cls = typeNameMap.get(type);
            Content content = cls.newInstance();
            return content.accept(new ContentDeserializationVisitor(p));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
