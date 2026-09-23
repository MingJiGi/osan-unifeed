package ac.kr.osan.unifeed.exception;

// 정지된 계정으로 로그인을 시도했을 때 던진다. 이메일 미인증(IllegalStateException)과
// 구분해서 처리해야 해서(재전송 버튼을 보여주면 안 됨) 별도 타입으로 뺐다.
public class MemberSuspendedException extends RuntimeException {
    public MemberSuspendedException(String message) {
        super(message);
    }
}
