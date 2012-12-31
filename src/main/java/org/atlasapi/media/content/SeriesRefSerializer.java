package org.atlasapi.media.content;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.common.ProtoBufUtils;
import org.atlasapi.media.entity.SeriesRef;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;

import com.google.common.primitives.Ints;

public class SeriesRefSerializer {

    public CommonProtos.Reference.Builder serialize(SeriesRef series) {
        CommonProtos.Reference.Builder ref = CommonProtos.Reference.newBuilder();
        ref.setId(series.getId().longValue());
        ref.setSort(series.getTitle());
        ref.setUpdated(ProtoBufUtils.serializeDateTime(series.getUpdated()));
        return ref;
    }

    public SeriesRef deserialize(Reference ref) {
        return new SeriesRef(
            Id.valueOf(ref.getId()),
            ref.getSort(),
            Ints.saturatedCast(ref.getPosition()),
            ProtoBufUtils.deserializeDateTime(ref.getUpdated()));
    }

}
