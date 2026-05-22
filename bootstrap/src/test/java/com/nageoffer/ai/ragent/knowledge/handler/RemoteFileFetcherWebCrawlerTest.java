package com.nageoffer.ai.ragent.knowledge.handler;

import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlRequest;
import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlResult;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteFileFetcherWebCrawlerTest {

    @Mock
    private HttpClientHelper httpClientHelper;

    @Mock
    private FileStorageService fileStorageService;

    @Test
    void storesCrawledHtmlPageAsMarkdownForKnowledgeUrlImport() {
        String url = "https://kb.example/guide";
        when(httpClientHelper.head(eq(url), anyMap()))
                .thenReturn(new HttpClientHelper.HttpHeadResponse(null, null,
                        "text/html;charset=utf-8", 128L, "guide.html"));
        when(fileStorageService.upload(eq("kb"), any(InputStream.class), anyLong(),
                contains("kb-example-guide"), eq("text/markdown;charset=UTF-8")))
                .thenReturn(StoredFileDTO.builder()
                        .url("s3://kb/kb-example-guide.md")
                        .originalFilename("kb-example-guide.md")
                        .detectedType("text/markdown")
                        .size(64L)
                        .build());
        RemoteFileFetcher fetcher = new RemoteFileFetcher(httpClientHelper, fileStorageService);
        ReflectionTestUtils.setField(fetcher, "maxFileSize", DataSize.ofMegabytes(5));
        AtomicReference<WebPageCrawlRequest> capturedRequest = new AtomicReference<>();
        fetcher.setWebPageCrawlerAgent(request -> {
            capturedRequest.set(request);
            return new WebPageCrawlResult(url, "Guide",
                    "Knowledge import page text", List.of("https://kb.example/next"), "text/html");
        });

        StoredFileDTO stored = fetcher.fetchAndStore("kb", url);

        assertThat(stored.getUrl()).isEqualTo("s3://kb/kb-example-guide.md");
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().url()).isEqualTo(url);
        verify(httpClientHelper, never()).openStream(eq(url), any(Map.class), anyLong());
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(fileStorageService).upload(eq("kb"), streamCaptor.capture(), anyLong(),
                contains("kb-example-guide"), eq("text/markdown;charset=UTF-8"));
    }
}
