package com.motivhub.be.workspace.service;

import com.motivhub.be.workspace.domain.Workspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceInviteMailService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public WorkspaceInviteMailService(JavaMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    public void sendInvite(String toEmail, Workspace workspace, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[MotivHub] " + workspace.getName() + " 워크스페이스에 초대되었습니다");
        message.setText(frontendUrl + "/invite/" + token + " 링크를 눌러 참여하세요.");
        mailSender.send(message);
    }
}
