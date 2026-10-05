package com.motivhub.be.auth.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    public PasswordResetMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResetCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[MotivHub] 비밀번호 재설정 코드");
        message.setText("재설정 코드: " + code + " (5분 이내에 입력해주세요.)");
        mailSender.send(message);
    }

    public void sendSocialAccountNotice(String toEmail, String providerDisplayName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[MotivHub] 비밀번호 재설정 안내");
        message.setText("이 이메일(" + toEmail + ")은 " + providerDisplayName
                + " 로그인으로 가입되어 있어 별도의 비밀번호가 없습니다. " + providerDisplayName + "로 로그인해주세요.");
        mailSender.send(message);
    }
}
