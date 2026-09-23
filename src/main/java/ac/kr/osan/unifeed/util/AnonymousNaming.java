package ac.kr.osan.unifeed.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ############################################################################
// AnonymousNaming — 게시글/리뷰 등 "한 스레드(게시글 하나, 맛집 리뷰 목록 하나)" 안에서만
// 통하는 "익명1", "익명2" 라벨을 만들어준다.
// ----------------------------------------------------------------------------
// 같은 스레드 안에서 같은 회원이면 항상 같은 번호(글쓴이가 자기 글에 또 댓글 달면 같은 번호로
// 보임), 스레드가 다르면 완전히 새로 번호가 매겨진다(다른 글에서는 같은 사람도 다른 번호).
// DB에 저장하지 않는 순수 표시용 계산 — 진짜 익명성을 위해 영구적인 별명 자체를 안 만든다.
// ############################################################################
public final class AnonymousNaming {

    private AnonymousNaming() {
    }

    // authorIdsInOrder: 그 스레드 안에서 등장하는 순서대로(글쓴이 먼저, 그다음 댓글/리뷰 순) 넣는다.
    public static Map<Long, String> assignLabels(List<Long> authorIdsInOrder) {
        Map<Long, String> labels = new LinkedHashMap<>();
        for (Long id : authorIdsInOrder) {
            labels.putIfAbsent(id, "익명" + (labels.size() + 1));
        }
        return labels;
    }
}
