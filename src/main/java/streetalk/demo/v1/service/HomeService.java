package streetalk.demo.v1.service;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import streetalk.demo.v1.domain.*;
import streetalk.demo.v1.dto.Home.HomeDto;
import streetalk.demo.v1.dto.Home.HomePostListDto;
import streetalk.demo.v1.dto.Home.LikeBoard;
import streetalk.demo.v1.dto.Post.PostListDto;
import streetalk.demo.v1.exception.ArithmeticException;
import streetalk.demo.v1.repository.BoardRepository;
import streetalk.demo.v1.repository.NoticeImgUrlRepository;
import streetalk.demo.v1.repository.NoticeRepository;
import streetalk.demo.v1.repository.PostRepository;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@AllArgsConstructor
public class HomeService {
    private final UserService userService;
    private final S3Service s3Service;
    private final NoticeImgUrlRepository noticeImgUrlRepository;
    private final NoticeRepository noticeRepository;
    private final PostRepository postRepository;

    @Transactional
    public HomeDto getHome(HttpServletRequest req) {
        User user = userService.getCurrentUser(req);
        String notice = noticeRepository.findFirstByOrderByCreatedDateAsc().getTitle();
        List<HomePostListDto> myLocalPosts = getLocalPosts(user.getLocation());
        List<HomePostListDto> myIndustryPosts = getIndustryPosts(user.getIndustry());
        List<HomePostListDto> newPosts = getNewPosts();
        List<LikeBoard> likeBoardList = user.getBoardLikes().stream()
                .map(boardLike ->  new LikeBoard(boardLike.getBoard().getBoardName(), boardLike.getBoard().getId()) )
                .collect(Collectors.toList());


        return HomeDto.builder()
                .userName(user.getName())
                .location(user.getLocation().getSmallLocation())
                .industry(user.getIndustry().getName())
                .mainNoticeImgUrl(getNoticeUrl())
                .notice(notice)
                .myLocalPosts(myLocalPosts)
                .myIndustryPosts(myIndustryPosts)
                .newPosts(newPosts)
                .likeBoardList(likeBoardList)
                .build();
    }

    @Transactional
    public List<String> getNoticeUrl(){
        return noticeImgUrlRepository.findAll().stream()
                .map(url -> s3Service.getPreSignedDownloadUrl(url.getFileName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<HomePostListDto> getLocalPosts(Location location){
        List<Post> posts = postRepository.findByCreatedDateAfterAndLocation(LocalDateTime.now().minusDays(7), location)
                .stream()
                .sorted(Comparator.comparing(Post::getReplyCount).reversed())
                .limit(5)
                .collect(Collectors.toList());
        return posts.stream()
                .map(post -> new HomePostListDto(post))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<HomePostListDto> getIndustryPosts(Industry industry){
        List<Post> posts = postRepository.findByCreatedDateAfterAndIndustry(LocalDateTime.now().minusDays(7), industry)
                .stream()
                .sorted(Comparator.comparing(Post::getReplyCount).reversed())
                .limit(5)
                .collect(Collectors.toList());
        return posts.stream()
                .map(post -> new HomePostListDto(post))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<HomePostListDto> getNewPosts(){
        List<Post> posts = postRepository.findTop5ByOrderByCreatedDateDesc();
        return posts.stream()
                .map(post -> new HomePostListDto(post))
                .collect(Collectors.toList());
    }


}