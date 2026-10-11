package org.shpytchuk.telegramingest.storage;

public interface ImageUploader {

    String upload(byte[] jpeg);
}
