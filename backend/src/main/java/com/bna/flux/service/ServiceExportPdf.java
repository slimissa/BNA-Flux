package com.bna.flux.service;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.EntreeAudit;
import com.bna.flux.entity.Transaction;
import com.bna.flux.repository.TransactionRepository;
import java.awt.Color;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.ClassPathResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service de génération de rapports PDF pour les transactions.
 *
 * @author Slim Issa — Projet Stage BNA
 * @since 2026-08-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceExportPdf {

    private final TransactionRepository transactionRepository;
    private final ServiceAudit serviceAudit;
    private final ResourceLoader resourceLoader;

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(26, 140, 78));
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
    private static final Font FONT_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(80, 80, 80));
    private static final Font FONT_VALUE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font FONT_CRITIQUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(231, 76, 60));
    private static final Font FONT_SURVEILLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(243, 156, 18));
    private static final Font FONT_ACCEPTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(46, 204, 113));
    private static final Font FONT_AUDIT = FontFactory.getFont(FontFactory.COURIER, 7, Color.DARK_GRAY);
    
    // Logo BNA encodé en Base64 (intégré directement pour éviter les problèmes de classpath)
    private static final String LOGO_BASE64 = "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";

    public byte[] genererPdf(Long transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction introuvable: " + transactionId));

        List<EntreeAudit> audits = serviceAudit.getPisteAudit(transactionId);
        var verification = serviceAudit.verifierChaine(transactionId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // === HEADER ===
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{3, 1});

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            try {
                byte[] logoBytes = java.util.Base64.getDecoder().decode(LOGO_BASE64);
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(120, 40);
                logo.setAlignment(Element.ALIGN_LEFT);
                logoCell.addElement(logo);
            } catch (Exception e) {
                logoCell.addElement(new Paragraph("BNA-FLUX", FONT_TITLE));
            }
            logoCell.addElement(new Paragraph("Rapport de Transaction", 
                FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY)));

            PdfPCell refCell = new PdfPCell();
            refCell.setBorder(Rectangle.NO_BORDER);
            refCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            refCell.addElement(new Paragraph("Réf: " + tx.getReferenceTransaction(), FONT_LABEL));
            refCell.addElement(new Paragraph("Date: " + tx.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), FONT_SMALL));

            header.addCell(logoCell);
            header.addCell(refCell);
            document.add(header);
            document.add(new Paragraph(" "));

            // === SEPARATOR ===
            PdfPTable sep = new PdfPTable(1);
            sep.setWidthPercentage(100);
            PdfPCell sepCell = new PdfPCell(new Phrase(" "));
            sepCell.setBorder(Rectangle.BOTTOM);
            sepCell.setBorderColor(new Color(26, 140, 78));
            sepCell.setBorderWidth(2f);
            sep.addCell(sepCell);
            document.add(sep);
            document.add(new Paragraph(" "));

            // === TRANSACTION DETAILS ===
            document.add(new Paragraph("Détails de la Transaction", FONT_HEADER));
            document.add(new Paragraph(" "));

            PdfPTable details = new PdfPTable(2);
            details.setWidthPercentage(100);
            details.setWidths(new float[]{1, 2});
            details.setSpacingBefore(5);

            addRow(details, "RIB Source", tx.getRibSource());
            addRow(details, "RIB Destination", tx.getRibDestination());
            addRow(details, "Montant", tx.getMontant() + " " + tx.getCodeDevise());
            addRow(details, "Type", tx.getTypeTransaction() != null ? tx.getTypeTransaction().name() : "-");
            addRow(details, "Canal", tx.getCanal() != null ? tx.getCanal().name() : "-");
            addRow(details, "Pays d'Origine", tx.getPaysOrigine() != null ? tx.getPaysOrigine() : "-");
            addRow(details, "Description", tx.getDescription() != null ? tx.getDescription() : "-");
            addRow(details, "Date Transaction", tx.getDateTransaction() != null ? 
                tx.getDateTransaction().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-");

            document.add(details);
            document.add(new Paragraph(" "));

            // === SCORE & STATUS ===
            PdfPTable scoreTable = new PdfPTable(2);
            scoreTable.setWidthPercentage(100);
            scoreTable.setWidths(new float[]{1, 1});

            PdfPCell scoreCell = new PdfPCell();
            scoreCell.setPadding(12);
            scoreCell.setBackgroundColor(new Color(245, 247, 245));
            scoreCell.addElement(new Paragraph("Score de Risque", FONT_LABEL));
            scoreCell.addElement(new Paragraph(tx.getScoreRisque() + "/100", 
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, getScoreColor(tx.getScoreRisque().intValue()))));

            PdfPCell statutCell = new PdfPCell();
            statutCell.setPadding(12);
            statutCell.setBackgroundColor(new Color(245, 247, 245));
            statutCell.addElement(new Paragraph("Statut", FONT_LABEL));
            String statut = tx.getStatut() != null ? tx.getStatut().name() : "ACCEPTE";
            statutCell.addElement(new Paragraph(statut, getStatutFont(statut)));

            scoreTable.addCell(scoreCell);
            scoreTable.addCell(statutCell);
            document.add(scoreTable);
            document.add(new Paragraph(" "));

            // === ALERTS ===
            // Alertes — disponibles via le repository dédié
            document.add(new Paragraph("Alertes — Consultez GET /api/alertes?transactionId=" + transactionId, FONT_SMALL));
            document.add(new Paragraph(" "));

            // === AUDIT CHAIN ===
            document.add(new Paragraph("Piste d'Audit SHA-256", FONT_HEADER));
            document.add(new Paragraph("Chaîne " + (verification.isChaineIntacte() ? "✅ INTACTE" : "❌ CORROMPUE"), 
                verification.isChaineIntacte() ? FONT_ACCEPTE : FONT_CRITIQUE));
            document.add(new Paragraph(" "));

            if (audits != null && !audits.isEmpty()) {
                PdfPTable auditTable = new PdfPTable(4);
                auditTable.setWidthPercentage(100);
                auditTable.setWidths(new float[]{1, 2, 2, 4});

                // Header
                addAuditHeader(auditTable, "Étape");
                addAuditHeader(auditTable, "Action");
                addAuditHeader(auditTable, "Horodatage");
                addAuditHeader(auditTable, "Hash SHA-256");

                for (EntreeAudit audit : audits) {
                    auditTable.addCell(new Phrase(audit.getEtape(), FONT_AUDIT));
                    auditTable.addCell(new Phrase(audit.getAction(), FONT_AUDIT));
                    auditTable.addCell(new Phrase(audit.getHorodatage() != null ? 
                        audit.getHorodatage().format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")) : "-", FONT_AUDIT));
                    auditTable.addCell(new Phrase(audit.getHashCourant() != null ? 
                        audit.getHashCourant().substring(0, 16) + "..." : "-", FONT_AUDIT));
                }

                document.add(auditTable);
            }

            // === FOOTER ===
            document.add(new Paragraph(" "));
            PdfPTable footer = new PdfPTable(1);
            footer.setWidthPercentage(100);
            PdfPCell footerCell = new PdfPCell(new Phrase("BNA-FLUX v1.0 — Banque Nationale Agricole © 2026 — Document généré le " + 
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")), FONT_SMALL));
            footerCell.setBorder(Rectangle.TOP);
            footerCell.setBorderColor(new Color(200, 200, 200));
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            footer.addCell(footerCell);
            document.add(footer);

            document.close();
            log.info("PDF généré pour la transaction {} — {} octets", transactionId, baos.size());

        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur de génération PDF", e);
        }

        return baos.toByteArray();
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_LABEL));
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(new Color(245, 247, 245));
        labelCell.setBorderColor(Color.WHITE);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", FONT_VALUE));
        valueCell.setPadding(6);
        valueCell.setBorderColor(Color.WHITE);
        table.addCell(valueCell);
    }

    private void addAuditHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE)));
        cell.setBackgroundColor(new Color(26, 140, 78));
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private Color getScoreColor(int score) {
        if (score >= 80) return new Color(231, 76, 60);
        if (score >= 50) return new Color(243, 156, 18);
        if (score >= 30) return new Color(241, 196, 15);
        return new Color(46, 204, 113);
    }

    private Font getStatutFont(String statut) {
        return switch (statut) {
            case "BLOQUE" -> FONT_CRITIQUE;
            case "SURVEILLE" -> FONT_SURVEILLE;
            default -> FONT_ACCEPTE;
        };
    }
}
