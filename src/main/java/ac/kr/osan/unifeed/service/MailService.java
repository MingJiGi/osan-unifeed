package ac.kr.osan.unifeed.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// 이메일 발송 — 회원가입 인증 링크 / 비밀번호 재설정 링크 전송용
@Service
public class MailService {

    private static final String LOGO_CID = "unifeedLogo";

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        String html = buildEmailHtml(
                "UNIFEED 웹메일 인증",
                "이메일 인증을 완료해주세요",
                "아래 버튼을 눌러 대학교 웹메일 인증을 완료하시면<br>유니피드의 모든 기능을 이용하실 수 있습니다.",
                "이메일 인증하기",
                link,
                "본인이 가입을 요청하지 않았다면 이 이메일을 무시하셔도 됩니다.",
                "이 인증 링크는 10분 동안만 유효합니다."
        );
        send(to, "[유니피드] 이메일 인증을 완료해주세요", html);
    }

    // 비밀번호 재설정 — 이메일 소유 확인 없이 누구나 재설정할 수 있던 취약점을 막기 위해
    // 토큰이 담긴 링크를 본인 이메일로만 보내고, 그 링크를 눌러야만 재설정 폼에 도달한다.
    public void sendPasswordResetEmail(String to, String token) {
        String link = baseUrl + "/find-password/reset?token=" + token;
        String html = buildEmailHtml(
                "UNIFEED 비밀번호 재설정",
                "비밀번호를 재설정해주세요",
                "아래 버튼을 눌러 새 비밀번호를 설정하실 수 있어요.<br>본인이 요청하지 않았다면 안전하게 무시하셔도 됩니다.",
                "비밀번호 재설정하기",
                link,
                "본인이 요청하지 않았다면 이 이메일을 무시하셔도 됩니다. 비밀번호는 그대로 유지됩니다.",
                "이 링크는 10분 동안만 유효합니다."
        );
        send(to, "[유니피드] 비밀번호 재설정 안내", html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true: 멀티파트 모드 — 로고 이미지를 외부 URL이 아니라 메일 안에 직접 첨부(CID)하기 위해 필요.
            // (app.base-url이 배포 전엔 localhost라 외부 링크로 넣으면 수신자 메일함에서 깨진 이미지로 보임)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline(LOGO_CID, new ClassPathResource("static/images/UNIFEED_LOGO.png"));
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("메일 발송에 실패했습니다.", e);
        }
    }

    private String buildEmailHtml(String badge, String heading, String description,
                                   String buttonText, String link, String disclaimer, String expiryNote) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                </head>
                <body style="margin:0; padding:0; background:#FFF7ED; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#FFF7ED; padding:40px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px; background:#FFFFFF; border-radius:20px; overflow:hidden; box-shadow:0 4px 24px rgba(234,88,12,0.12);">
                          <tr>
                            <td style="height:6px; line-height:6px; font-size:0; background-color:#F97316 !important;">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:36px 40px 20px; text-align:center;">
                              <img src="cid:%s" alt="유니피드" width="88" height="88" style="display:inline-block; width:88px; height:88px; object-fit:contain;">
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 40px 6px; text-align:center;">
                              <span style="display:inline-block; background-color:#FFF7ED !important; color:#EA580C !important; font-size:11px; font-weight:800; letter-spacing:0.3px; padding:5px 14px; border-radius:9999px;">%s</span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:14px 40px 30px; text-align:center;">
                              <h1 style="margin:0 0 12px; font-size:24px; font-weight:800; color:#1E293B;">%s</h1>
                              <p style="margin:0; font-size:14px; color:#64748B; line-height:1.6;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 40px 32px; text-align:center;">
                              <a href="%s" style="display:inline-block; background-color:#F97316 !important; color:#FFFFFF !important; font-size:15px; font-weight:700; text-decoration:none; padding:14px 36px; border-radius:9999px;">%s</a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 40px; border-top:1px solid #FFEDD5; text-align:center;">
                              <p style="margin:0 0 6px; font-size:12.5px; color:#94A3B8; line-height:1.6;">%s</p>
                              <p style="margin:0; font-size:11px; color:#CBD5E1;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 40px 28px; text-align:center; background-color:#F97316 !important;">
                              <div style="font-size:17px; font-weight:900; color:#FFFFFF !important; letter-spacing:-0.3px;">UNIFEED</div>
                              <p style="margin:6px 0 0; font-size:11px; color:#FFEDD5 !important;">전국 대학교 캠퍼스 미식 &amp; 커뮤니티 플랫폼</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(LOGO_CID, badge, heading, description, link, buttonText, disclaimer, expiryNote);
    }
}
