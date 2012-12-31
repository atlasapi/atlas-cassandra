package org.atlasapi.media.content;

import org.atlasapi.media.common.ProtoBufUtils;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.media.entity.ReleaseDate.ReleaseType;
import org.atlasapi.serialization.protobuf.ContentProtos;

import com.metabroadcast.common.intl.Countries;

public class ReleaseDateSerializer {

    public ContentProtos.ReleaseDate serialize(ReleaseDate releaseDate) {
        ContentProtos.ReleaseDate.Builder date = ContentProtos.ReleaseDate.newBuilder();
        date.setDate(ProtoBufUtils.serializeDateTime(releaseDate.date().toDateTimeAtStartOfDay()));
        date.setCountry(releaseDate.country().code());
        date.setType(releaseDate.type().toString());
        return date.build();
    }

    public ReleaseDate deserialize(ContentProtos.ReleaseDate date) {
        return new ReleaseDate(
            ProtoBufUtils.deserializeDateTime(date.getDate()).toLocalDate(),
            Countries.fromCode(date.getCountry()),
            ReleaseType.valueOf(date.getType()));
    }
    
}
