package com.huangzizhu.service.impl;

import com.huangzizhu.exception.*;
import com.huangzizhu.mapper.CollectionMapper;
import com.huangzizhu.mapper.UserMapper;
import com.huangzizhu.pojo.*;
import com.huangzizhu.pojo.collection.Collection;
import com.huangzizhu.pojo.user.*;
import com.huangzizhu.service.EmailCaptchaManager;
import com.huangzizhu.service.ImageCaptchaManager;
import com.huangzizhu.service.UserService;
import com.huangzizhu.utils.CommonUtils;
import com.huangzizhu.utils.CurrentHolder;
import com.huangzizhu.utils.HashWithSalt;
import com.huangzizhu.utils.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;


/**
 * 用户服务实现类
 * @Author huangzizhu
 * @Version 1.0
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CollectionMapper collectionMapper;


    @Override
    public QueryResult<User> getAllUser(UserQueryParam param) {
        Integer total = userMapper.countAllUser();
        Integer start = (param.getPage() - 1) * param.getPageSize();
        List<User> list = userMapper.getAllUser(start, param.getPageSize());
        return new QueryResult<>(total, list);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void regUser(UserRegParam param) {
        //验证图形验证码
        if(!ImageCaptchaManager.getInstance().verifyCode(param.getUuid(), param.getImageCode())) throw new CaptchaException("图形验证码错误");
        //验证邮箱验证码
        if(!EmailCaptchaManager.getInstance().verifyCode(param.getEmail(), param.getEmailCode())) throw new CaptchaException("邮箱验证码错误");
        //对密码进行哈希加盐，记录哈希后的密码和盐值
        hashPassword(param);
        //将用户信息插入数据库
        try {
            userMapper.insertUser(param);
        }catch (DuplicateKeyException e){
            throw new DuplicateValueException(e.getMessage(),e);
        }
        try {
            //为用户创建收藏
            Collection collection = new Collection(null, param.getId(), LocalDateTime.now(), null, null, 0,0);
            collectionMapper.addCollection(collection);
            //将用户行为信息插入数据库
            userMapper.addUserBehavior(new UserBehavior(null,param.getId(),collection.getId(),0,0,null));
        }catch (Exception e){
            throw new UserRegFailException("注册失败",e);
        }
    }


    @Override
    public LoginResult<User> login(LoginParam param) {
        //验证图形验证码
        if(!ImageCaptchaManager.getInstance().verifyCode(param.getUuid(), param.getCaptcha())) throw new CaptchaException("图形验证码错误");
        //判断用户是否存在
        User user = userMapper.getUser(param.getUsername());
        if(user == null){
            throw new UserNotFoundException("用户不存在");
        }
        //获取盐值
        String salt = user.getSalt();
        //salt转换为字节数组
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        //进行哈西后比较
        byte[] passwordToCheck = HashWithSalt.hashPassword(param.getHashedPassword(), saltBytes);
        if(java.util.Arrays.equals(passwordToCheck,Base64.getDecoder().decode(user.getHashedPassword()) )){
            //登录成功,设置上次登陆时间,清除敏感信息，返回token
            userMapper.setLastLoginTime(user.getId(), LocalDateTime.now());
            user.setSalt(null);
            user.setHashedPassword(null);
            String token = JWTUtil.generateToken(user.getId(), user.getUsername(),false);
            return new LoginResult<>(user, token);
        }
        //登陆失败，返回错误信息
        throw new PasswordErrorException("密码错误");
    }

    @Override
    public User getUserById(Integer id) {
        User user = checkUser(id);
        //获取用户行为信息
        UserBehavior userBehavior = userMapper.getUserBehavior(user.getId());
        user.setUserBehavior(userBehavior);
        return user;
    }

    @Override
    public List<SimpleUserInfo> fuzzySearchUser(String username) {
        return userMapper.fuzzySearchUser(username);
    }

    @Override
    public void updateUserInfo(UpdateUserInfoParam param) {
        if(!CommonUtils.isValidEmail(param.getEmail())) throw new EmailException("邮箱格式错误");
        try {
            Integer affectRows = userMapper.updateUser(param);
            if (affectRows == 0) throw new UserNotFoundException("用户不存在");
        }catch (DuplicateKeyException e) {throw new DuplicateValueException(e.getMessage(),e);}
    }

    @Override
    public void deleteUser(Integer id) {
        Integer affectRows = userMapper.deleteUser(id);
        if (affectRows == 0) throw new UserNotFoundException("用户不存在");
    }

    @Override
    public void updatePassword(LoginParam param) {
        User user = checkUser(param);
        //检查是否是本人邮箱
        if(!user.getEmail().equals(param.getEmail())) throw new EmailException("邮箱不匹配");
        //检查邮箱验证码
        if(!EmailCaptchaManager.getInstance().verifyCode(param.getEmail(), param.getEmailCode())) throw new CaptchaException("邮箱验证码错误");
        hashPassword(param);
        Integer affectRows = userMapper.updatePassword(param);
        if (affectRows == 0) throw new UserNotFoundException("用户不存在");
    }

    @Override
    public User getUserStatus() {
        //通过token获取用户id
        Integer id = CurrentHolder.getCurrentId();
        User user = checkUser(id);
        //获取用户行为信息
        UserBehavior userBehavior = userMapper.getUserBehavior(user.getId());
        user.setUserBehavior(userBehavior);
        return user;
    }

    private User checkUser(Integer id) {
        if(id == null) throw new UserNotFoundException("用户不存在");
        User user = userMapper.getUserById(id);
        if(user == null) throw new UserNotFoundException("用户不存在");
        return user;
    }

    private User checkUser(LoginParam param) {
        User user = userMapper.getUserById(param.getId());
        if(user == null) throw new UserNotFoundException("用户不存在");
        return user;
    }


    private void hashPassword(PasswordAndSalt param) {
        //生成随机盐值
        byte[] salt = HashWithSalt.generateSalt();
        //对密码进行哈希加盐
        byte[] hashedPassword = HashWithSalt.hashPassword(param.getHashedPassword(), salt);
        //将哈希后的密码和盐值转为Base64字符串
        param.setHashedPassword(Base64.getEncoder().encodeToString(hashedPassword));
        param.setSalt(Base64.getEncoder().encodeToString(salt));
    }
}
