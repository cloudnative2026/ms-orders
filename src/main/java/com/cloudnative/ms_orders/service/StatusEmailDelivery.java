package com.cloudnative.ms_orders.service;
import com.cloudnative.ms_orders.repository.StatusEmailRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Service
public class StatusEmailDelivery {
    private static final Logger log=LoggerFactory.getLogger(StatusEmailDelivery.class);
    private final StatusEmailRepository emails;
    private final JavaMailSender sender;
    private final String from;
    public StatusEmailDelivery(StatusEmailRepository emails, JavaMailSender sender,
            @Value("${app.email.from:pedidos360@example.test}") String from) {
        this.emails=emails;this.sender=sender;this.from=from;
    }
    @Transactional
    public void deliver(String id) {
        var event=emails.lockById(id).orElse(null);
        if(event==null || !"PENDING".equals(event.getState()) || event.getNextAttemptAt().isAfter(LocalDateTime.now())) return;
        var mail=new SimpleMailMessage();
        mail.setFrom(from);mail.setTo(event.getRecipient());
        mail.setSubject("Pedido #"+event.getOrderId()+": "+label(event.getNewStatus()));
        mail.setText("Hola,\n\nTu pedido #"+event.getOrderId()+" cambió de estado.\n\nEstado anterior: "
            +label(event.getPreviousStatus())+"\nNuevo estado: "+label(event.getNewStatus())
            +"\nFecha del cambio (UTC): "+event.getOccurredAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            +"\n\nPuedes consultar el detalle en la sección Pedidos.\n\nPedidos360");
        event.setAttempts(event.getAttempts()+1);
        try {
            sender.send(mail);
            event.setState("SENT");event.setSentAt(LocalDateTime.now());
            log.info("Correo del evento {} aceptado por SMTP", id);
        } catch(MailException error) {
            event.setState(event.getAttempts()>=5 ? "FAILED" : "PENDING");
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(30L*event.getAttempts()));
            log.warn("Envio del evento {} fallido; intento {} de 5",id,event.getAttempts());
        }
    }
    private String label(String status) {
        return switch(status) {
            case "CREADO" -> "Creado"; case "ACEPTADO" -> "Aceptado";
            case "EN_PREPARACION" -> "En preparación"; case "DESPACHADO" -> "Despachado";
            case "ENTREGADO" -> "Entregado"; case "CANCELADO" -> "Cancelado"; default -> status;
        };
    }
}
