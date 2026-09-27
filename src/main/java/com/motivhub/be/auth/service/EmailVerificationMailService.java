package com.motivhub.be.auth.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailService {

    private final JavaMailSender mailSender;

    public EmailVerificationMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerification(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[MotivHub] 이메일 인증 코드");
        message.setText("인증코드: " + code + " (5분 이내에 입력해주세요.)");
        mailSender.send(message);
    }
}
