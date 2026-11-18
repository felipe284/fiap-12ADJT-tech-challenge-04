package br.com.fiap.feedback.infra;

import br.com.fiap.feedback.domain.Avaliacao;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.identity.DefaultAzureCredentialBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class AvaliacaoRepository {

    @ConfigProperty(name = "cosmos.endpoint", defaultValue = "")
    String endpoint;

    @ConfigProperty(name = "cosmos.database", defaultValue = "feedback")
    String database;

    @ConfigProperty(name = "cosmos.container", defaultValue = "avaliacoes")
    String container;

    private CosmosContainer cosmosContainer;

    private synchronized CosmosContainer container() {
        if (cosmosContainer == null) {
            CosmosClient client = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .gatewayMode()
                    .buildClient();
            cosmosContainer = client.getDatabase(database).getContainer(container);
        }
        return cosmosContainer;
    }

    public void salvar(Avaliacao avaliacao) {
        container().createItem(avaliacao, new PartitionKey(avaliacao.dia), null);
    }

    public List<Avaliacao> buscarDesde(String dataIso) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.dataEnvio >= @inicio ORDER BY c.dataEnvio",
                new SqlParameter("@inicio", dataIso));
        return container().queryItems(query, new CosmosQueryRequestOptions(), Avaliacao.class)
                .stream().toList();
    }
}
