package com.huangzizhu.service.impl;

import com.google.code.kaptcha.Producer;
import com.huangzizhu.annotion.Log;
import com.huangzizhu.exception.CaptchaException;
import com.huangzizhu.exception.EmailException;
import com.huangzizhu.exception.FIleException;
import com.huangzizhu.mapper.SentenceMapper;
import com.huangzizhu.pojo.Says;
import com.huangzizhu.pojo.SendEmailParam;
import com.huangzizhu.pojo.WeatherInfo;
import com.huangzizhu.service.EmailCaptchaManager;
import com.huangzizhu.service.GaodeService;
import com.huangzizhu.service.ImageCaptchaManager;
import com.huangzizhu.service.OtherService;
import com.huangzizhu.utils.AliOSSOperator;
import com.huangzizhu.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class OtherServiceImpl implements OtherService {
    @Autowired AliOSSOperator aliOSSOperator;
    @Autowired
    Producer kaptchaProducer;
    @Autowired
    SentenceMapper sentenceMapper;
    @Autowired
    GaodeService gaodeService;
    private Random random = new Random();
    @Log
    @Override
    public String uploadImg(MultipartFile file) {
        if (file.isEmpty()) throw new FIleException("文件不能为空!");
        if(CommonUtils.isBlank(file.getOriginalFilename())) throw new FIleException("文件名不能为空!");
        try {
            return aliOSSOperator.uploadFile(file.getBytes(), UUID.randomUUID() + file.getOriginalFilename());
        } catch (Exception e){
            throw new FIleException("上传图片失败",e);
        }
    }

    @Override
    public BufferedImage getCaptchaImage(String uuid) {
        try {
            String captchaText = kaptchaProducer.createText();
            ImageCaptchaManager.getInstance().generateAndStoreCode(uuid, captchaText);
            return kaptchaProducer.createImage(captchaText);
        } catch (Exception e) {
            throw new CaptchaException("生成验证码失败", e);
        }
    }

    @Override
    public void sendEmail(SendEmailParam param) {
        //验证邮箱格式
        if(!CommonUtils.isValidEmail(param.getEmail())) throw new EmailException("邮箱格式错误");
        //验证验证码是否有效
        if(!ImageCaptchaManager.getInstance().verifyCode(param.getUuid(), param.getCaptcha())) throw new CaptchaException("验证码错误");
        //生成验证码
        try {
            String captchaText = kaptchaProducer.createText();
            EmailCaptchaManager.getInstance().generateAndStoreCode(param.getEmail(),captchaText);
        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("发送邮件失败", e);
        }
    }

    @Override
    public Says getSays() {
        Integer total = sentenceMapper.getCount();
        if (total == null || total == 0) {
            throw new RuntimeException("没有可用的句子");
        }
        int randId = random.nextInt(total) + 1;
        return sentenceMapper.getSays(randId);
    }

    @Override
    public WeatherInfo getWeather(String ip) {
        log.info("ip:{}",ip);
        WeatherInfo weatherInfo = null;
        try {
            weatherInfo = gaodeService.getWeatherInfo(ip);
        } catch (Exception e) {
            throw new RuntimeException("获取天气信息失败",e);
        }
        return weatherInfo;
    }
}
