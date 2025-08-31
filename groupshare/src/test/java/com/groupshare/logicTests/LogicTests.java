package com.groupshare.logicTests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.groupshare.configuration.AbstractTestContainers;

import lombok.SneakyThrows;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AbstractTestContainers.class)
public class LogicTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @SneakyThrows
        void testCreateAlbumWithoutName_ShouldFail() {
                mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @SneakyThrows
        void testUploadFileToNonexistentAlbum_ShouldFail() {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test.jpg",
                                "image/jpeg",
                                "fakeimagecontent".getBytes());

                mockMvc.perform(multipart("/api/albums/does-not-exist/upload").file(file))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @SneakyThrows
        void testDownloadNonexistentMedia_ShouldFail() {
                mockMvc.perform(get("/api/media/00000000-0000-0000-0000-000000000000/download"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @SneakyThrows
        void testListMediaFromEmptyAlbum() throws Exception {
                // Create empty album
                String albumResponse = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Empty Album\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String albumId = albumResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

                mockMvc.perform(get("/api/albums/" + albumId + "/media"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.media").isEmpty());

                // Clean up
                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());
        }

        @Test
        @SneakyThrows
        void testDuplicateAlbumName_ShouldFailOrCreateNew() {
                // Create first album
                String albumResponse1 = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Duplicate Album\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String albumId1 = albumResponse1.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

                // Try to create same name again
                mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Duplicate Album\"}"))
                                .andExpect(status().isConflict());

                // Clean up
                mockMvc.perform(delete("/api/albums/" + albumId1))
                                .andExpect(status().isNoContent());
        }

        @Test
        @SneakyThrows
        void testUploadInvalidFileType_ShouldFail() {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "malware.exe",
                                "application/octet-stream",
                                "badstuff".getBytes());

                String albumResponse = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Invalid Upload\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String albumId = albumResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

                mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file))
                                .andExpect(status().isUnsupportedMediaType());

                // Clean up
                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());
        }

        @Test
        @SneakyThrows
        void testUploadAndDownloadMultipleFiles() {
                // Create album
                String albumResponse = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Multi Upload\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String albumId = albumResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

                // Upload 2 files
                MockMultipartFile file1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", "aaa".getBytes());
                MockMultipartFile file2 = new MockMultipartFile("file", "b.jpg", "image/jpeg", "bbb".getBytes());

                String mediaResponse1 = mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file1))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                String mediaId1 = mediaResponse1.replaceAll(".*\"mediaId\":\"([^\"]+)\".*", "$1");

                String mediaResponse2 = mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file2))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                String mediaId2 = mediaResponse2.replaceAll(".*\"mediaId\":\"([^\"]+)\".*", "$1");

                // List should return both
                mockMvc.perform(get("/api/albums/" + albumId + "/media"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.media.length()").value(2));

                // Download should succeed for both
                mockMvc.perform(get("/api/media/" + mediaId1 + "/download"))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/media/" + mediaId2 + "/download"))
                                .andExpect(status().isOk());

                // Clean up
                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());
        }

        @Test
        @SneakyThrows
        void testDeleteAlbum_ShouldCascadeMediaRemoval() throws Exception {
                // Create album
                String albumResponse = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Cascade Delete\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String albumId = albumResponse.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

                // Upload file
                MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", "xxx".getBytes());
                mockMvc.perform(multipart("/api/albums/" + albumId + "/upload").file(file))
                                .andExpect(status().isCreated());

                // Delete album
                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());

                // Verify album media no longer accessible
                mockMvc.perform(get("/api/albums/" + albumId + "/media"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.media").isEmpty());
        }
}
