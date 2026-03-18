package com.chainsettle.service;

import com.chainsettle.config.FabricGatewayProperties;
import com.chainsettle.model.dto.NetworkHealthResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.hyperledger.fabric.client.CloseableIterator;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.stereotype.Service;

@Service
public class FabricGatewayService {
    private final FabricGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, FabricClientHolder> clients = new ConcurrentHashMap<>();

    public FabricGatewayService(final FabricGatewayProperties properties, final ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String submitTransaction(final String orgName, final String function, final String... args) {
        try {
            final Contract contract = clientForOrg(orgName).contract();
            return new String(contract.submitTransaction(function, args), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw translateException(exception);
        }
    }

    public String evaluateTransaction(final String orgName, final String function, final String... args) {
        try {
            final Contract contract = clientForOrg(orgName).contract();
            return new String(contract.evaluateTransaction(function, args), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw translateException(exception);
        }
    }

    public void streamChaincodeEvents(final String orgName, final Consumer<ChaincodeEvent> consumer) {
        final FabricClientHolder client = clientForOrg(orgName);
        try (CloseableIterator<ChaincodeEvent> iterator = client.network().getChaincodeEvents(properties.getChaincodeName())) {
            while (iterator.hasNext()) {
                consumer.accept(iterator.next());
            }
        } catch (Exception exception) {
            throw translateException(exception);
        }
    }

    public NetworkHealthResponse getNetworkHealth() {
        final List<NetworkHealthResponse.PeerStatus> peers = new ArrayList<>();
        String status = "UP";
        String message = "All configured peer gateways are reachable";
        for (String org : properties.getProfiles().keySet()) {
            try {
                final FabricClientHolder holder = clientForOrg(org);
                peers.add(new NetworkHealthResponse.PeerStatus(org, holder.profile().peerEndpoint(), true));
            } catch (RuntimeException exception) {
                status = "DEGRADED";
                message = exception.getMessage();
                peers.add(new NetworkHealthResponse.PeerStatus(org, properties.getProfiles().get(org), false));
            }
        }
        return new NetworkHealthResponse(
            status,
            properties.getChannelName(),
            properties.getChaincodeName(),
            peers,
            java.time.Instant.now(),
            message
        );
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(FabricClientHolder::closeQuietly);
        clients.clear();
    }

    private FabricClientHolder clientForOrg(final String orgName) {
        return clients.computeIfAbsent(orgName, this::buildClient);
    }

    private FabricClientHolder buildClient(final String orgName) {
        try {
            final String profileLocation = properties.getProfiles().get(orgName);
            if (profileLocation == null) {
                throw new IllegalArgumentException("No Fabric profile configured for " + orgName);
            }
            final Path profilePath = resolvePath(profileLocation);
            final ConnectionProfile profile = objectMapper.readValue(profilePath.toFile(), ConnectionProfile.class);

            final Path tlsPath = resolvePath(profile.tlsCertPath());
            final Path certPath = resolvePath(profile.identityCertPath());
            final Path keyPath = resolvePrivateKeyPath(profile.privateKeyPath());

            final ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
                .trustManager(tlsPath.toFile())
                .build();

            final ManagedChannel channel = Grpc.newChannelBuilder(profile.peerEndpoint(), credentials)
                .overrideAuthority(profile.peerHostAlias())
                .build();

            final X509Identity identity = new X509Identity(profile.mspId(), readCertificate(certPath));
            final Gateway gateway = Gateway.newInstance()
                .identity(identity)
                .signer(Signers.newPrivateKeySigner(readPrivateKey(keyPath)))
                .connection(channel)
                .connect();

            final Network network = gateway.getNetwork(properties.getChannelName());
            final Contract contract = network.getContract(properties.getChaincodeName());
            return new FabricClientHolder(profile, channel, gateway, network, contract);
        } catch (Exception exception) {
            throw translateException(exception);
        }
    }

    private Path resolvePath(final String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(properties.getConfigBase()).resolve(configuredPath).normalize();
    }

    private Path resolvePrivateKeyPath(final String configuredPath) throws IOException {
        final Path path = resolvePath(configuredPath);
        if (Files.exists(path)) {
            return path;
        }
        final Path parent = path.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try (var stream = Files.list(parent)) {
                return stream.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No private key found in " + parent));
            }
        }
        throw new IllegalStateException("Private key path does not exist: " + path);
    }

    private X509Certificate readCertificate(final Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return Identities.readX509Certificate(reader);
        }
    }

    private PrivateKey readPrivateKey(final Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return Identities.readPrivateKey(reader);
        }
    }

    private RuntimeException translateException(final Exception exception) {
        return new IllegalStateException("Fabric gateway call failed: " + exception.getMessage(), exception);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConnectionProfile(
        String orgName,
        String mspId,
        String peerEndpoint,
        String peerHostAlias,
        String tlsCertPath,
        String identityCertPath,
        String privateKeyPath
    ) {
    }

    private record FabricClientHolder(
        ConnectionProfile profile,
        ManagedChannel channel,
        Gateway gateway,
        Network network,
        Contract contract
    ) {
        private void closeQuietly() {
            try {
                gateway.close();
            } catch (Exception ignored) {
            }
            channel.shutdownNow();
        }
    }
}
