package com.service;

import com.security.AppConfig;
import com.service.mail.MailSender;
import jakarta.mail.MessagingException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gửi yêu cầu liên hệ của khách hàng bằng hạ tầng SMTP dùng chung.
 * Địa chỉ nhận được lấy từ secure config, không lưu trong source code.
 */
public class ContactMailService {

    private static final String SUBJECT_PREFIX = "[SIMS Contact] ";

    private final MailSender mailSender;
    private final String receiverAddress;

    public ContactMailService() {
        AppConfig config = AppConfig.getInstance();
        this.mailSender = new MailSender();

        String configuredReceiver = config.get("CONTACT_RECEIVER_ADDRESS", "").trim();
        this.receiverAddress = configuredReceiver.isEmpty()
                ? config.get("MAIL_SENDER_ADDRESS").trim()
                : configuredReceiver;

        if (receiverAddress.isEmpty()) {
            throw new IllegalStateException("Contact receiver address is not configured.");
        }
    }

    public void sendContactRequest(ContactRequestData data) throws MessagingException {
        String safeCategory = singleLine(data.category());
        String safeSubject = singleLine(data.subject());
        String subject = SUBJECT_PREFIX + safeCategory + " - " + safeSubject;

        StringBuilder body = new StringBuilder();
        body.append("Sent at: ")
                .append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append('\n');
        body.append("User ID: ")
                .append(data.userId() == null ? "-" : data.userId())
                .append('\n');
        body.append("Username: ").append(valueOrDash(singleLine(data.username()))).append('\n');
        body.append("Full name: ").append(singleLine(data.fullName())).append('\n');
        body.append("Reply email: ").append(singleLine(data.replyEmail())).append('\n');
        body.append("Phone: ").append(valueOrDash(singleLine(data.phone()))).append('\n');
        body.append("Category: ").append(safeCategory).append('\n');
        body.append("Subject: ").append(safeSubject).append("\n\n");
        body.append("Message:\n").append(data.message().trim());

        mailSender.send(receiverAddress, subject, body.toString());
    }

    private String singleLine(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String valueOrDash(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    public record ContactRequestData(
            Integer userId,
            String username,
            String fullName,
            String replyEmail,
            String phone,
            String category,
            String subject,
            String message) {
    }
}