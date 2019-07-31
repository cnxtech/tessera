package config;

import com.quorum.tessera.config.*;
import com.quorum.tessera.config.keypairs.ConfigKeyPair;
import com.quorum.tessera.config.keypairs.DirectKeyPair;
import suite.EnclaveType;
import suite.ExecutionContext;
import suite.SocketType;
import suite.SslType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigBuilder {

    private Integer q2tPort;

    private Integer p2pPort;

    private Integer adminPort;

    private Integer enclavePort;

    private ExecutionContext executionContext;

    private String nodeId;

    private Integer nodeNumber;

    private List<String> peerUrls = new ArrayList<>();

    private Map<String, String> keys = new HashMap<>();

    private List<String> alwaysSendTo = new ArrayList<>();

    private SocketType q2tSocketType;

    private FeatureToggles featureToggles;

    public ConfigBuilder withQ2TSocketType(SocketType q2tSocketType) {
        this.q2tSocketType = q2tSocketType;
        return this;
    }

    public ConfigBuilder withAlwaysSendTo(String alwaysSendTo) {
        this.alwaysSendTo.add(alwaysSendTo);
        return this;
    }

    public ConfigBuilder withKeys(Map<String, String> keys) {
        this.keys.putAll(keys);
        return this;
    }

    public ConfigBuilder withKeys(String publicKey, String privateKey) {
        this.keys.put(publicKey, privateKey);
        return this;
    }

    public ConfigBuilder withAdminPort(Integer adminPort) {
        this.adminPort = adminPort;
        return this;
    }

    public ConfigBuilder withQt2Port(Integer q2tPort) {
        this.q2tPort = q2tPort;
        return this;
    }

    public ConfigBuilder withP2pPort(Integer p2pPort) {
        this.p2pPort = p2pPort;
        return this;
    }

    public ConfigBuilder withEnclavePort(Integer enclavePort) {
        this.enclavePort = enclavePort;
        return this;
    }

    public ConfigBuilder withNodeNumber(Integer nodeNumber) {
        this.nodeNumber = nodeNumber;
        return this;
    }

    public ConfigBuilder withNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public ConfigBuilder withPeer(String peerUrl) {
        this.peerUrls.add(peerUrl);
        return this;
    }

    public ConfigBuilder withExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        return this;
    }

    public ConfigBuilder withFeatureToggles(final FeatureToggles featureToggles) {
        this.featureToggles = featureToggles;
        return this;
    }

    public Config build() {
        final Config config = new Config();

        JdbcConfig jdbcConfig = new JdbcConfig();
        jdbcConfig.setUrl(executionContext.getDbType().createUrl(nodeId, nodeNumber));
        jdbcConfig.setUsername("sa");
        jdbcConfig.setPassword("");
        config.setJdbcConfig(jdbcConfig);

        ServerConfig q2tServerConfig = new ServerConfig();
        q2tServerConfig.setApp(AppType.Q2T);
        q2tServerConfig.setEnabled(true);
        q2tServerConfig.setCommunicationType(executionContext.getCommunicationType());

        if (executionContext.getCommunicationType() == CommunicationType.REST
                && (q2tSocketType != null || executionContext.getSocketType() == SocketType.UNIX)) {
            q2tServerConfig.setServerAddress(String.format("unix:/tmp/q2t-rest-unix-%d.ipc", nodeNumber));
        } else {
            q2tServerConfig.setServerAddress("http://localhost:" + q2tPort);
            q2tServerConfig.setBindingAddress("http://0.0.0.0:" + q2tPort);
        }

        final Path sslDirectory;
        try {
            sslDirectory = Files.createTempDirectory("sslFiles");
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }

        //        final SslConfig generalSslConfig =
        //                new SslConfig(
        //                        SslAuthenticationMode.STRICT,
        //                        true,
        //                        sslDirectory.resolve("serverkeystore"),
        //                        "testtest",
        //                        sslDirectory.resolve("servertruststore"),
        //                        "testtest",
        //                        SslTrustMode.TOFU,
        //                        sslDirectory.resolve("clientkeystore"),
        //                        "testtest",
        //                        sslDirectory.resolve("clienttruststore"),
        //                        "testtest",
        //                        SslTrustMode.TOFU,
        //                        sslDirectory.resolve("knownclient"),
        //                        sslDirectory.resolve("knownservers"),
        //                        null,
        //                        null,
        //                        null,
        //                        null,
        //                        null,
        //                        null,
        //                        null);

        SslConfig generalSslConfig =
                new SslConfig(
                        SslAuthenticationMode.STRICT,
                        false,
                        Paths.get(getClass().getResource("/certificates/localhost-with-san-keystore.jks").getFile()),
                        "testtest",
                        Paths.get(getClass().getResource("/certificates/truststore.jks").getFile()),
                        "testtest",
                        SslTrustMode.CA,
                        Paths.get(getClass().getResource("/certificates/quorum-client-keystore.jks").getFile()),
                        "testtest",
                        Paths.get(getClass().getResource("/certificates/truststore.jks").getFile()),
                        "testtest",
                        SslTrustMode.CA,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        List<ServerConfig> servers = new ArrayList<>();

        servers.add(q2tServerConfig);

        ServerConfig p2pServerConfig = new ServerConfig();
        p2pServerConfig.setApp(AppType.P2P);
        p2pServerConfig.setEnabled(true);
        p2pServerConfig.setCommunicationType(executionContext.getCommunicationType());
        if (executionContext.getSslType() == SslType.TOFU) {
            p2pServerConfig.setServerAddress("https://localhost:" + p2pPort);
            p2pServerConfig.setBindingAddress("https://0.0.0.0:" + p2pPort);
            p2pServerConfig.setSslConfig(generalSslConfig);
        } else {
            p2pServerConfig.setServerAddress("http://localhost:" + p2pPort);
            p2pServerConfig.setBindingAddress("http://0.0.0.0:" + p2pPort);
        }
        servers.add(p2pServerConfig);

        if (executionContext.getCommunicationType() == CommunicationType.REST && Objects.nonNull(adminPort)) {
            ServerConfig adminServerConfig = new ServerConfig();
            adminServerConfig.setApp(AppType.ADMIN);
            adminServerConfig.setEnabled(true);
            adminServerConfig.setCommunicationType(CommunicationType.REST);

            if (executionContext.getSslType() == SslType.TOFU) {
                adminServerConfig.setServerAddress("https://localhost:" + adminPort);
                adminServerConfig.setBindingAddress("https://0.0.0.0:" + adminPort);
                adminServerConfig.setSslConfig(generalSslConfig);
            } else {
                adminServerConfig.setServerAddress("http://localhost:" + adminPort);
                adminServerConfig.setBindingAddress("http://0.0.0.0:" + adminPort);
            }

            servers.add(adminServerConfig);
        }

        if (executionContext.getEnclaveType() == EnclaveType.REMOTE) {
            ServerConfig enclaveServerConfig = new ServerConfig();
            enclaveServerConfig.setApp(AppType.ENCLAVE);
            enclaveServerConfig.setEnabled(true);

            if (executionContext.getSslType() == SslType.TOFU) {
                enclaveServerConfig.setBindingAddress("https://0.0.0.0:" + enclavePort);
                enclaveServerConfig.setServerAddress("https://localhost:" + enclavePort);
                enclaveServerConfig.setSslConfig(generalSslConfig);
            } else {
                enclaveServerConfig.setBindingAddress("http://0.0.0.0:" + enclavePort);
                enclaveServerConfig.setServerAddress("http://localhost:" + enclavePort);
            }

            enclaveServerConfig.setCommunicationType(CommunicationType.REST);

            servers.add(enclaveServerConfig);
        }

        config.setServerConfigs(servers);

        peerUrls.stream().map(Peer::new).forEach(config::addPeer);

        config.setKeys(new KeyConfiguration());

        final List<ConfigKeyPair> pairs =
                keys.entrySet().stream()
                        .map(e -> new DirectKeyPair(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());

        config.getKeys().setKeyData(pairs);

        config.setAlwaysSendTo(alwaysSendTo);

        config.setFeatures(featureToggles);

        return config;
    }
}
