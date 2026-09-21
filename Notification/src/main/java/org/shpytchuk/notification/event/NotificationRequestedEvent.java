package org.shpytchuk.notification.event;


import io.notifyhub.core.Channel;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        String phone,
        String email,
        SocialMediaEnum[] socialMedias,
        String deduplicationKey) {


    public enum SocialMediaEnum {
        TELEGRAM ("TELEGRAM", Channel.TELEGRAM),
        VIBER ("VIBER", Channel.SMS),
        WHATSAPP ("WHATSAPP", Channel.WHATSAPP);

        private String name;
        private Channel channel;

        SocialMediaEnum(String name, Channel channel) {
            this.name = name;
            this.channel = channel;
        }

//        public Channel toChanel() {
//            return channel;
//        }
    }



    public String getRecipient(Channel channel) {
        return switch (channel) {
            case EMAIL -> email;
            default -> phone;
        };
    }
}
