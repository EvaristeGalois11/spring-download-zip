package com.example.springdownloadzip;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping(path = "download")
public class DownloadController {

    private final Executor executor;

    public DownloadController(Executor executor) {
        this.executor = executor;
    }

    @GetMapping(path = "tmp-file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Resource downloadTmpFile(HttpServletResponse response) throws IOException {
        var start = Instant.now();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compressed-tmp-file.zip");
        var resource = new PathResource(compressTmpFile());
        var end = Instant.now();
        printDuration(start, end, "Using a temporary file");
        return resource;
    }

    @GetMapping(path = "piped", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Resource downloadPiped(HttpServletResponse response) {
        var start = Instant.now();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compressed-piped.zip");
        return new InputStreamResource(compressPiped(start));
    }

    @GetMapping(path = "streaming", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody downloadStreaming(HttpServletResponse response) {
        var start = Instant.now();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compressed-streaming.zip");
        return outputStream -> {
            compressStreaming(outputStream);
            var end = Instant.now();
            printDuration(start, end, "Streaming");
        };
    }

    private Path compressTmpFile() throws IOException {
        var zip = Files.createTempFile("compressed", ".zip");
        try (var out = Files.newOutputStream(zip);
             var zipOut = new ZipOutputStream(out)) {
            compress(zipOut);
        }
        return zip;
    }

    private InputStream compressPiped(Instant start) {
        var pipeIn = new PipedInputStream();
        executor.execute(() -> {
            try (var pipeOut = new PipedOutputStream(pipeIn);
                 var zipOut = new ZipOutputStream(pipeOut)) {
                compress(zipOut);
                var end = Instant.now();
                printDuration(start, end, "Piping");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return pipeIn;
    }

    private void compressStreaming(OutputStream out) throws IOException {
        try (var zipOut = new ZipOutputStream(out)) {
            compress(zipOut);
        }
    }

    private void compress(ZipOutputStream zipOut) throws IOException {
        for (final Path junk : junks()) {
            System.out.println("Processing file " + junk.getFileName().toString());
            try (var junkFis = Files.newInputStream(junk)) {
                var zipEntry = new ZipEntry(junk.getFileName().toString());
                zipOut.putNextEntry(zipEntry);
                junkFis.transferTo(zipOut);
            }
        }
    }

    private List<Path> junks() {
        return Stream.of("junk1.txt", "junk2.txt", "junk3.txt").map(Path::of).toList();
    }

    private void printDuration(Instant start, Instant end, String method) {
        var duration = Duration.between(start, end);
        System.out.println(method + " took about " + duration.getSeconds() + " seconds");
    }
}
