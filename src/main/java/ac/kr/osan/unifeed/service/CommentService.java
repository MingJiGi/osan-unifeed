package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.NotificationType;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostService postService;
    private final NotificationService notificationService;

    public CommentService(CommentRepository commentRepository, PostService postService,
                          NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.postService = postService;
        this.notificationService = notificationService;
    }

    // 댓글 목록 조회 (원댓글 안에 대댓글 replies 를 계층 구조로 정리하여 반환)
    public List<Comment> findByPost(Post post) {
        List<Comment> allComments = commentRepository.findByPostWithAuthor(post);
        java.util.Map<Long, Comment> map = new java.util.HashMap<>();
        List<Comment> rootComments = new java.util.ArrayList<>();

        for (Comment c : allComments) {
            map.put(c.getId(), c);
            c.getReplies().clear();
        }

        for (Comment c : allComments) {
            if (c.getParent() != null) {
                Comment parent = map.get(c.getParent().getId());
                if (parent != null) {
                    parent.getReplies().add(c);
                }
            } else {
                rootComments.add(c);
            }
        }

        return rootComments;
    }

    // 특정 회원이 작성한 댓글 목록 조회 (게시글 정보 포함)
    public List<Comment> findByAuthor(Member author) {
        if (author == null) return List.of();
        return commentRepository.findByAuthorWithPost(author);
    }

    // 댓글 등록 (+ 카운트 1 증가)
    @Transactional
    public void create(Long postId, Member author, String content) {
        create(postId, author, content, null);
    }

    // 대댓글 포함 댓글 등록 (+ 카운트 1 증가)
    @Transactional
    public void create(Long postId, Member author, String content, Long parentId) {
        if (content == null || content.isBlank()) {
            return;
        }
        Post post = postService.findById(postId);
        Comment parent = null;
        if (parentId != null) {
            parent = commentRepository.findById(parentId).orElse(null);
        }
        Comment comment = new Comment(content, post, author, parent);
        commentRepository.save(comment);
        post.increaseCommentCount();

        // 알림 — 답글이면 원댓글 작성자에게, 최상위 댓글이면 게시글 작성자에게(본인 행동은 notify()가 알아서 무시).
        // 익명 커뮤니티라 메시지에 "누구"인지는 절대 안 적는다.
        if (parent != null) {
            notificationService.notify(parent.getAuthor(), author, NotificationType.REPLY,
                    "내 댓글에 새 답글이 달렸습니다.", "/posts/" + postId);
        } else {
            notificationService.notify(post.getAuthor(), author, NotificationType.COMMENT,
                    "내 게시글에 새 댓글이 달렸습니다.", "/posts/" + postId);
        }
    }

    // 댓글 삭제 (본인 확인 + 카운트 1 감소) — 관리자는 신고 없이도 직접 삭제 가능
    @Transactional
    public void delete(Long commentId, Member loginMember) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다."));
        if (!comment.getAuthor().getId().equals(loginMember.getId()) && !loginMember.isAdmin()) {
            return;
        }
        Post post = comment.getPost();
        commentRepository.delete(comment);
        post.decreaseCommentCount();
    }

    // 관리자용 — 작성자 본인 확인 없이 강제 삭제(신고 처리).
    @Transactional
    public void adminDelete(Long commentId) {
        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) return;
        Post post = comment.getPost();
        commentRepository.delete(comment);
        post.decreaseCommentCount();
    }

    // 신고 누적 자동 숨김/해제 — 댓글 수(post.commentCount)는 그대로 둔다. 삭제가 아니라
    // "내용만 가려짐"이라 실제로 댓글이 사라지는 게 아니기 때문(화면에서 placeholder로 대체).
    @Transactional
    public void hide(Long commentId) {
        commentRepository.findById(commentId).ifPresent(Comment::hide);
    }

    @Transactional
    public void unhide(Long commentId) {
        commentRepository.findById(commentId).ifPresent(Comment::unhide);
    }

    // 댓글 수정 (본인 확인 + 변경 감지)
    @Transactional
    public void update(Long commentId, Member loginMember, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            return;
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다."));
        if (!comment.getAuthor().getId().equals(loginMember.getId())) {
            return;
        }
        comment.edit(newContent);
    }
}