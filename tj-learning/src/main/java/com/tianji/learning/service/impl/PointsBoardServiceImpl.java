package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author chake
 * @since 2025-07-24
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    @Override
    public PointsBoardVO listPointsBoard(PointsBoardQuery query) {
        // 1. 获取当前登录用户的id
        Long userId = UserContext.getUser();
        // 2. 判断是查询当前赛季还是历史赛季 query.season 赛季id， 未null或者0为当前赛季，否则为历史赛季
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        Long season = query.getSeason();
        // 3. 查询我的排名和积分 query.season 判断是否查询redis还是db
        PointsBoard board = isCurrent ? getMyCurrentBoard(key, userId) : getMyHistoryBoard(season, userId);
        // 4. 分页查询赛季列表 query.season 判断是否查询redis还是db
        List<PointsBoard> boardList = isCurrent ? listCurrentBoard(key, query.getPageNo(), query.getPageSize()) : listHistoryBoard(query);
        // 5. 封装用户id集合 远程调用查询用户信息 转map
        Set<Long> uIds = boardList.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException(ErrorInfo.Msg.USER_NOT_EXISTS);
        }
        Map<Long, String> userInfoMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        // 5. 封装vo返回
        PointsBoardVO vo = new PointsBoardVO();
        vo.setRank(board.getRank());
        vo.setPoints(board.getPoints());

        List<PointsBoardItemVO> voList = new ArrayList<>();
        for (PointsBoard boardItem : boardList) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setName(userInfoMap.get(boardItem.getUserId()));
            itemVO.setPoints(boardItem.getPoints());
            itemVO.setRank(boardItem.getRank());
            voList.add(itemVO);
        }
        vo.setBoardList(voList);
        return vo;
    }

    @Override
    public List<PointsBoard> listCurrentBoard(String key,
                                              @Min(value = 1, message = "页码不能小于1") Integer pageNo,
                                              @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        // 1.计算起始下标和结束下标
        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize - 1;
        // 2.利用RevRange命令获取榜单列表 按分数倒序 分页查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (CollUtils.isEmpty(typedTuples)) {
            return CollUtils.emptyList();
        }
        // 3.封装vo
        int rank = start + 1;
        List<PointsBoard> boardList = CollUtils.newArrayList();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String userId = typedTuple.getValue();
            Double score = typedTuple.getScore();
            if (StringUtils.isBlank(userId) || score == null) {
                continue;
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(userId));
            board.setPoints(score.intValue());
            board.setRank(rank++);
            boardList.add(board);
        }
        return boardList;
    }

    private List<PointsBoard> listHistoryBoard(PointsBoardQuery query) {
        return null;
    }

    private PointsBoard getMyHistoryBoard(Long season, Long userId) {

        return null;
    }

    private PointsBoard getMyCurrentBoard(String key, Long userId) {
        // 获取分值
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        // 获取排名 从零开始
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        // 封装vo
        PointsBoard board = new PointsBoard();
        board.setPoints(score == null ? 0 : score.intValue());
        board.setRank(rank == null ? 0 : rank.intValue() + 1);
        return board;
    }
}
