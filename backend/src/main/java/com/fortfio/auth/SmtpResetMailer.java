package com.fortfio.auth;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.fortfio.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends through SMTP once MAIL_HOST is set; until then the link only appears in the server log,
 * like the phone codes.
 */
@Slf4j
@Component
public class SmtpResetMailer implements ResetMailer {

	private final AppProperties properties;
	private final JavaMailSenderImpl sender;

	public SmtpResetMailer(AppProperties properties) {
		this.properties = properties;
		AppProperties.Mail mail = properties.mail();
		if (mail.configured()) {
			sender = new JavaMailSenderImpl();
			sender.setHost(mail.host());
			sender.setPort(mail.port());
			sender.setUsername(mail.username());
			sender.setPassword(mail.password());
			sender.setDefaultEncoding("UTF-8");
			sender.getJavaMailProperties().put("mail.smtp.auth", "true");
			sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
		} else {
			sender = null;
		}
	}

	@Override
	public void send(String email, String link) {
		if (sender == null) {
			log.info("Password reset link for {}: {}", email, link);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		String from = properties.mail().from();
		message.setFrom(from == null || from.isBlank() ? properties.mail().username() : from);
		message.setTo(email);
		message.setSubject("Đặt lại mật khẩu Chụp Xịn");
		message.setText("""
				Xin chào,

				Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu cho tài khoản Chụp Xịn của email này.
				Mở đường dẫn dưới đây trong %d phút để đặt mật khẩu mới:

				%s

				Nếu bạn không yêu cầu, hãy bỏ qua email này, mật khẩu cũ vẫn dùng được.
				""".formatted(properties.auth().passwordReset().ttl().toMinutes(), link));
		try {
			sender.send(message);
		} catch (MailException ex) {
			log.error("Could not send the password reset email to {}", email, ex);
		}
	}
}
