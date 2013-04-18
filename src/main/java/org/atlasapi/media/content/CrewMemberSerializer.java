package org.atlasapi.media.content;

import org.atlasapi.media.entity.Actor;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.CrewMember.Role;
import org.atlasapi.serialization.protobuf.ContentProtos;

import com.google.common.collect.ImmutableSet;

public class CrewMemberSerializer {

    public ContentProtos.CrewMember serialize(CrewMember crew) {
        ContentProtos.CrewMember.Builder crewMember = ContentProtos.CrewMember.newBuilder();
        if (crew.getCanonicalUri() != null) {
            crewMember.setUri(crew.getCanonicalUri());
        }
        if (crew.role() != null) {
            crewMember.setRole(crew.role().key());
        }
        if (crew.name() != null) {
            crewMember.setName(crew.name());
        }
        if (crew instanceof Actor) {
            String character = ((Actor) crew).character();
            if (character != null) {
                crewMember.setCharacter(character);
            }
        }
        crewMember.addAllProfileLinks(crew.profileLinks());
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
