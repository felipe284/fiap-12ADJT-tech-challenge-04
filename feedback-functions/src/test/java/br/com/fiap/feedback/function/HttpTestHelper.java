package br.com.fiap.feedback.function;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpTestHelper {

    public static void prepararRequisicao(HttpRequestMessage<Optional<String>> request, String corpo) {
        when(request.getBody()).thenReturn(Optional.ofNullable(corpo));
        when(request.createResponseBuilder(any(HttpStatus.class))).thenAnswer(invocation -> {
            HttpStatus status = invocation.getArgument(0);
            HttpResponseMessage resposta = mock(HttpResponseMessage.class);
            when(resposta.getStatus()).thenReturn(status);
            HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(resposta);
            return builder;
        });
    }
}
