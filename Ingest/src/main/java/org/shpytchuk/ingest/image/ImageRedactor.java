package org.shpytchuk.ingest.image;

public interface ImageRedactor {

    /**
     * @param documentHint the post text says the photo shows a document; OCR can also recognize
     *                     a document on its own
     */
    RedactedImage redact(byte[] image, boolean documentHint);


    RedactedImage redactOnPhotoEvidence(byte[] image);

    RedactedImage blurFully(byte[] image);

    boolean mentionsDocument(String text);
}
