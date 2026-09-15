package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long>   {

    boolean existsByPostAndMember(Post post, Member member);

    // 좋아요 취소할 때 해당 기록 찾기
    Optional<PostLike> findByPostAndMember(Post post, Member member);

    // 특정 회원이 좋아요를 누른 게시글 목록 (게시글 및 게시글 작성자 페치 조인, 최신 좋아요 순)
    @Query("select pl.post from PostLike pl join pl.post p join fetch p.author where pl.member = :member order by pl.createdAt desc")
    List<Post> findLikedPostsByMember(@Param("member") Member member);
}
