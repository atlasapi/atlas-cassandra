package org.atlasapi.media.content;

import org.atlasapi.media.common.ProtoBufUtils;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.EntityType;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;

public class ChildRefSerializer {

    public CommonProtos.Reference.Builder serialize(ChildRef child) {
        CommonProtos.Reference.Builder ref = CommonProtos.Reference.newBuilder();
        ref.setId(child.getId().longValue());
        ref.setSort(child.getSortKey());
        ref.setUpdated(ProtoBufUtils.serializeDateTime(child.getUpdated()));
        ref.setType(child.getType().toString());
        return ref;
    }

    public ChildRef deserialize(Reference ref) {
        return new ChildRef(
            ref.getId(),
            ref.getSort(),
            ProtoBufUtils.deserializeDateTime(ref.getUpdated()),
            EntityType.from(ref.getType()));
    }

}
