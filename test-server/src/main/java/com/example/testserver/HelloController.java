package com.example.testserver;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Files;

@RestController
public class HelloController {

    /**
     * 播放本地视频，支持拖拽进度
     * @param range 请求头 Range: bytes=0-
     */
    @GetMapping("/video/play")
    public ResponseEntity<Resource> playVideo(
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {
        File videoFile = new File("D:\\Program Files\\edge\\【小甜歌】“生活这么苦啦 听点甜的吧~”.mp4");
        if (!videoFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(videoFile);
        long fileSize = videoFile.length();

        // 没有range头，直接返回完整文件
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .body(resource);
        }

        // 解析 range: bytes=start-end
        String[] parts = rangeHeader.replace("bytes=", "").split("-");
        long start = Long.parseLong(parts[0]);
        long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : fileSize - 1;
        long contentLen = end - start + 1;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4");
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLen));
        headers.add(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));

        // 206 Partial Content 分片状态码
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(new FileSliceResource(resource, start, end));
    }

    // 内部包装Resource，返回文件指定区间字节
    public static class FileSliceResource extends AbstractResource {
        private final Resource delegate;
        private final long start;
        private final long end;

        public FileSliceResource(Resource delegate, long start, long end) {
            this.delegate = delegate;
            this.start = start;
            this.end = end;
        }

        @Override
        public String getDescription() {
            return "slice resource";
        }

        @Override
        public InputStream getInputStream() throws IOException {
            RandomAccessFile raf = new RandomAccessFile(delegate.getFile(), "r");
            raf.seek(start);
            return new FileInputStream(raf.getFD()) {
                private long remain = end - start + 1;
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (remain <=0) return -1;
                    int realLen = (int) Math.min(len, remain);
                    int r = super.read(b, off, realLen);
                    if(r>0) remain -= r;
                    return r;
                }
                @Override
                public int read() throws IOException {
                    if(remain <=0) return -1;
                    remain --;
                    return super.read();
                }
            };
        }
    }
}
