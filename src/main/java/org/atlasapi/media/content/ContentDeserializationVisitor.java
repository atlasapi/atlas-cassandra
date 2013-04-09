package org.atlasapi.media.content;

import static org.atlasapi.media.common.ProtoBufUtils.deserializeDateTime;

import org.atlasapi.equiv.EquivalenceRef;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.common.ProtoBufUtils;
import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Certificate;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.ContentGroupRef;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.EntityType;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Image;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.RelatedLink;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.SeriesRef;
import org.atlasapi.media.entity.Song;
import org.atlasapi.media.entity.Specialization;
import org.atlasapi.media.entity.Subtitles;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.serialization.protobuf.CommonProtos;
import org.atlasapi.serialization.protobuf.CommonProtos.Reference;
import org.atlasapi.serialization.protobuf.ContentProtos;
import org.atlasapi.serialization.protobuf.ContentProtos.Subtitle;
import org.atlasapi.serialization.protobuf.ContentProtos.Synopsis;
import org.joda.time.Duration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.metabroadcast.common.intl.Countries;

public class ContentDeserializationVisitor implements ContentVisitor<Content> {

    private static final VersionsSerializer versionsSerializer = new VersionsSerializer();
    private static final TopicRefSerializer topicRefSerializer = new TopicRefSerializer();
    private static final RelatedLinkSerializer relatedLinkSerializer = new RelatedLinkSerializer();
    private static final KeyPhraseSerializer keyPhraseSerializer = new KeyPhraseSerializer();
    private static final CrewMemberSerializer crewMemberSerializer = new CrewMemberSerializer();
    private static final ContainerSummarySerializer containerSummarySerializer = new ContainerSummarySerializer();
    private static final ReleaseDateSerializer releaseDateSerializer = new ReleaseDateSerializer();
    private static final ChildRefSerializer childRefSerializer = new ChildRefSerializer();
    private static final SeriesRefSerializer seriesRefSerializer = new SeriesRefSerializer();
    private static final CertificateSerializer certificateSerializer = new CertificateSerializer();
    
    private ContentProtos.Content msg;

    public ContentDeserializationVisitor(ContentProtos.Content msg) {
        this.msg = msg;
    }
    
    private <I extends Identified> I visitIdentified(I identified) {
        if (msg.hasId()) {
            identified.setId(Id.valueOf(msg.getId()));
        }
        if (msg.hasUri()) {
            identified.setCanonicalUri(msg.getUri());
        }
        if (msg.hasLastUpdated()) {
            identified.setLastUpdated(deserializeDateTime(msg.getLastUpdated()));
        }

        Builder<Alias> aliases = ImmutableSet.builder();
        for (CommonProtos.Alias alias : msg.getAliasesList()) {
            aliases.add(new Alias(alias.getNamespace(), alias.getValue()));
        }
        identified.setAliases(aliases.build());
        
        ImmutableSet.Builder<EquivalenceRef> equivRefs = ImmutableSet.builder();
        for (Reference equivRef : msg.getEquivsList()) {
            equivRefs.add(new EquivalenceRef(Id.valueOf(equivRef.getId()),
                Publisher.fromKey(equivRef.getSource()).requireValue()
            ));
        }
        identified.setEquivalentTo(equivRefs.build());
        return identified;
    }

    private <D extends Described> D visitDescribed(D described) {
        described = visitIdentified(described);
        described.setPublisher(Publisher.fromKey(msg.getSource()).requireValue());
        if (msg.hasFirstSeen()) {
            described.setFirstSeen(ProtoBufUtils.deserializeDateTime(msg.getFirstSeen()));
        }
        if (msg.hasChildLastUpdated()) {
            described.setThisOrChildLastUpdated(ProtoBufUtils.deserializeDateTime(msg.getChildLastUpdated()));
        }
        if (msg.hasMediaType()) {
            described.setMediaType(MediaType.fromKey(msg.getMediaType()).get());
        }
        if (msg.getTitlesCount() > 0) {
            described.setTitle(msg.getTitles(0).getValue());
        }
        if (msg.hasDescription()) {
            described.setDescription(msg.getDescription());
        }
        if (msg.hasImage()) {
            described.setImage(msg.getImage());
        }
        if (msg.hasThumb()) {
            described.setThumbnail(msg.getThumb());
        }
        if (msg.getSynopsesCount() > 0) {
            Synopsis synopsis = msg.getSynopses(0);
            if (synopsis.hasShort()) {
                described.setShortDescription(synopsis.getShort());
            }
            if (synopsis.hasMedium()) {
                described.setMediumDescription(synopsis.getMedium());
            }
            if (synopsis.hasLong()) {
                described.setLongDescription(synopsis.getLong());
            }
        }
        
        ImmutableSet.Builder<Image> images = ImmutableSet.builder();
        for (ContentProtos.Image image : msg.getImagesList()) {
            images.add(new Image(image.getUri()));
        }
        described.setImages(images.build());
        described.setGenres(msg.getGenresList());
        described.setPresentationChannel(msg.getPresentationChannel());
        if (msg.hasScheduleOnly()) {
            described.setScheduleOnly(msg.getScheduleOnly());
        }
        if (msg.hasSpecialization()) {
            described.setSpecialization(Specialization.valueOf(msg.getSpecialization().toUpperCase()));
        }
        return described;
    }
    
    private <C extends Content> C visitContent(C content) {
        content = visitDescribed(content);
        ImmutableSet.Builder<Certificate> certificates = ImmutableSet.builder();
        for (ContentProtos.Certificate cert : msg.getCertificatesList()) {
            certificates.add(certificateSerializer.deserialize(cert));
        }
        content.setCertificates(certificates.build());

        ImmutableSet.Builder<CrewMember> crew = ImmutableSet.builder();
        for (ContentProtos.CrewMember crewMember : msg.getCrewMembersList()) {
            crew.add(crewMemberSerializer.deserialize(crewMember));
        }
        content.setPeople(crew.build().asList());
        
        ImmutableSet.Builder<Clip> clips = ImmutableSet.builder();
        for (ContentProtos.Content clipPb : msg.getClipsList()) {
            clips.add((Clip) new Clip().accept(new ContentDeserializationVisitor(clipPb)));
        }
        content.setClips(clips.build());
        
        ImmutableSet.Builder<ContentGroupRef> groupRefs = ImmutableSet.builder();
        for (Reference groupRef : msg.getContentGroupsList()) {
            groupRefs.add(new ContentGroupRef(
                Id.valueOf(groupRef.getId()),""
            ));
        }
        content.setContentGroupRefs(groupRefs.build());
        
        ImmutableSet.Builder<KeyPhrase> phrases = ImmutableSet.builder();
        for (ContentProtos.KeyPhrase phrase : msg.getKeyPhrasesList()) {
            phrases.add(keyPhraseSerializer.deserialize(phrase));
        }
        content.setKeyPhrases(phrases.build());
        
        content.setLanguages(msg.getLanguageList());
        
        ImmutableSet.Builder<RelatedLink> links = ImmutableSet.builder();
        for (int i = 0; i < msg.getRelatedLinkCount(); i++) {
            links.add(relatedLinkSerializer.deserialize(msg.getRelatedLink(i)));
        }
        content.setRelatedLinks(links.build());
        
        ImmutableSet.Builder<TopicRef> topicRefs = ImmutableSet.builder();
        for (int i = 0; i < msg.getTopicRefsCount(); i++) {
            topicRefs.add(topicRefSerializer.deserialize(msg.getTopicRefs(i)));
        }
        content.setTopicRefs(topicRefs.build());
        
        if (msg.hasYear()) {
            content.setYear(msg.getYear());
        }
        
        return content;
    }

    private <C extends Container> C visitContainer(C container) {
        container = visitContent(container);
        ImmutableSet.Builder<ChildRef> childRefs = ImmutableSet.builder();
        for (int i = 0; i < msg.getChildrenCount(); i++) {
            childRefs.add(childRefSerializer.deserialize(msg.getChildren(i)));
        }
        container.setChildRefs(ChildRef.dedupeAndSort(childRefs.build()));
        return container;
    }

    @Override
    public Brand visit(Brand brand) {
        brand = visitContainer(brand);
        ImmutableSet.Builder<SeriesRef> childRefs = ImmutableSet.builder();
        for (int i = 0; i < msg.getSecondariesCount(); i++) {
            childRefs.add(seriesRefSerializer.deserialize(msg.getSecondaries(i)));
        }
        brand.setSeriesRefs(SeriesRef.dedupeAndSort(childRefs.build()));
        return brand;
    }

    @Override
    public Series visit(Series series) {
        series = visitContainer(series);
        if (msg.hasContainerRef()) {
            series.setParentRef(new ParentRef(
                Id.valueOf(msg.getContainerRef().getId()),
                EntityType.from(msg.getContainerRef().getType())
            ));
        }
        series.withSeriesNumber(msg.hasSeriesNumber() ? msg.getSeriesNumber() : null);
        series.setTotalEpisodes(msg.hasTotalEpisodes() ? msg.getTotalEpisodes() : null);
        return series;
    }

    @Override
    public Episode visit(Episode episode) {
        episode = visitItem(episode);
        if (msg.hasSeriesRef()) {
            episode.setSeriesRef(new ParentRef(
                Id.valueOf(msg.getSeriesRef().getId()),
                EntityType.from(msg.getSeriesRef().getType())
            ));
        }
        episode.setSeriesNumber(msg.hasSeriesNumber() ? msg.getSeriesNumber() : null);
        episode.setEpisodeNumber(msg.hasEpisodeNumber() ? msg.getEpisodeNumber() : null);
        episode.setPartNumber(msg.hasPartNumber() ? msg.getPartNumber() : null);
        return episode;
    }

    @Override
    public Film visit(Film film) {
        film = visitItem(film);
        ImmutableSet.Builder<ReleaseDate> releaseDates = ImmutableSet.builder();
        for (int i = 0; i < msg.getReleaseDatesCount(); i++) {
            releaseDates.add(releaseDateSerializer.deserialize(msg.getReleaseDates(i)));
        }
        film.setReleaseDates(releaseDates.build());
        film.setWebsiteUrl(msg.hasWebsiteUrl() ? msg.getWebsiteUrl() : null);
        ImmutableSet.Builder<Subtitles> subtitles = ImmutableSet.builder();
        for (Subtitle sub : msg.getSubtitlesList()) {
            subtitles.add(new Subtitles(sub.getLanguage()));
        }
        film.setSubtitles(subtitles.build());
        return film;
    }

    @Override
    public Song visit(Song song) {
        song = visitItem(song);
        song.setIsrc(msg.hasIsrc() ? msg.getIsrc() : null);
        song.setDuration(msg.hasDuration() ? Duration.millis(msg.getDuration()) : null);
        return song;
    }

    @Override
    public Item visit(Item item) {
        return visitItem(item);
    }

    private <I extends Item> I visitItem(I item) {
        item = visitContent(item);
        if (msg.hasContainerRef()) {
            item.setParentRef(new ParentRef(
                Id.valueOf(msg.getContainerRef().getId()),
                EntityType.from(msg.getContainerRef().getType())
            ));
        }
        if (msg.hasContainerSummary()) {
            item.setContainerSummary(containerSummarySerializer.deserialize(msg.getContainerSummary()));
        }
        if (msg.hasBlackAndWhite()) {
            item.setBlackAndWhite(msg.getBlackAndWhite());
        }
        item.setCountriesOfOrigin(Countries.fromCodes(msg.getCountriesList()));
        if (msg.hasLongform()) {
            item.setIsLongForm(msg.getLongform());
        }
        item.setVersions(versionsSerializer.deserialize(msg));
        return item;
    }
    
    @Override
    public Content visit(Clip clip) {
        return visitItem(clip);
    }
    
    
}