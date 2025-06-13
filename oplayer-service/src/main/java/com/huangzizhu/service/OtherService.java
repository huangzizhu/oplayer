package com.huangzizhu.service;

import com.huangzizhu.pojo.Says;
import com.huangzizhu.pojo.SendEmailParam;
import com.huangzizhu.pojo.WeatherInfo;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;

public interface OtherService {
    String uploadImg(MultipartFile file);

    BufferedImage getCaptchaImage(String uuid);

    void sendEmail(SendEmailParam param);

    Says getSays();

    WeatherInfo getWeather(String ip);
}
