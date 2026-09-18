package ac.kr.osan.unifeed.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// 이메일 발송 — 회원가입 시 대학교 웹메일 인증 링크 전송용
@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[유니피드] 이메일 인증을 완료해주세요");
        message.setText(
                "유니피드 회원가입을 환영합니다!\n\n" +
                "아래 링크를 클릭하면 이메일 인증이 완료됩니다 (24시간 이내):\n" +
                link + "\n\n" +
                "본인이 가입한 적이 없다면 이 메일을 무시하셔도 됩니다."
        );
        mailSender.send(message);
    }
}
