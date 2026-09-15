package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
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

    public CommentService(CommentRepository commentRepository, PostService postService) {
        this.commentRepository = commentRepository;
        this.postService = postService;
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
    }

    // 댓글 삭제 (본인 확인 + 카운트 1 감소)
    @Transactional
    public void delete(Long commentId, Member loginMember) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다."));
        if (!comment.getAuthor().getId().equals(loginMember.getId())) {
            return;
        }
        Post post = comment.getPost();
        commentRepository.delete(comment);
        post.decreaseCommentCount();
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