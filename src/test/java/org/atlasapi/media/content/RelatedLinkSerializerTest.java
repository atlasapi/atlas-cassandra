package org.atlasapi.media.content;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.atlasapi.media.entity.RelatedLink;
import org.atlasapi.media.entity.RelatedLink.Builder;
import org.junit.Test;


public class RelatedLinkSerializerTest {

    private final RelatedLinkSerializer serializer = new RelatedLinkSerializer();
    
    @Test
    public void testDeSerializeRelatedLink() {
        Builder linkBuilder = RelatedLink.facebookLink("url");
        serializeAndCheck(linkBuilder.build());
        serializeAndCheck(linkBuilder.withDescription("desc").build());
        serializeAndCheck(linkBuilder.withImage("image").build());
        serializeAndCheck(linkBuilder.withShortName("shortName").build());
        serializeAndCheck(linkBuilder.withSourceId("sourceId").build());
        serializeAndCheck(linkBuilder.withThumbnail("thumbnail").build());
        serializeAndCheck(linkBuilder.withTitle("title").build());
    }

    private void serializeAndCheck(RelatedLink link) {
        RelatedLink deserialized = serializer.deserialize(serializer.serialize(link));
        assertThat(deserialized.getUrl(), is(link.getUrl()));
        assertThat(deserialized.getType(), is(link.getType()));
        assertThat(deserialized.getDescription(), is(link.getDescription()));
        assertThat(deserialized.getImage(), is(link.getImage()));
        assertThat(deserialized.getShortName(), is(link.getShortName()));
        assertThat(deserialized.getSourceId(), is(link.getSourceId()));
        assertThat(deserialized.getThumbnail(), is(link.getThumbnail()));
        assertThat(deserialized.getTitle(), is(link.getTitle()));
    }

}
