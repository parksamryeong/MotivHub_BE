package com.motivhub.be.auth.service;

import com.motivhub.be.global.config.FrontendUrls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public EmailVerificationMailService(JavaMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    public void sendVerification(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[MotivHub] 이메일 인증을 완료해주세요");
        message.setText(FrontendUrls.primary(frontendUrl) + "/signup/verify/" + token + " 링크를 눌러 회원가입을 완료하세요.");
        mailSender.send(message);
    }
}
