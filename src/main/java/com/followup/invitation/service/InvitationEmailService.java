package com.followup.invitation.service;

import com.followup.invitation.entity.ProjectInvitation;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 초대 메일을 발송한다. 원본 토큰은 이 메서드 호출 한 번에만 전달되고 어디에도 저장되지 않는다 —
 * createInvitation/resendInvitation이 토큰을 생성한 바로 그 자리에서 호출해야 한다.
 * SMTP 오류 등 발송 실패는 로그만 남기고 삼킨다 — 초대 row 자체의 커밋을 막으면 안 되기 때문이다
 * (재발송 기능으로 복구 가능).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationEmailService {

    private static final String TEMPLATE_HTML_PATH = "email/invite-email.html";
    private static final String TEMPLATE_TEXT_PATH = "email/invite-email.txt";
    private static final long EXPIRES_DAYS = 7;

    private final JavaMailSender mailSender;

    @Value("${app.origin}")
    private String appOrigin;

    public void sendInvitationEmail(ProjectInvitation invitation, String inviterName, String rawToken) {
        String acceptUrl = appOrigin + "/invite/" + rawToken;
        String projectName = invitation.getProject().getName();
        String inviteeEmail = invitation.getEmail();
        String subject = "[FollowUp] " + inviterName + " 님이 \"" + projectName + "\" 프로젝트에 초대했습니다";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(inviteeEmail);
            helper.setSubject(subject);
            helper.setText(
                    render(TEMPLATE_TEXT_PATH, projectName, inviterName, inviteeEmail, acceptUrl),
                    render(TEMPLATE_HTML_PATH, projectName, inviterName, inviteeEmail, acceptUrl));
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}", inviteeEmail, e);
        }
    }

    private String render(String templatePath, String projectName, String inviterName, String inviteeEmail,
                           String acceptUrl) {
        return readTemplate(templatePath)
                .replace("{{projectName}}", projectName)
                .replace("{{inviterName}}", inviterName)
                .replace("{{inviteeEmail}}", inviteeEmail)
                .replace("{{acceptUrl}}", acceptUrl)
                .replace("{{expiresDays}}", String.valueOf(EXPIRES_DAYS));
    }

    private String readTemplate(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read email template: " + path, e);
        }
    }
}
