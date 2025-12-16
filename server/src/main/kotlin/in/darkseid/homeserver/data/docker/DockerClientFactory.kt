package `in`.darkseid.homeserver.data.docker

import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

object DockerClientFactory {
    fun create() = DockerClientBuilder.getInstance(
        DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    ).withDockerHttpClient(
        ApacheDockerHttpClient.Builder()
            .dockerHost(java.net.URI("unix:///var/run/docker.sock"))
            .build()
    ).build()
}
