package com.selfhealing.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    @Autowired
    JavaMailSender mailSender;

    public void sendEmail(String subject, String body) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("ritiklucky48@gmail.com");
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}