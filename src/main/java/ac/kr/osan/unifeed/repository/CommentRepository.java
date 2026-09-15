package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글의 댓글 목록 (작성자 및 부모댓글 함께 조회, 작성일 순)
    @Query("select c from Comment c join fetch c.author left join fetch c.parent where c.post = :post order by c.createdAt asc")
    List<Comment> findByPostWithAuthor(@Param("post") Post post);

    // 특정 회원이 작성한 댓글 목록 (게시글 및 게시글 작성자 함께 페치 조인, 최신순)
    @Query("select c from Comment c join fetch c.post p join fetch p.author where c.author = :author order by c.createdAt desc")
    List<Comment> findByAuthorWithPost(@Param("author") Member author);
}