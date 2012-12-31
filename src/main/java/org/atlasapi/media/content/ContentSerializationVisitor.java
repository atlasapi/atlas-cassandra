package org.atlasapi.media.content;

import org.atlasapi.media.common.ProtoBufUtils;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Certificate;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.ContentGroupRef;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Image;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.RelatedLink;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.SeriesRef;
import org.atlasapi.media.entity.Song;
import org.atlasapi.media.entity.Subtitles;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Alias;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Content.Builder;

import com.metabroadcast.common.intl.Countries;

final class ContentSerializationVisitor implements ContentVisitor<Builder> {
    
    private final VersionsSerializer versionsSerializer = new VersionsSerializer();
    private final TopicRefSerializer topicRefSerializer = new TopicRefSerializer();
    private final RelatedLinkSerializer relatedLinkSerializer = new RelatedLinkSerializer();
    private final KeyPhraseSerializer keyPhraseSerializer = new KeyPhraseSerializer();
    private final CrewMemberSerializer crewMemberSerializer = new CrewMemberSerializer();
    private final ContainerSummarySerializer containerSummarySerializer = new ContainerSummarySerializer();
    private final ReleaseDateSerializer releaseDateSerializer = new ReleaseDateSerializer();
    private final ChildRefSerializer childRefSerializer = new ChildRefSerializer();
    private static final SeriesRefSerializer seriesRefSerializer = new SeriesRefSerializer();
    private final CertificateSerializer certificateSerializer = new CertificateSerializer();
    
    private Builder visitIdentified(Identified ided) {
        Builder builder = ContentProtos.Content.newBuilder();
        if (ided.getId() != null) {
            builder.setId(ided.getId().longValue())
                .setType(ided.getClass().getSimpleName().toLowerCase());
        }
        if (ided.getLastUpdated() != null) {
            builder.setLastUpdated(ProtoBufUtils.serializeDateTime(ided.getLastUpdated()));
        }
        if (ided.getCanonicalUri() != null) {
            builder.setUri(ided.getCanonicalUri());
        }
        for (String alias : ided.getAliases()) {
            builder.addAliases(Alias.newBuilder().setValue(alias));
        }
        for (LookupRef equivRef : ided.getEquivalentTo()) {
            builder.addEquivs(CommonProtos.Reference.newBuilder()
                .setId(equivRef.id().longValue())
                .setSource(equivRef.publisher().key())
                .setType(equivRef.category().name())
            );
        }
        return builder;
    }
    
    private Builder visitDescribed(Described content) {
        Builder builder = visitIdentified(content);
        if (content.getThisOrChildLastUpdated() != null) {
            builder.setChildLastUpdated(ProtoBufUtils.serializeDateTime(content.getThisOrChildLastUpdated()));
        }
        if (content.getPublisher() != null) {
            builder.setSource(content.getPublisher().key());
        }
        if (content.getFirstSeen() != null) {
            builder.setFirstSeen(ProtoBufUtils.serializeDateTime(content.getFirstSeen()));
        }
        if (content.getMediaType() != null && !MediaType.VIDEO.equals(content.getMediaType())) {
            builder.setMediaType(content.getMediaType().toKey());
        }
        if (content.getTitle() != null) {
            builder.addTitlesBuilder().setValue(content.getTitle()).build();
        }
        if (content.getDescription() != null) {
            builder.setDescription(content.getDescription());
        }
        ContentProtos.Synopsis.Builder synopsis = ContentProtos.Synopsis.newBuilder();
        boolean includeSynopsis = false;
        if (content.getShortDescription() != null) {
            synopsis.setShort(content.getShortDescription());
            includeSynopsis = true;
        }
        if (content.getMediumDescription() != null) {
            synopsis.setMedium(content.getMediumDescription());
            includeSynopsis = true;
        }
        if (content.getLongDescription() != null) {
            synopsis.setLong(content.getLongDescription());
            includeSynopsis = true;
        }
        if (includeSynopsis) {
            builder.addSynopses(synopsis);
        }
        builder.addAllGenres(content.getGenres());
        if (content.getImage() != null) {
            builder.setImage(content.getImage());
        }
        if (content.getThumbnail() != null) {
            builder.setThumb(content.getThumbnail());
        }
        for (Image image : content.getImages()) {
            builder.addImagesBuilder().setUri(image.getCanonicalUri());
        }
        if (content.getPresentationChannel() != null) {
            builder.setPresentationChannel(content.getPresentationChannel());
        }
        if (content.isScheduleOnly()) {
            builder.setScheduleOnly(content.isScheduleOnly());
        }
        if (content.getSpecialization() != null) {
            builder.setSpecialization(content.getSpecialization().toString());
        }
        return builder;
    }

    private Builder visitContent(Content content) {
        Builder builder = visitDescribed(content);
        for (Certificate certificate : content.getCertificates()) {
            builder.addCertificates(certificateSerializer.serialize(certificate)
            );
        }
        for (CrewMember crew : content.people()) {
            builder.addCrewMembers(crewMemberSerializer.serialize(crew));
        }
        for (Clip clip : content.getClips()) {
            builder.addClips(clip.accept(this));
        }
        for (ContentGroupRef groupRef : content.getContentGroupRefs()) {
            builder.addContentGroupsBuilder()
                .setId(groupRef.getId().longValue())
                .build();
        }
        
        builder.addAllLanguage(content.getLanguages());
        
        for (KeyPhrase keyPhrase : content.getKeyPhrases()) {
            builder.addKeyPhrases(keyPhraseSerializer.serialize(keyPhrase));
        }
        
        for (RelatedLink relatedLink : content.getRelatedLinks()) {
            builder.addRelatedLink(relatedLinkSerializer.serialize(relatedLink));
        }
        
        for (TopicRef topicRef : content.getTopicRefs()) {
            builder.addTopicRefs(topicRefSerializer.serialize(topicRef));
        }
        
        if (content.getYear() != null) {
            builder.setYear(content.getYear());
        }
        return builder;
    }

    private Builder visitItem(Item item) {
        Builder builder = visitContent(item);
        if (item.getContainer() != null) {
            builder.setContainerRef(Reference.newBuilder()
                .setId(item.getContainer().getId().longValue())
                .setType(item.getContainer().getType().toString()));
        }
        if (item.getContainerSummary() != null) {
            builder.setContainerSummary(containerSummarySerializer.serialize(item.getContainerSummary()));
        }
        if (item.getBlackAndWhite() != null) {
            builder.setBlackAndWhite(item.getBlackAndWhite());
        }
        if (!item.getCountriesOfOrigin().isEmpty()) {
            builder.addAllCountries(Countries.toCodes(item.getCountriesOfOrigin()));
        }
        if (item.getIsLongForm()) {
            builder.setLongform(item.getIsLongForm());
        }
        builder.mergeFrom(versionsSerializer.serialize(item.getVersions()));
        return builder;
    }

    
    private Builder visitContainer(Container container) {
        Builder builder = visitContent(container);
        for (ChildRef child : container.getChildRefs()) {
            builder.addChildren(childRefSerializer.serialize(child));
        }
        return builder;
    }

    @Override
    public Builder visit(Brand brand) {
        Builder builder = visitContainer(brand);
        for (SeriesRef seriesRef : brand.getSeriesRefs()) {
            builder.addSecondaries(seriesRefSerializer.serialize(seriesRef));
        }
        return builder;
    }

    @Override
    public Builder visit(Series series) {
        Builder builder = visitContainer(series);
        if (series.getParent() != null) {
            builder.setContainerRef(Reference.newBuilder()
                .setId(series.getParent().getId().longValue())
                .setType(series.getParent().getType().toString()));
        }
        if (series.getSeriesNumber() != null) {
            builder.setSeriesNumber(series.getSeriesNumber());
        }
        if (series.getTotalEpisodes() != null) {
            builder.setTotalEpisodes(series.getTotalEpisodes());
        }
        return builder;
    }

    @Override
    public Builder visit(Episode episode) {
        Builder builder = visitItem(episode);
        if (episode.getSeriesRef() != null) {
            builder.setSeriesRef(Reference.newBuilder()
                .setId(episode.getSeriesRef().getId().longValue())
                .setType(episode.getContainer().getType().toString()));
        }
        if (episode.getSeriesNumber() != null) {
            builder.setSeriesNumber(episode.getSeriesNumber());
        }
        if (episode.getEpisodeNumber() != null) {
            builder.setEpisodeNumber(episode.getEpisodeNumber());
        }
        if (episode.getPartNumber() != null) {
            builder.setPartNumber(episode.getPartNumber());
        }
        return builder;
    }

    @Override
    public Builder visit(Film film) {
        Builder builder = visitItem(film);
        for (ReleaseDate releaseDate : film.getReleaseDates()) {
            builder.addReleaseDates(releaseDateSerializer .serialize(releaseDate));
        }
        if (film.getWebsiteUrl() != null) {
            builder.setWebsiteUrl(film.getWebsiteUrl());
        }
        for (Subtitles subtitles : film.getSubtitles()) {
            ContentProtos.Subtitle.Builder sub = ContentProtos.Subtitle.newBuilder();
            sub.setLanguage(subtitles.code());
            builder.addSubtitles(sub);
        }
        return builder;
    }

    @Override
    public Builder visit(Song song) {
        Builder builder = visitItem(song);
        if (song.getIsrc() != null) {
            builder.setIsrc(song.getIsrc());
        }
        if (song.getDuration() != null) {
            builder.setDuration(song.getDuration().getMillis());
        }
        return builder;
    }

    @Override
    public Builder visit(Item item) {
        return visitItem(item);
    }
    
    @Override
    public Builder visit(Clip clip) {
        return visitItem(clip);
    }
    
}