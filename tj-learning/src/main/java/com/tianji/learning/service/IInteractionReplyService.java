package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author chake
 * @since 2025-07-21
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void addReply(ReplyDTO dto);

    PageDTO<ReplyVO> getReplies(ReplyPageQuery query);

    PageDTO<ReplyVO> getRepliesAdmin(ReplyPageQuery query);

    void updateHidden(Long id, Boolean hidden);

    ReplyVO getReply(Long id);
}
