package com.hmdp.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * tb_sign
 */
@TableName("tb_sign")
@Data
public class Sign implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 签到的年
     */
    private Object year;

    /**
     * 签到的月
     */
    private Byte month;

    /**
     * 签到的日期
     */
    private Date date;

    /**
     * 是否补签
     */
    private Byte isBackup;

    private static final long serialVersionUID = 1L;
}