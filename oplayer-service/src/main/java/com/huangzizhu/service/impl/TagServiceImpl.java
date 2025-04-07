package com.huangzizhu.service.impl;

import com.huangzizhu.exception.ParamInvalidException;
import com.huangzizhu.exception.TagException;
import com.huangzizhu.mapper.SongMapper;
import com.huangzizhu.mapper.TagMapper;
import com.huangzizhu.pojo.QueryResult;
import com.huangzizhu.pojo.Song;
import com.huangzizhu.pojo.tag.Tag;
import com.huangzizhu.pojo.tag.TagCategory;
import com.huangzizhu.pojo.tag.TagForSongParam;
import com.huangzizhu.service.SongService;
import com.huangzizhu.service.TagService;
import com.huangzizhu.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class TagServiceImpl implements TagService {
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private SongMapper songMapper;

    @Override
    public QueryResult<TagCategory> getTagCategory() {
        List<TagCategory> list = tagMapper.getTagCategory();
        Integer total = list.size();
        return new QueryResult<>(total,list);
    }

    @Override
    public QueryResult<Tag> getTags() {
        List<Tag> list = tagMapper.getTags();
        Integer total = list.size();
        return new QueryResult<>(total,list);
    }

    @Override
    public QueryResult<Tag> getTags(Integer categoryId) {
        checkCategory(categoryId);
        List<Tag> list = tagMapper.getTagsByCateId(categoryId);
        Integer total = list.size();
        return new QueryResult<>(total,list);
    }

    @Override
    public Tag getTag(Integer tagId) {
        return checkTag(tagId);
    }

    @Override
    public void addTag(Tag param) {
        checkCategory(param.getCategoryId());
        checkTagIsExist(param);
        checkInfo(param);
        param.setCreateTime(LocalDateTime.now());
        param.setUpdateTime(LocalDateTime.now());
        param.setStatus(1);
        tagMapper.addTag(param);
    }



    @Override
    public void deleteTag(Integer tagId) {
        checkTag(tagId);
        tagMapper.deleteTag(tagId);
    }

    @Override
    public void updateTag(Tag param) {
        checkCategory(param.getCategoryId());
        checkTag(param.getId());
        checkTagIsExist(param);
        checkInfo(param);
        param.setUpdateTime(LocalDateTime.now());
        param.setStatus(1);
        tagMapper.updateTag(param);
    }

    @Override
    public void addTagForMusic(TagForSongParam param) {
        checkSong(param.getSongId());
        checkTag(param.getTagId());
        tagMapper.addTagForSong(param);
    }

    @Override
    public void deleteTagForMusic(TagForSongParam param) {
        checkTag(param.getTagId());
        checkSong(param.getSongId());
        Integer affectRows = tagMapper.deleteTagForMusic(param);
        if (affectRows == 0) throw new TagException("该歌曲没有这个标签");
    }

    @Override
    public QueryResult<Tag> getTagsBySongId(Integer songId) {
        checkSong(songId);
        List<Tag> list = tagMapper.getTagsBySongId(songId);
        Integer total = list.size();
        return new QueryResult<>(total,list);
    }

    private Tag checkTag(Integer tagId) {
        Tag tag = tagMapper.getTag(tagId);
        if (tag == null) {
            throw new ParamInvalidException("标签不存在");
        }
        return tag;
    }
    private void checkTagIsExist(Tag tag) {
        Tag res = tagMapper.getTagByTag(tag);
        if (res != null) {
            throw new ParamInvalidException("标签已存在");
        }
    }

    private Song checkSong(Integer id) {
        Song song = songMapper.getMusicById(id);
        if (song == null) {
            throw new ParamInvalidException("歌曲不存在");
        }
        return song;
    }

    private TagCategory checkCategory(Integer categoryId) {
        TagCategory tagCategory = tagMapper.getTagCategoryById(categoryId);
        if (tagCategory == null) {
            throw new ParamInvalidException("分类不存在");
        }
        return tagCategory;
    }

    private void checkInfo(Tag param) {
        if(CommonUtils.isBlank(param.getName())) throw new ParamInvalidException("标签名称不能为空");
        if(CommonUtils.isBlank(param.getDescription())) throw new ParamInvalidException("标签描述不能为空");
    }
}
