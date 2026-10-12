package org.shpytchuk.ingest.api.storage;

public interface ImageUploader {

    String upload(byte[] jpeg);
}
