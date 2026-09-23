package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // GNB 드롭다운에 보여줄 최근 알림 — 메서드 이름의 Top20으로 개수 제한(이 프로젝트의 다른 쿼리들과 같은 관례).
    List<Notification> findTop20ByRecipientOrderByCreatedAtDesc(Member recipient);

    long countByRecipientAndReadFalse(Member recipient);

    List<Notification> findByRecipientAndReadFalse(Member recipient);
}
