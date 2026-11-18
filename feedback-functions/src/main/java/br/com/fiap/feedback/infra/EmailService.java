package br.com.fiap.feedback.infra;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailService {

    @ConfigProperty(name = "acs.connection-string", defaultValue = "")
    String connectionString;

    @ConfigProperty(name = "email.sender", defaultValue = "")
    String sender;

    @ConfigProperty(name = "email.admin", defaultValue = "")
    String admin;

    private EmailClient client;

    private synchronized EmailClient getClient() {
        if (client == null) {
            client = new EmailClientBuilder().connectionString(connectionString).buildClient();
        }
        return client;
    }

    public void enviarParaAdmin(String assunto, String texto) {
        EmailMessage message = new EmailMessage()
                .setSenderAddress(sender)
                .setToRecipients(admin)
                .setSubject(assunto)
                .setBodyPlainText(texto);
        getClient().beginSend(message).getFinalResult();
    }
}
