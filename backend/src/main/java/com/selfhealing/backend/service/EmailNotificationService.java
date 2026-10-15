package com.selfhealing.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${alert.email.recipient:ritiklucky48@gmail.com}")
    private String alertRecipient;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String subject, String body) {

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(alertRecipient);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Alert email sent to {} — subject: {}", alertRecipient, subject);
        } catch (Exception e) {
            log.error("Failed to send alert email to {}: {}", alertRecipient, e.getMessage());
        }
    }
}