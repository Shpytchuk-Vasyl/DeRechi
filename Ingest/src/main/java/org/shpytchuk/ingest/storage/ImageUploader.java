package org.shpytchuk.ingest.storage;

public interface ImageUploader {

    String upload(byte[] jpeg);
}
