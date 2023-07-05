package streetalk.demo.v1.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;
import streetalk.demo.v1.domain.*;
import streetalk.demo.v1.dto.Post.*;
import streetalk.demo.v1.exception.ArithmeticException;
import streetalk.demo.v1.repository.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Service
@Transactional
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final UserService userService;
    private final PostLikeRepository postLikeRepository;
    private final PostImageService postImageService;
    private final String PREFIX = "거리지기 ";
    private final int POSTSIZE = 5;
    private final PostScrapRepository postScrapRepository;
    private final ReplyService replyService;
    private final LockPostRepository lockPostRepository;
    //post 저장
    @Transactional
    public void save(HttpServletRequest req, PostDto postDto){
        User user = userService.getCurrentUser(req);

        //글쓴지 5분 지났는지 체크
        //testing 하는동안 풀기


//        if(!checkUserRecentWriteTime(user))
//            throw new ArithmeticException(404, "글을 자주 올릴 수 없습니다.");
        Board board = boardRepository.findBoardByBoardName(postDto.getBoard())
                .orElseThrow(() -> new ArithmeticException(404,"can't match board"));
        try{
            Post post = postRepository.save(
                    Post.builder()
                            .title(postDto.getTitle())
                            .content(postDto.getContent())
                            .location(user.getLocation())
                            .industry(user.getIndustry())
                            .likeCount(0l)
                            .scrapCount(0l)
                            .replyCount(0l)
                            .board(board)
                            .writer(userService.getWriterByCheckName(postDto.getCheckName(), user, ""))
                            .blocked(false)
                            .reportCount(0l)
                            .user(user)
                            .build()
                );
            //TODO value에 아무파일을 안넣어도 multipartFiles에 값이 계속 들어옴.. 왜그러지..
            //postImage 저장 및 s3 upload
            System.out.println(postDto.getMultipartFiles().isEmpty());
            System.out.println(postDto.getMultipartFiles().size());
            System.out.println(postDto.getMultipartFiles().get(0).getOriginalFilename().isBlank());

            if(postDto.getMultipartFiles().size()==1 && postDto.getMultipartFiles().get(0).getOriginalFilename().isBlank()) { }
            else if(!postDto.getMultipartFiles().isEmpty()){
                try{
                    System.out.println("save images!!");
                    post.setImages(postImageService.setPostImages(user.getId(), post, postDto.getMultipartFiles()));
                }catch (Exception e){
                    throw new ArithmeticException(404, "can't find file");
                }
            }
        }catch (Error e){
            throw new ArithmeticException(404, "Error for postsave");
        }
        user.setRecentWriteTime(LocalDateTime.now());
        return;
    }
//User가 최근에 글을 작성한지 5분이 지났으면 true 전송
    @Transactional
    public Boolean checkUserRecentWriteTime(User user){
        if(user.getRecentWriteTime() == null)
            return true;
        LocalDateTime recentTime = user.getRecentWriteTime();
        LocalDateTime fiveMinutesBefore =  LocalDateTime.now().minusMinutes(5);
        System.out.println(recentTime);
        System.out.println(fiveMinutesBefore);
        return recentTime.isBefore(fiveMinutesBefore);
    }


    //주어진 postId 로부터 항상 0번째 페이지 가져옴
    @Transactional
    public List<PostListDto> getPostListByPage(Long boardId, Long postId){
        Board board = boardRepository.findBoardById(boardId)
                .orElseThrow(()->new ArithmeticException(404,"can't find board"));
        PageRequest pageRequest = PageRequest.of(0, POSTSIZE, Sort.by(Sort.Direction.DESC, "createdDate"));
        if(postId == null){
            postId = postRepository.findFirstByOrderByCreatedDateDesc().getId();
            ++postId;
        }
        System.out.println("postId = " + postId);
        Slice<Post> posts = postRepository.findByIdLessThanAndBoard(postId, board, pageRequest);
        return posts.getContent()
                .stream()
                .map(post -> new PostListDto(post))
                .sorted(Comparator.comparing(PostListDto::getPostId).reversed())
                .collect(Collectors.toList());
    }

    //post 하나 가져오기
    @Transactional
    public PostResponseDto findPostById(HttpServletRequest req,Long postId){
        User user = userService.getCurrentUser(req);
        Post post=postRepository.findById(postId)
                .orElseThrow(()->new ArithmeticException(404,"해당 게시물이 없습니다."));
        Optional<PostLike> postLike = postLikeRepository.findByPostAndUser(post, user);
        Optional<PostScarp> postScarp = postScrapRepository.findByPostAndUser(post,user);
        Boolean like = false;
        Boolean scrap = false;
        if(postLike.isPresent())
            like = true;
        if(postScarp.isPresent())
            scrap = true;
        try{
            return PostResponseDto.builder()
                    .boardName(post.getBoard().getBoardName())
                    .postWriterName(post.getWriter())
                    .postWriterId(post.getUser().getId())
                    .location(user.getLocation().getSmallLocation())
                    .industry(user.getIndustry().getName())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .likeCount(post.getLikeCount())
                    .scrapCount(post.getScrapCount())
                    .replyCount(post.getReplyCount())
                    .postLike(like)
                    .postScrap(scrap)
                    .lastTime(Duration.between(post.getCreatedDate(), LocalDateTime.now()).getSeconds())
                    .replyList(getRepliesByPost(post))
                    .images(postImageService.getPostImagesUrl(post))
                    .build();
        }catch(Error e){
            throw new ArithmeticException(404,"Error for return post");
        }
    }

    //post의 댓글 가져오기
    @Transactional
    public List<ReplyResponseDto> getRepliesByPost(Post post){
        List<Reply> replies = post.getReplies();
        return replies.stream()
                .filter(reply -> !reply.getBlocked())
                .map(reply -> replyService.getReplyToDto(reply))
                .collect(toList());
    }

    //post 업데이트
    @Transactional
    public void update(HttpServletRequest req, @ModelAttribute PostUpdateDto postUpdateDto) throws IOException {
        User user = userService.getCurrentUser(req);
        Post post = postRepository.findById(postUpdateDto.getPostId())
                .orElseThrow(()->new ArithmeticException(404,"Doesn't Exist post"));
        //post가 해당 유저의 것인지 확인
        if(post.getUser().equals(user)){
            post.update(postUpdateDto.getTitle(), postUpdateDto.getContent());
            post.setImages(postImageService.setPostImages(user.getId(), post, postUpdateDto.getMultipartFiles()));
        }else{
            throw new ArithmeticException(404, "해당 유저의 글이 아닙니다.");
        }
        return;
    }

    //post 좋아요
    @Transactional
    public Boolean postLike(HttpServletRequest req, Long id){
        User user=userService.getCurrentUser(req);
        Post post=postRepository.findById(id)
                .orElseThrow(()->new ArithmeticException(404,"Doesn't Exist post"));
        Optional<PostLike> postLike = postLikeRepository.findByPostAndUser(post,user);

        //만약 postLike 취소하는 경우
        if(postLike.isPresent()){
            postLike.ifPresent(thisPostLike -> {
                postLikeRepository.delete(thisPostLike);
                post.postLikeDown();
            });
            return false;
        }else{
            postLikeRepository.save(
                    PostLike.builder()
                            .post(post)
                            .user(user)
                            .build());
            post.postLikeUp();
            return true;
        }

    }

    //post scrap
    @Transactional
    public Boolean postScrap(HttpServletRequest req, Long id) {
        User user = userService.getCurrentUser(req);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ArithmeticException(404, "Doesn't Exist post"));

        Optional<PostScarp> scrapLikes = postScrapRepository.findByPostAndUser(post, user);

        //만약 postLike 취소하는 경우
        if(scrapLikes.isPresent()){
            scrapLikes.ifPresent(thisPostScarp ->{
                postScrapRepository.delete(thisPostScarp);
                post.postScrapDown();
            });
            return false;
        } else {
            postScrapRepository.save(
                    PostScarp.builder()
                            .post(post)
                            .user(user)
                            .build());
            post.postScrapUp();
            return true;
        }
    }

    //post 검색
    @Transactional
    public List<Post> searchPost(HttpServletRequest req, String key){
        User user = userService.getCurrentUser(req);
        List<Post>post = postRepository.findByTitleContaining(key);
        return post;
    }

    @Transactional
    public void deletePost(HttpServletRequest req, Long postId){
        User user = userService.getCurrentUser(req);
        Post post = postRepository.findById(postId)
                .orElseThrow(()
                        ->new ArithmeticException(404,"Doesn't Exist post"));

        if(!post.getUser().equals(user))
            throw new ArithmeticException(404, "you are not the writer");
        else{
            try{
                deleteLockPost(post);
                postRepository.delete(post);
            }catch (Error e){
                System.out.println(e);
                throw new ArithmeticException(404, "fail deleting post");
            }
        }
    }
    @Transactional
    public void deleteLockPost(Post post){
        lockPostRepository.deleteAll(
                lockPostRepository.findByPost(post));
    }

    @Transactional
    public Boolean lockPost(HttpServletRequest req, LockPostDto lockPostDto){
        User user=userService.getCurrentUser(req);
        Post post=postRepository.findById(lockPostDto.getPostId())
                .orElseThrow(()->new ArithmeticException(404,"Doesn't Exist Post"));
        Optional<LockPost> lockPost = lockPostRepository.findByPostAndUser(post,user);
        if (lockPost.isPresent()){
            return false;
        }
        else{
            lockPostRepository.save(
                    LockPost.builder()
                            .user(user)
                            .post(post)
                            .lockInfo(lockPostDto.getLockInfo())
                            .build());
            post.reportCountUp();
            if (post.getReportCount()>=5){
                post.setBlocked(Boolean.TRUE);
            }
            return true;
        }
    }
}