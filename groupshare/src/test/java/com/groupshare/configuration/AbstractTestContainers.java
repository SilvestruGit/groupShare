package com.groupshare.configuration;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class AbstractTestContainers implements DisposableBean {

    private static final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("groupshare")
            .withUsername("admin")
            .withPassword("admin");

    private static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data");

    static {
        postgreSQLContainer.start();

        System.setProperty("spring.datasource.url", postgreSQLContainer.getJdbcUrl());
        System.setProperty("spring.datasource.password", postgreSQLContainer.getPassword());
        System.setProperty("spring.datasource.username", postgreSQLContainer.getUsername());

        minio.start();
        String minioHost = minio.getHost();
        Integer minioPort = minio.getMappedPort(9000);
        String minioEndpoint = "http://" + minioHost + ":" + minioPort;

        System.setProperty("minio.url", minioEndpoint);
        System.setProperty("minio.accessKey", "minioadmin");
        System.setProperty("minio.secretKey", "minioadmin");
    }

    @Override
    public void destroy() throws Exception {
        if (postgreSQLContainer != null) {
            postgreSQLContainer.close();
        }
        if (minio != null) {
            minio.close();
        }
    }
}
