package streetalk.demo.v1.dto.Home;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import streetalk.demo.v1.dto.Post.BannerResponseDto;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
public class HomeDto {
    String userName;
    String location;
    String industry;
    List<BannerResponseDto> bannerList;
    String notice;
    List<HomePostListDto> myLocalPosts;
    List<HomePostListDto> myIndustryPosts;
    List<HomePostListDto> newPosts;
    List<LikeBoard> likeBoardList;
}
