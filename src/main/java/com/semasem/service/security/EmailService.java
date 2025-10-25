package com.semasem.service.security;

import com.semasem.dto.exception.CustomException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.semasem.dto.exception.ErrorCode.INTERNAL_ERROR;

@Service
public class EmailService {
    private final JavaMailSender javaMailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Async
    public void sendEmail(String template, String toEmail) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Подтверждение email - AniMeTrack");

            helper.setText(template, true);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            throw new CustomException(INTERNAL_ERROR, "Ошибка отправки Email" , e);
        }
    }

    public String processTemplate(String templatesName, Map<String, String> variables) {
        try {
            Resource resource = new ClassPathResource("templates/emails/" + templatesName);
            String template;
            try (InputStream inputStream = resource.getInputStream()) {
                template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace(String.format("{{ %s }}", entry.getKey()), entry.getValue());
            }

            return template;

        } catch (IOException e) {
            throw new CustomException(INTERNAL_ERROR, "Ошибка загрузки шаблона", e);
        }
    }

    public String processTemplate(String templateName, String code) {
        return processTemplate(templateName, Map.of("code", code));
    }

}
