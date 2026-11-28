package inscricao.messaging;

import com.rabbitmq.client.Return;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQEventPublisherTest {

    @Test
    void deveAceitarPublicacaoQuandoNaoHouverRetornoDoBroker() {
        assertDoesNotThrow(() -> RabbitMQEventPublisher.validarRetornoRoteamento(
                null,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.RK_INSCRICAO_CRIADA
        ));
    }

    @Test
    void deveLancarIOExceptionQuandoMensagemMandatoryNaoForRoteavel() {
        Return retorno = new Return(
                312,
                "NO_ROUTE",
                RabbitMQConfig.EXCHANGE_EVENTOS,
                "evento.sem.fila",
                null,
                new byte[0]
        );

        IOException ex = assertThrows(
                IOException.class,
                () -> RabbitMQEventPublisher.validarRetornoRoteamento(
                        retorno,
                        RabbitMQConfig.EXCHANGE_EVENTOS,
                        "evento.sem.fila"
                )
        );
        assertTrue(ex.getMessage().contains("NO_ROUTE"));
        assertTrue(ex.getMessage().contains("evento.sem.fila"));
    }
}
