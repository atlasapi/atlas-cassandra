package org.atlasapi.media.content;

import org.atlasapi.media.entity.Certificate;
import org.atlasapi.serialization.protobuf.ContentProtos;

import com.metabroadcast.common.intl.Countries;

public class CertificateSerializer {

    public ContentProtos.Certificate serialize(Certificate certificate) {
        return ContentProtos.Certificate.newBuilder()
            .setClassification(certificate.classification())
            .setCountry(certificate.country().code()).build();
    }

    public Certificate deserialize(ContentProtos.Certificate cert) {
        return new Certificate(cert.getClassification(), 
            Countries.fromCode(cert.getCountry()));
    }
    
}
