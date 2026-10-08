package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.exception.RejectedUploadException;
import org.shpytchuk.adminapi.service.ImageStorage;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping(UploadController.BASE_PATH)
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    public static final String BASE_PATH = "/admin/uploads";

    private final ImageStorage storage;
    private final MessageSource messages;

    public UploadController(ImageStorage storage, MessageSource messages) {
        this.storage = storage;
        this.messages = messages;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("@perm.canManageItems(authentication)")
    @ResponseBody
    public String upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        String key = storage.store(file);
        log.info("{} uploaded image {} ({} bytes)", authentication.getName(), key, file.getSize());
        return key;
    }

    @DeleteMapping
    @PreAuthorize("@perm.canManageItems(authentication)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void revert(@RequestBody String key, Authentication authentication) {
        storage.delete(key);
        log.info("{} deleted uploaded image {}", authentication.getName(), key);
    }

    @ExceptionHandler(RejectedUploadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String rejected(RejectedUploadException exception, Authentication authentication) {
        log.info("{} had an upload rejected: {}", authentication.getName(), exception.getMessageKey());
        return messages.getMessage(exception.getMessageKey(), null, LocaleContextHolder.getLocale());
    }
}
