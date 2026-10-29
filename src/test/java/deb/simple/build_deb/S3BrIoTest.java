package deb.simple.build_deb;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static deb.simple.build_deb.DebPackageConfig.PackageMeta.SD_INDEX_EXTENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3BrIoTest {

    @Test
    void readMetas_followsAllTruncatedPages() {
        var s3Client = mock(S3Client.class);

        // 4 pages: 3 truncated + 1 final; each page has index files plus a non-index file
        var pages = new ArrayList<ListObjectsV2Response>();
        int pageCount = 4;
        int perPage = 3;
        for (int p = 0; p < pageCount; p++) {
            var contents = new ArrayList<S3Object>();
            for (int i = 0; i < perPage; i++) {
                String codeName = i % 2 == 0 ? "noble" : "trixie";
                contents.add(S3Object.builder().key("pool/" + codeName + "/pkg-" + p + "-" + i + SD_INDEX_EXTENSION).build());
            }
            contents.add(S3Object.builder().key("pool/noble/pkg-" + p + ".deb").build());
            boolean last = p == pageCount - 1;
            pages.add(ListObjectsV2Response.builder()
                    .contents(contents)
                    .isTruncated(!last)
                    .nextContinuationToken(last ? null : "token-" + (p + 1))
                    .build());
        }
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(pages.getFirst(), pages.subList(1, pages.size()).toArray(ListObjectsV2Response[]::new));

        var jsonMapper = JsonMapper.builder().build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            var meta = new DebPackageMeta().setSize(request.key().length());
            byte[] bytes = jsonMapper.writeValueAsString(meta).getBytes(StandardCharsets.UTF_8);
            return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ByteArrayInputStream(bytes)));
        });

        var io = new BuildRepositoryIO.S3BrIo(s3Client, jsonMapper, URI.create("s3://bucket/pool"), URI.create("s3://bucket/out/"));
        var metas = io.readMetas();

        // every index object on every page is read
        int total = metas.values().stream().mapToInt(List::size).sum();
        assertEquals(pageCount * perPage, total);
        assertEquals(pageCount * 2, metas.get("noble").size());
        assertEquals(pageCount, metas.get("trixie").size());
        verify(s3Client, times(pageCount * perPage)).getObject(any(GetObjectRequest.class));

        // the continuation token from each page is passed to the next request, with the same bucket/prefix
        var captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(pageCount)).listObjectsV2(captor.capture());
        var requests = captor.getAllValues();
        assertNull(requests.getFirst().continuationToken());
        IntStream.range(1, pageCount).forEach(p -> assertEquals("token-" + p, requests.get(p).continuationToken()));
        requests.forEach(r -> {
            assertEquals("bucket", r.bucket());
            assertEquals("pool", r.prefix());
        });
        assertEquals(List.of("noble", "trixie"), metas.keySet().stream().sorted().toList());
    }
}
