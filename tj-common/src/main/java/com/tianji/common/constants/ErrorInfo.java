package com.tianji.common.constants;

public interface ErrorInfo {

    interface Msg {
        String OK = "OK";
        String INVALID_VERIFY_CODE = "验证码错误";

        String USER_NOT_LOGIN = "必须得登录";


        String SERVER_INTER_ERROR = "服务器内部错误";

        String DB_SAVE_EXCEPTION = "数据新增失败";
        String DB_DELETE_EXCEPTION = "数据删除失败";
        String DB_BATCH_DELETE_EXCEPTION = "数据批量删除失败";
        String DB_UPDATE_EXCEPTION = "数据更新失败";
        String DB_SORT_FIELD_NOT_FOUND = "排序字段不存在";
        String OPERATE_FAILED = "操作失败";

        String REQUEST_PARAM_ILLEGAL = "请求参数不合法";
        String REQUEST_OPERATE_FREQUENTLY = "操作频繁,请稍后重试";
        String REQUEST_TIME_OUT = "请求超时";

        String USER_NOT_EXISTS = "用户信息不存在";
        String INVALID_USER_TYPE = "无效的用户类型";
        String COURSE_NOT_EXIST = "课程不存在";
        String SECTION_NOT_EXIST = "小节不存在";
        String DELETE_LESSON_FAILED = "删除课程失败";
        String COURSE_NOT_JOINED_TABLE = "该课程未加入课表";
        String SAVE_RECORD_FAILED = "新增记录失败";
        String UPDATE_RECORD_FAILED = "更新记录失败";
        String UPDATE_INTERACT_FAILED = "不能修改别人的互动问题";
        String QUESTION_NOT_EXIST = "互动问题不存在";
        String CHAPTER_NOT_EXIST = "章节不存在";
        String REPLY_NOT_EXIST = "回复不存在";
        String USER_NOT_EXIST = "用户不存在";
        String SIGN_RECORD_ALREADY_EXISTS = "已经签到过了";
        String COUPON_SCOPE_NOT_EMPTY = "分类id不能为空";
        String COUPON_NOT_EXIST = "优惠券不存在";
        String COUPON_STATUS_NOT_ALLOW_ISSUE = "只有待发放和暂停中的优惠券才能发放";
        String COUPON_NOT_ISSUING = "该优惠券已过期或未发放";
        String COUPON_NOT_ENOUGH = "该优惠券库存不足";
        String COUPON_USER_LIMIT = "该优惠券已达领取上限";
        String EXCHANGE_CODE_USED = "该优惠券兑换码已被使用";
        String EXCHANGE_CODE_NOT_EXIST = "该优惠券兑换码不存在";
        String EXCHANGE_CODE_EXPIRED = "该优惠券兑换码已过期";
        String OPERATE_TOO_FREQUENTLY = "请求太频繁";
    }

    interface Code {
        int SUCCESS = 200;
        int FAILED = 0;
    }
}
