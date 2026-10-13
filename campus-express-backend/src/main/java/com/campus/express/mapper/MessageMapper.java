package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.entity.Message;
import com.campus.express.vo.ConversationVO;
import com.campus.express.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 留言消息表 Mapper（t_message）
 * 以订单为会话维度：查询会话时间线、未读数、标记已读、会话列表
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 订单留言会话分页查询（按时间升序），关联发送者昵称
     * 前端轮询获取最新留言（3.6）
     */
    IPage<MessageVO> selectByOrderPage(IPage<MessageVO> page, @Param("orderId") Integer orderId);

    /**
     * 订单留言完整时间线（非分页，一次拉取）
     */
    java.util.List<MessageVO> selectAllByOrderId(@Param("orderId") Integer orderId);

    /**
     * 统计某接收者的未读留言数（个人订单中心角标展示）
     */
    int countUnread(@Param("receiverId") Integer receiverId);

    /**
     * 当前用户参与的所有订单会话列表（含对方昵称、未读数、最新消息）
     * 按最新消息时间倒序
     */
    List<ConversationVO> selectConversations(@Param("userId") Integer userId);
}
