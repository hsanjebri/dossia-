package com.example.dossia.support;

import org.testcontainers.DockerClientFactory;

public final class DockerConditions {

    private DockerConditions() {}

    public static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
