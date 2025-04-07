package com.huangzizhu.service.impl;

import com.huangzizhu.exception.GetMusicResourceException;
import com.huangzizhu.exception.ParamInvalidException;
import com.huangzizhu.mapper.AlbumMapper;
import com.huangzizhu.mapper.SongMapper;
import com.huangzizhu.mapper.TagMapper;
import com.huangzizhu.pojo.Album;
import com.huangzizhu.pojo.QueryResult;
import com.huangzizhu.pojo.Song;
import com.huangzizhu.pojo.music.MusicQueryForm;
import com.huangzizhu.pojo.music.SimpleMusicInfo;
import com.huangzizhu.pojo.tag.Tag;
import com.huangzizhu.service.SongService;
import com.huangzizhu.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class SongServiceImpl implements SongService {

    @Autowired
    private SongMapper songMapper;
    @Autowired
    private AlbumMapper albumMapper;
    @Autowired
    private TagMapper tagMapper;



    @Override
    public HashSet<String> getAllMD5() {
        return songMapper.getAllMD5();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addSongs(List<Song> songs) {
        preProcess result = getPreProcess(songs);
        //根据MD5将歌曲分组
        List<Song> songsToAdd = new ArrayList<>();
        List<Song> songsToUpdate = new ArrayList<>();
        result.songs().forEach(song -> {
            if (result.finalMD5s().contains(song.getMd5())){
                setAlbumInfo(song);
                songsToAdd.add(song);
            }
            else songsToUpdate.add(song);
        });
        if(!songsToAdd.isEmpty()) songMapper.addSongs(songsToAdd);//批量增加
        if (!songsToUpdate.isEmpty()) updateSongs(songsToUpdate);//提交给更新处理
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateSongs(List<Song> songs) {
        preProcess result = getPreProcess(songs);
        //根据MD5将歌曲分组
        List<Song> songsToUpdate = new ArrayList<>();
        List<Song> songsToAdd = new ArrayList<>();
        result.songs().forEach(song -> {
            if (!result.finalMD5s().contains(song.getMd5())){
                setAlbumInfo(song);
                songsToUpdate.add(song);
            }
            else songsToAdd.add(song);
        });
        //由于更新的次数很少，所以不使用批量更新
        if(!songsToUpdate.isEmpty()){
            for (Song song : songsToUpdate) {
                songMapper.updateSong(song);
            }
        }
        if(!songsToAdd.isEmpty()) addSongs(songsToAdd);//提交给增加处理
    }

    @Override
    public void markInvalid(List<String> list) {songMapper.markInvalid(list);}

    @Override
    public Resource getMusicResource(String md5) {
        String filePath = songMapper.getSongByMD5(md5);
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new GetMusicResourceException("获取音乐资源:"+filePath+"失败");
            }
        }catch (Exception e){
            throw new GetMusicResourceException("获取音乐资源:"+filePath+"失败");
        }
    }
    @Override
    public ResponseEntity<StreamingResponseBody> handlePartialContent(Resource resource, String rangeHeader) throws IOException {
        // 解析Range头
        long rangeStart = 0;
        long rangeEnd = resource.contentLength() - 1;
        long fileSize = resource.contentLength();

        String[] ranges = rangeHeader.substring(6).split("-");
        rangeStart = Long.parseLong(ranges[0]);
        if (ranges.length > 1) {
            rangeEnd = Long.parseLong(ranges[1]);
        }

        // 验证范围
        if (rangeStart >= fileSize || rangeEnd >= fileSize) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                    .build();
        }

        long contentLength = rangeEnd - rangeStart + 1;

        // 增强版的流传输实现
        long finalRangeStart = rangeStart;
        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream in = resource.getInputStream()) {
                in.skip(finalRangeStart);
                byte[] buffer = new byte[64 * 1024]; // 64KB缓冲区
                long remaining = contentLength;

                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) break;

                    try {
                        outputStream.write(buffer, 0, read);
                        remaining -= read;
                    } catch (IOException e) {
                        // 客户端断开连接时安全退出
                        if (e.getMessage() != null &&
                                (e.getMessage().contains("Connection reset") ||
                                        e.getMessage().contains("broken pipe"))) {
                            log.debug("客户端中断连接: {}", e.getMessage());
                            break;
                        }
                        throw e;
                    }
                }
            }
        };

        // 构建响应
        String contentRange = String.format("bytes %d-%d/%d", rangeStart, rangeEnd, fileSize);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.valueOf("audio/mpeg")) // 推荐使用具体类型而非APPLICATION_OCTET_STREAM
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(responseBody);
    }
    @Override
    public ResponseEntity<Resource> downloadMusic(String md5) throws IOException {
        // 获取音乐资源
        Resource resource = getMusicResource(md5);
        // 构建响应
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @Override
    public QueryResult<Song> getAllMusic(MusicQueryForm param) {
        param.setStart(param.getPageSize() * (param.getPage() - 1));
        //获取总数
        Integer total = songMapper.getTotal(param);
        //获取数据
        List<Song> songs = songMapper.getAllMusic(param);
        return new QueryResult<>(total, songs);
    }

    @Override
    public Song getMusicById(Integer id) {
        return songMapper.getMusicById(id);
    }

    @Override
    public List<SimpleMusicInfo> fuzzySearch(String name) {
        return songMapper.fuzzySearch(name);
    }

    @Override
    public QueryResult<Song> getMusicByTagId(MusicQueryForm param) {
        checkTag(param.getTagId());
        param.setStart(param.getPageSize() * (param.getPage()-1));
        Integer total = songMapper.getMusicCountByTagId(param.getTagId());
        List<Song> songs = songMapper.getMusicByTagId(param);
        return new QueryResult<>(total, songs);
    }

    @Override
    public QueryResult<Song> getMusic(MusicQueryForm param) {
        param.setStart(param.getPageSize() * (param.getPage()-1));
        Integer total = songMapper.getMusicCount(param);
        List<Song> songs = songMapper.getMusic(param);
        return new QueryResult<>(total, songs);
    }

    private preProcess getPreProcess(List<Song> songs) {
        //去重
        songs = songs.stream().distinct().toList();
        //获取MD5
        List<String> md5s = songs.stream().map(Song::getMd5).toList();
        //获取不存在于数据库的MD5
        List<String> finalMD5s = songMapper.getMD5sNotExist(md5s);
        preProcess result = new preProcess(songs, finalMD5s);
        return result;
    }

    private record preProcess(List<Song> songs, List<String> finalMD5s) {
    }
    private Tag checkTag(Integer tagId) {
        Tag tag = tagMapper.getTag(tagId);
        if (tag == null) {
            throw new ParamInvalidException("标签不存在");
        }
        return tag;
    }

    private void setAlbumInfo(Song song) {
        String albumName = song.getAlbumName();
        String artist = song.getArtist();
        if(albumName == null || albumName.isBlank()){
            albumName = "unknown";
        }
        if(artist == null || artist.isBlank()){
            artist = "unknown";
        }
        song.setAlbumName(albumName);
        song.setArtist(artist);
        //获取专辑id
        Integer albumId = albumMapper.getAlbumId(albumName, artist);
        if (albumId != null){
            song.setAlbumId(albumId);
        }
        else{
            //专辑不存在，插入专辑
            Album album = new Album(null, albumName, artist);
            albumMapper.addAlbum(album);
            song.setAlbumId(album.getAlbumId());
        }
    }
}
