package com.huangzizhu.pojo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录参数
 * @Author huangzizhu
 * @Version 1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class LoginParam extends PasswordAndSalt {
    private String uuid;
    private String captcha;
    private String email;
    private String emailCode;
    private Integer id;
    private String username;
    private String ip;
}
