package streetalk.demo.v1.dto.Post;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LockReplyDto {
    private Long replyId;
    private String lockInfo;
}
