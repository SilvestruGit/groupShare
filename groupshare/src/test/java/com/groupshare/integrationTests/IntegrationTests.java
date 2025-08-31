package com.groupshare.integrationTests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupshare.configuration.AbstractTestContainers;

import lombok.SneakyThrows;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AbstractTestContainers.class)
class IntegrationTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @AfterEach
        void cleanup() throws Exception {
                // 1. Clean database tables
                jdbcTemplate.execute("DELETE FROM media");
                jdbcTemplate.execute("DELETE FROM albums");
        }

        @Test
        @SneakyThrows
        public void testAlbumLifecycle() {
                // 1. Create Album
                MvcResult albumResult = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Summer Trip\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andReturn();

                JsonNode albumJson = objectMapper.readTree(albumResult.getResponse().getContentAsString());
                String albumId = albumJson.get("id").asText();

                // ✅ Assert in DB
                List<Map<String, Object>> albums = jdbcTemplate.queryForList(
                                "SELECT * FROM albums WHERE id = ?", UUID.fromString(albumId));
                Assertions.assertEquals(1, albums.size());
                Assertions.assertEquals("Summer Trip", albums.get(0).get("name"));

                // 2. Upload File
                MockMultipartFile file = new MockMultipartFile(
                                "file", "test.jpg", "image/jpeg", "fakeimagecontent".getBytes());

                MvcResult mediaResult = mockMvc.perform(multipart("/api/albums/" + albumId + "/upload")
                                .file(file))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.mediaId").exists())
                                .andReturn();

                JsonNode mediaJson = objectMapper.readTree(mediaResult.getResponse().getContentAsString());
                String mediaId = mediaJson.get("mediaId").asText();

                // ✅ Assert in DB
                List<Map<String, Object>> media = jdbcTemplate.queryForList(
                                "SELECT * FROM media WHERE id = ?", UUID.fromString(mediaId));
                Assertions.assertEquals(1, media.size());
                Assertions.assertEquals("test.jpg", media.get(0).get("file_name"));

                // 3. List Files
                mockMvc.perform(get("/api/albums/" + albumId + "/media"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.media[0].fileName").value("test.jpg"));

                // 4. Download File
                MvcResult downloadResult = mockMvc.perform(get("/api/media/" + mediaId + "/download"))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.jpg\""))
                                .andReturn();

                byte[] downloaded = downloadResult.getResponse().getContentAsByteArray();
                Assertions.assertArrayEquals("fakeimagecontent".getBytes(), downloaded);

                // 5. Try duplicate upload (should fail with 409)
                mockMvc.perform(multipart("/api/albums/" + albumId + "/upload")
                                .file(file))
                                .andExpect(status().isConflict());

                // 6. Delete media
                mockMvc.perform(delete("/api/media/" + mediaId))
                                .andExpect(status().isNoContent());
                Assertions.assertEquals(0, jdbcTemplate
                                .queryForList("SELECT * FROM media WHERE id = ?", UUID.fromString(mediaId)).size());

                // 7. Delete album
                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());
                Assertions.assertEquals(0, jdbcTemplate
                                .queryForList("SELECT * FROM albums WHERE id = ?", UUID.fromString(albumId)).size());
        }

        @Test
        @SneakyThrows
        void testBadRequests_withDbChecks() {
                // Try to upload to a non-existing album
                MockMultipartFile file = new MockMultipartFile(
                                "file", "bad.jpg", "image/jpeg", "invalid".getBytes());

                mockMvc.perform(multipart("/api/albums/doesnotexist/upload")
                                .file(file))
                                .andExpect(status().isBadRequest());

                // ✅ Assert in DB → should be empty
                Assertions.assertEquals(0, jdbcTemplate.queryForList("SELECT * FROM media").size());
                Assertions.assertEquals(0, jdbcTemplate.queryForList("SELECT * FROM albums").size());

                // Try to create album with duplicate name
                MvcResult albumResult = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"UniqueName\"}"))
                                .andExpect(status().isCreated()).andReturn();

                mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"UniqueName\"}"))
                                .andExpect(status().isConflict());

                // Try to create album with invalid payload
                mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                                .andExpect(status().isBadRequest());

                mockMvc.perform(delete("/api/albums/" + objectMapper
                                .readTree(albumResult.getResponse().getContentAsString()).get("id").asText()));
        }

        private static String sha256(byte[] data) throws Exception {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return Base64.getEncoder().encodeToString(digest.digest(data));
        }

        @Test
        @SneakyThrows
        void testUploadDownloadIntegrity() {
                // Step 1: Create Album
                MvcResult albumResult = mockMvc.perform(post("/api/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Trip with Friends\"}"))
                                .andExpect(status().isCreated())
                                .andReturn();

                JsonNode albumJson = objectMapper.readTree(albumResult.getResponse().getContentAsString());
                String albumId = albumJson.get("id").asText();

                // Step 2: Upload File
                byte[] fileContent = "This is a fake test file for integrity check.".getBytes();
                String uploadHash = sha256(fileContent);

                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "integrity.txt",
                                "text/plain",
                                fileContent);

                MvcResult mediaResult = mockMvc.perform(multipart("/api/albums/" + albumId + "/upload")
                                .file(file))
                                .andExpect(status().isCreated())
                                .andReturn();

                JsonNode mediaJson = objectMapper.readTree(mediaResult.getResponse().getContentAsString());
                String mediaId = mediaJson.get("mediaId").asText();

                // Step 3: Download File
                // If your download endpoint writes to disk, check the file on disk
                // If it returns bytes, check the response body
                // Here, we assume it writes to disk as per your implementation
                MvcResult downloadResult = mockMvc.perform(get("/api/media/" + mediaId + "/download"))
                                .andExpect(status().isOk())
                                .andReturn();

                byte[] downloadedBytes = downloadResult.getResponse().getContentAsByteArray();
                String downloadHash = sha256(downloadedBytes);

                // Step 4: Assert Hash Equality
                Assertions.assertEquals(uploadHash, downloadHash);

                mockMvc.perform(delete("/api/albums/" + albumId))
                                .andExpect(status().isNoContent());
        }
}
