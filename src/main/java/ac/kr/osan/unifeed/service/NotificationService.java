package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Notification;
import ac.kr.osan.unifeed.entity.NotificationType;
import ac.kr.osan.unifeed.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // 본인 행동에 대한 알림은 보내지 않는다(자기 글에 자기가 댓글 달았는데 알림 오면 이상하니까) —
    // 호출부에서 actor != recipient 확인 후 부르는 게 아니라 여기서 한 번 더 방어한다.
    @Transactional
    public void notify(Member recipient, Member actor, NotificationType type, String message, String link) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        notificationRepository.save(new Notification(recipient, type, message, link));
    }

    public List<Notification> findRecent(Member member) {
        return notificationRepository.findTop20ByRecipientOrderByCreatedAtDesc(member);
    }

    public long countUnread(Member member) {
        return notificationRepository.countByRecipientAndReadFalse(member);
    }

    @Transactional
    public void markAllRead(Member member) {
        List<Notification> unread = notificationRepository.findByRecipientAndReadFalse(member);
        unread.forEach(Notification::markRead);
    }

    // 본인 알림만 읽음 처리 — 남의 알림 id를 넣어도 조용히 무시(소유 확인).
    @Transactional
    public void markRead(Long notificationId, Member member) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(member.getId()))
                .ifPresent(Notification::markRead);
    }
}
