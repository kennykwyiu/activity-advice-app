package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * shop id
     */
    private Long shopId;

    private String title;

    private String subTitle;

    private String rules;

    /**
     * Amount paid
     */
    private Long payValue;

    /**
     * Amount of deduction
     */
    private Long actualValue;

    /**
     * Coupon type
     */
    private Integer type;

    /**
     * Coupon status
     */
    private Integer status;

    @TableField(exist = false)
    private Integer stock;

    /**
     * Effective date
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * Expiration date
     */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * Creation date
     */
    private LocalDateTime createTime;


    /**
     * Update time
     */
    private LocalDateTime updateTime;


}
