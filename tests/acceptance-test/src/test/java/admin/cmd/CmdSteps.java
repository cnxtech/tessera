package admin.cmd;

import com.quorum.tessera.config.AppType;
import com.quorum.tessera.config.Peer;
import com.quorum.tessera.config.ServerConfig;
import com.quorum.tessera.jaxrs.client.ClientFactory;
import com.quorum.tessera.test.Party;
import com.quorum.tessera.test.PartyHelper;
import cucumber.api.java8.En;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CmdSteps implements En {

    public CmdSteps() {

        final PartyHelper partyHelper = PartyHelper.create();
        final Party subjectNode = partyHelper.getParties().findAny().get();
        final ServerConfig adminConfig =
                subjectNode.getConfig().getServerConfigs().stream()
                        .filter(sc -> sc.getApp() == AppType.ADMIN)
                        .findFirst()
                        .get();
        final Client client = new ClientFactory().buildFrom(adminConfig);

        Given(
                "any node is running",
                () -> {
                    assertThat(
                                    Stream.of(subjectNode)
                                            .map(Party::getP2PUri)
                                            .map(client::target)
                                            .map(t -> t.path("upcheck"))
                                            .map(WebTarget::request)
                                            .map(Invocation.Builder::get)
                                            .allMatch(r -> r.getStatus() == 200))
                            .isTrue();
                });

        When(
                "admin user executes add peer",
                () -> {
                    int exitcode = Utils.addPeer(subjectNode, "bogus");
                    assertThat(exitcode).isEqualTo(0);
                });

        Then(
                "a peer is added to party",
                () -> {
                    Response response =
                            Stream.of(subjectNode)
                                    .map(Party::getAdminUri)
                                    .map(client::target)
                                    .map(t -> t.path("config"))
                                    .map(t -> t.path("peers"))
                                    .map(WebTarget::request)
                                    .map(Invocation.Builder::get)
                                    .findAny()
                                    .get();

                    assertThat(response.getStatus()).isEqualTo(200);
                    Peer[] peers = response.readEntity(Peer[].class);

                    List<String> urls = Stream.of(peers).map(Peer::getUrl).collect(Collectors.toList());

                    assertThat(urls).contains("bogus");
                });
    }
}
