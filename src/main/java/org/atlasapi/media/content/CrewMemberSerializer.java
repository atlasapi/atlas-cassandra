package org.atlasapi.media.content;

import org.atlasapi.media.entity.Actor;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.CrewMember.Role;
import org.atlasapi.serialization.protobuf.ContentProtos;

import com.google.common.collect.ImmutableSet;

public class CrewMemberSerializer {

    public ContentProtos.CrewMember serialize(CrewMember crew) {
        ContentProtos.CrewMember.Builder crewMember = ContentProtos.CrewMember.newBuilder()
            .setUri(crew.getCanonicalUri())
            .setRole(crew.role().key())
            .setName(crew.name());
        if (crew instanceof Actor) {
            crewMember.setCharacter(((Actor) crew).character());
        }
        return crewMember.build();
    }

    public CrewMember deserialize(ContentProtos.CrewMember crewMember) {
        Role role = Role.fromKey(crewMember.getRole());
        CrewMember member;
        if (role == Role.ACTOR) {
            member = new Actor().withCharacter(crewMember.getCharacter());
        } else {
            member = new CrewMember();
        }
        member.withName(crewMember.getName())
            .withRole(role)
            .withProfileLinks(ImmutableSet.copyOf(crewMember.getProfileLinksList()));
        member.setCanonicalUri(crewMember.getUri());
        return member;
    }

}
