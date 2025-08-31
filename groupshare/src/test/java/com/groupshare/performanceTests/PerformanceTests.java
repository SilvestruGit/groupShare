package com.groupshare.performanceTests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.groupshare.configuration.AbstractTestContainers;

import lombok.SneakyThrows;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AbstractTestContainers.class)
public class PerformanceTests {

    @Autowired
    private MockMvc mockMvc;

    private String albumId;

    @BeforeEach
    void createAlbum() throws Exception {
        String albumResponse = mockMvc.perform(post("/api/albums")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Performance Album\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        albumId = albumResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (albumId != null) {
            mockMvc.perform(
                    MockMvcRequestBuilders.delete("/api/albums/" + albumId))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @SneakyThrows
    void testBulkUploadPerformance() {
        int fileCount = 20;
        List<Long> uploadTimes = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "perf_test_no_" + i + ".txt",
                    "text/plain",
                    ("performance file " + i).getBytes());

            long start = System.currentTimeMillis();
            mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file))
                    .andExpect(status().isCreated());
            long end = System.currentTimeMillis();
            uploadTimes.add(end - start);
        }
        double avg = uploadTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("Average upload time for " + fileCount + " files: " + avg + " ms");
        Assertions.assertTrue(avg < 2000, "Average upload time is too high: " + avg + " ms");
    }

    @Test
    void testLargeFileUploadPerformance() throws Exception {
        // Create a realistic large JPEG file (with a valid JPEG header)
        int size = 50 * 1024 * 1024; // 50MB for test speed
        byte[] largeContent = new byte[size];
        // JPEG header: FF D8 FF E0
        largeContent[0] = (byte) 0xFF;
        largeContent[1] = (byte) 0xD8;
        largeContent[2] = (byte) 0xFF;
        largeContent[3] = (byte) 0xE0;
        // Fill the rest with a repeating pattern
        for (int i = 4; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "largefile.jpg",
                "image/jpeg",
                largeContent);

        long start = System.currentTimeMillis();
        mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file))
                .andExpect(status().isCreated());
        long end = System.currentTimeMillis();
        long duration = end - start;
        System.out.println("Large file upload time: " + duration + " ms");
        Assertions.assertTrue(duration < 60000, "Large file upload took too long: " + duration + " ms");
    }
}
