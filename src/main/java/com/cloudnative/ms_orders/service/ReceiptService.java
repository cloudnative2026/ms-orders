package com.cloudnative.ms_orders.service;

import com.cloudnative.ms_orders.dto.*;
import com.cloudnative.ms_orders.model.Receipt;
import com.cloudnative.ms_orders.repository.ReceiptRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReceiptService {
    private final OrderService orders;
    private final ReceiptRepository receipts;
    private final ObjectMapper mapper;
    public ReceiptService(OrderService orders, ReceiptRepository receipts, ObjectMapper mapper) {
        this.orders = orders; this.receipts = receipts; this.mapper = mapper;
    }

    @Transactional
    public ReceiptResponseDTO request(long id, JwtAuthenticationToken auth) {
        var order = orders.findById(id, auth);
        var receipt = receipts.findById(id).orElseGet(() -> {
            var r = new Receipt();
            r.setOrderId(id);
            r.setSnapshot(mapper.writeValueAsString(order));
            r.setRequestedAt(LocalDateTime.now());
            return receipts.saveAndFlush(r);
        });
        return response(receipt);
    }

    @Transactional(readOnly = true)
    public ReceiptResponseDTO get(long id, JwtAuthenticationToken auth) {
        orders.findById(id, auth); // Apply the same ownership checks as the order API.
        return receipts.findById(id).map(this::response).orElseGet(() ->
            new ReceiptResponseDTO(id, "NOT_REQUESTED", null, null, null, null));
    }

    @Transactional
    public void generate(long id) {
        var receipt = receipts.lockByOrderId(id).orElse(null);
        if (receipt == null || receipt.getGeneratedAt() != null) return;
        var order = mapper.readValue(receipt.getSnapshot(), OrderResponseDTO.class);
        var generated = LocalDateTime.now();
        receipt.setHtml(render(order, receipt.getRequestedAt(), generated));
        receipt.setGeneratedAt(generated);
    }

    private ReceiptResponseDTO response(Receipt r) {
        boolean ready = r.getGeneratedAt() != null;
        return new ReceiptResponseDTO(r.getOrderId(), ready ? "READY" : "PENDING",
            ready ? "B-" + r.getOrderId() : null, r.getRequestedAt(), r.getGeneratedAt(), r.getHtml());
    }

    private String render(OrderResponseDTO order, LocalDateTime requested, LocalDateTime generated) {
        var rows = new StringBuilder();
        for (var item : order.items()) {
            var name = item.product() == null ? "Producto #" + item.productId() : item.product().name();
            rows.append("<tr><td>").append(HtmlUtils.htmlEscape(name)).append("</td><td>")
                .append(item.quantity()).append("</td><td>$").append(item.unitPrice().toPlainString())
                .append("</td><td>$").append(item.unitPrice().multiply(java.math.BigDecimal.valueOf(item.quantity())).toPlainString())
                .append("</td></tr>");
        }
        var dates = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL"));
        return """
            <!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Boleta B-%d</title><style>
            *{box-sizing:border-box}body{font:15px system-ui,sans-serif;color:#172332;background:#f4f6f8;margin:0;padding:28px}
            main{max-width:760px;margin:auto;background:white;padding:36px;border:1px solid #dce3ea;border-radius:16px}
            header{border-bottom:2px solid #172332;padding-bottom:20px}h1{font-size:28px;margin:8px 0}
            .brand{font-weight:700;letter-spacing:.12em;color:#397863}p{line-height:1.6}small{color:#526171}
            table{width:100%%;border-collapse:collapse;margin:28px 0}th,td{text-align:right;padding:12px 6px;border-bottom:1px solid #e4e9ef}
            th:first-child,td:first-child{text-align:left}.total{text-align:right;font-size:23px;font-weight:700}
            footer{margin-top:32px;border-top:1px solid #e4e9ef;padding-top:18px;color:#526171}
            @media print{body{background:white;padding:0}main{border:0;border-radius:0;padding:12px}tr{break-inside:avoid}}
            </style></head><body><main><header><span class="brand">PEDIDOS360</span>
            <h1>Boleta B-%d</h1><small>Comprobante interno del pedido</small></header>
            <p><strong>Pedido:</strong> #%d<br><strong>Cliente:</strong> %d<br>
            <strong>Fecha del pedido:</strong> %s<br><strong>Emisión:</strong> %s</p>
            <table><thead><tr><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Subtotal</th></tr></thead>
            <tbody>%s</tbody></table><p class="total">Total: $%s</p>
            <footer><small>Detalle y precios registrados al solicitar la boleta (%s).
            Este comprobante no acredita el pago ni reemplaza un documento tributario.</small></footer></main></body></html>
            """.formatted(order.id(), order.id(), order.id(), order.customerId(), dates.format(order.createdAt()),
                dates.format(generated), rows, order.totalAmount().toPlainString(), dates.format(requested));
    }
}
