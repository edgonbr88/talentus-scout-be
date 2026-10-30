package com.talentus.scout.service.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AthleteCvPdfService {

    // Paleta intencional deportiva Talentus Scout
    private static final Color COLOR_PITCH_DARK = new Color(15, 23, 42);     // #0F172A
    private static final Color COLOR_VOLT_EMERALD = new Color(16, 185, 129);  // #10B981
    private static final Color COLOR_GOLD_AMBER = new Color(217, 119, 6);     // #D97706
    private static final Color COLOR_ICE_BG = new Color(248, 250, 252);       // #F8FAFC
    private static final Color COLOR_SLATE_HEADER = new Color(241, 245, 249); // #F1F5F9
    private static final Color COLOR_BORDER = new Color(226, 232, 240);       // #E2E8F0
    private static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);   // #64748B
    private static final Color COLOR_TEXT_DARK = new Color(30, 41, 59);       // #1E293B

    private final AthleteRepository athleteRepository;
    private final AthleteBadgeRepository athleteBadgeRepository;
    private final String frontendBaseUrl;

    public AthleteCvPdfService(
            AthleteRepository athleteRepository,
            AthleteBadgeRepository athleteBadgeRepository,
            @Value("${talentus.frontend.base-url:https://talentus.app}") String frontendBaseUrl
    ) {
        this.athleteRepository = athleteRepository;
        this.athleteBadgeRepository = athleteBadgeRepository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional(readOnly = true)
    public byte[] generateAthleteCvPdf(UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + athleteId));

        List<AthleteBadge> approvedBadges = athleteBadgeRepository.findByAthleteIdAndStatus(athlete.getId(), BadgeStatus.APPROVED);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 28, 28, 24, 24);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Cabecera Institucional
            addHeader(document, athlete);

            // 2. Bloque Hero: Identidad del Atleta + Código QR Interactivo
            addHeroSection(document, athlete);

            // 3. Ficha Técnica y Antropometría (Grid)
            addTechnicalDataGrid(document, athlete);

            // 4. Insignias de Confianza y Certificaciones Oficiales
            addBadgesSection(document, athlete, approvedBadges);

            // 5. Perfil Táctico & Trayectoria
            addBioSection(document, athlete);

            // 6. Pie de Página Legal y Protección Integral del Menor (FIFA Guardians / Normativa Aplicable)
            addLegalFooter(document, athlete);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar el Curriculum Vitae en PDF para el atleta " + athleteId, e);
        }
    }

    private void addHeader(Document document, Athlete athlete) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{75f, 25f});
        headerTable.setSpacingAfter(10f);

        // Celda Izquierda: Logo y Título
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBackgroundColor(COLOR_PITCH_DARK);
        leftCell.setPadding(9f);
        leftCell.setBorder(Rectangle.NO_BORDER);

        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12.5f, Color.WHITE);
        Font subBrandFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, new Color(148, 163, 184));

        Paragraph titlePara = new Paragraph("TALENTUS SCOUT • FICHA TÉCNICA OFICIAL", brandFont);
        Paragraph subtitlePara = new Paragraph("Curriculum Vitae Deportivo Certificado • Categorías Formativas Juveniles", subBrandFont);
        leftCell.addElement(titlePara);
        leftCell.addElement(subtitlePara);

        // Celda Derecha: Badge de Verificación
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBackgroundColor(COLOR_PITCH_DARK);
        rightCell.setPadding(9f);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_GOLD_AMBER);
        Font dateFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(148, 163, 184));

        Paragraph badgePara = new Paragraph("★ DOCUMENTO VERIFICADO", badgeFont);
        badgePara.setAlignment(Element.ALIGN_RIGHT);
        Paragraph datePara = new Paragraph("Emisión: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), dateFont);
        datePara.setAlignment(Element.ALIGN_RIGHT);

        rightCell.addElement(badgePara);
        rightCell.addElement(datePara);

        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addHeroSection(Document document, Athlete athlete) throws Exception {
        PdfPTable heroTable = new PdfPTable(2);
        heroTable.setWidthPercentage(100);
        heroTable.setWidths(new float[]{70f, 30f});
        heroTable.setSpacingAfter(10f);

        // Celda de Identidad
        PdfPCell identityCell = new PdfPCell();
        identityCell.setBackgroundColor(COLOR_ICE_BG);
        identityCell.setPadding(12f);
        identityCell.setBorderColor(COLOR_BORDER);
        identityCell.setBorderWidth(1f);

        // Nombre del Atleta
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17f, COLOR_PITCH_DARK);
        Paragraph namePara = new Paragraph(athlete.getFirstName() + " " + athlete.getLastName(), nameFont);
        namePara.setSpacingAfter(4f);
        identityCell.addElement(namePara);

        // Subtítulo con Posición Principal y Categoría
        int birthYear = athlete.getBirthDate() != null ? athlete.getBirthDate().getYear() : 0;
        int age = athlete.getBirthDate() != null ? Period.between(athlete.getBirthDate(), LocalDate.now()).getYears() : 0;

        Font posFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, COLOR_VOLT_EMERALD);
        String categoryText = birthYear > 0 ? "Categoría Sub-" + (LocalDate.now().getYear() - birthYear) + " (Año " + birthYear + ")" : "Categoría Formativa";
        Paragraph posPara = new Paragraph(athlete.getPrimaryPosition().toUpperCase() + " • " + categoryText + " • " + age + " AÑOS", posFont);
        posPara.setSpacingAfter(6f);
        identityCell.addElement(posPara);

        // Club Actual
        Font clubFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, COLOR_TEXT_DARK);
        String clubName = athlete.getCurrentClub() != null
                ? athlete.getCurrentClub().getName() + " (" + athlete.getCurrentClub().getCity() + ", " + athlete.getCurrentClub().getCountry() + ")"
                : "Agente Libre / Formación Independiente";
        Paragraph clubPara = new Paragraph("Club Actual: " + clubName, clubFont);
        clubPara.setSpacingAfter(3f);
        identityCell.addElement(clubPara);

        // Licencia Federativa
        if (athlete.getFederationLicenseNumber() != null && !athlete.getFederationLicenseNumber().isBlank()) {
            Font licFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, COLOR_TEXT_MUTED);
            Paragraph licPara = new Paragraph("Ficha Federativa Nº: " + athlete.getFederationLicenseNumber(), licFont);
            identityCell.addElement(licPara);
        }

        // Celda de Código QR Interactivo
        PdfPCell qrCell = new PdfPCell();
        qrCell.setBackgroundColor(COLOR_ICE_BG);
        qrCell.setPadding(8f);
        qrCell.setBorderColor(COLOR_BORDER);
        qrCell.setBorderWidth(1f);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        String profileUrl = frontendBaseUrl + "/atleta/" + athlete.getSlug();
        byte[] qrBytes = generateQrCodeBytes(profileUrl, 160, 160);
        Image qrImage = Image.getInstance(qrBytes);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        qrImage.scaleAbsolute(78f, 78f);
        qrCell.addElement(qrImage);

        Font qrCaptionFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, COLOR_TEXT_MUTED);
        Paragraph qrCaption = new Paragraph("Escanear para ver Perfil y Videos de Jugadas", qrCaptionFont);
        qrCaption.setAlignment(Element.ALIGN_CENTER);
        qrCaption.setSpacingBefore(3f);
        qrCell.addElement(qrCaption);

        heroTable.addCell(identityCell);
        heroTable.addCell(qrCell);
        document.add(heroTable);
    }

    private void addTechnicalDataGrid(Document document, Athlete athlete) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25f, 25f, 25f, 25f});
        table.setSpacingAfter(10f);

        // Header de Sección
        PdfPCell sectionHeader = new PdfPCell(new Phrase("1. BIOTIPO & ESPECIFICACIONES TÉCNICAS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_PITCH_DARK)));
        sectionHeader.setColspan(4);
        sectionHeader.setBackgroundColor(COLOR_SLATE_HEADER);
        sectionHeader.setPadding(5f);
        sectionHeader.setBorderColor(COLOR_BORDER);
        table.addCell(sectionHeader);

        // Datos en pares Label / Value
        addMetricCell(table, "Posición Principal", athlete.getPrimaryPosition());
        addMetricCell(table, "Posición Secundaria", athlete.getSecondaryPosition() != null ? athlete.getSecondaryPosition() : "Ninguna");
        addMetricCell(table, "Pierna Hábil", athlete.getPreferredFoot() != null ? athlete.getPreferredFoot() : "No especificada");
        addMetricCell(table, "Nacionalidad", athlete.getNationality() != null ? athlete.getNationality() : "Venezolana");

        String heightStr = athlete.getHeightCm() != null ? athlete.getHeightCm() + " cm" : "Pendiente de medición";
        String weightStr = athlete.getWeightKg() != null ? athlete.getWeightKg() + " kg" : "Pendiente de medición";
        String birthDateStr = athlete.getBirthDate() != null ? athlete.getBirthDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/D";
        String secondaryPassports = athlete.getSecondaryPassports() != null && !athlete.getSecondaryPassports().isBlank()
                ? athlete.getSecondaryPassports()
                : "Sin pasaporte comunitario";

        addMetricCell(table, "Estatura Oficial", heightStr);
        addMetricCell(table, "Peso Corporal", weightStr);
        addMetricCell(table, "Fecha de Nacimiento", birthDateStr);
        addMetricCell(table, "Pasaportes / Comunitario", secondaryPassports);

        document.add(table);
    }

    private void addMetricCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(5.5f);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 7f, COLOR_TEXT_MUTED);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_TEXT_DARK);

        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));

        table.addCell(cell);
    }

    private void addBadgesSection(Document document, Athlete athlete, List<AthleteBadge> approvedBadges) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        PdfPCell sectionHeader = new PdfPCell(new Phrase("2. INSIGNIAS DE CONFIANZA & SELLOS DE VERIFICACIÓN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_PITCH_DARK)));
        sectionHeader.setBackgroundColor(COLOR_SLATE_HEADER);
        sectionHeader.setPadding(5f);
        sectionHeader.setBorderColor(COLOR_BORDER);
        table.addCell(sectionHeader);

        PdfPCell contentCell = new PdfPCell();
        contentCell.setBackgroundColor(Color.WHITE);
        contentCell.setBorderColor(COLOR_BORDER);
        contentCell.setPadding(7f);

        Font badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_PITCH_DARK);
        Font subBadgeFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_TEXT_MUTED);

        boolean hasBadges = false;

        if (approvedBadges != null && !approvedBadges.isEmpty()) {
            for (AthleteBadge b : approvedBadges) {
                String orgName = b.getOrganization() != null ? b.getOrganization().getName() : "Organización Oficial";
                Paragraph p = new Paragraph("✔ " + resolveBadgeTitle(b.getBadgeType(), orgName), badgeFont);
                contentCell.addElement(p);
                hasBadges = true;
            }
        }

        // Si no tiene badges aprobados en BD, verificar si tiene licencia federativa o club
        if (!hasBadges) {
            if (athlete.getFederationLicenseNumber() != null && !athlete.getFederationLicenseNumber().isBlank()) {
                Paragraph p1 = new Paragraph("✔ Licencia Federada Oficial — Reg. " + athlete.getFederationLicenseNumber(), badgeFont);
                contentCell.addElement(p1);
                hasBadges = true;
            }
            if (athlete.getCurrentClub() != null) {
                Paragraph p2 = new Paragraph("✔ Ficha de Club Activa — " + athlete.getCurrentClub().getName(), badgeFont);
                contentCell.addElement(p2);
                hasBadges = true;
            }
        }

        if (!hasBadges) {
            Paragraph emptyPara = new Paragraph("Perfil en proceso de certificación federativa y acreditación oficial.", subBadgeFont);
            contentCell.addElement(emptyPara);
        }

        table.addCell(contentCell);
        document.add(table);
    }

    private void addBioSection(Document document, Athlete athlete) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);

        PdfPCell sectionHeader = new PdfPCell(new Phrase("3. PERFIL TÁCTICO & OBSERVACIONES DEPORTIVAS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_PITCH_DARK)));
        sectionHeader.setBackgroundColor(COLOR_SLATE_HEADER);
        sectionHeader.setPadding(5f);
        sectionHeader.setBorderColor(COLOR_BORDER);
        table.addCell(sectionHeader);

        PdfPCell contentCell = new PdfPCell();
        contentCell.setBackgroundColor(Color.WHITE);
        contentCell.setBorderColor(COLOR_BORDER);
        contentCell.setPadding(7f);

        Font bioFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, COLOR_TEXT_DARK);
        String bioText = (athlete.getBio() != null && !athlete.getBio().isBlank())
                ? athlete.getBio()
                : "Atleta en formación deportiva con proyección competitiva y seguimiento audiovisual registrado en la plataforma Talentus Scout.";

        Paragraph bioPara = new Paragraph(bioText, bioFont);
        bioPara.setLeading(11f);
        contentCell.addElement(bioPara);

        table.addCell(contentCell);
        document.add(table);
    }

    private void addLegalFooter(Document document, Athlete athlete) throws DocumentException {
        PdfPTable footerTable = new PdfPTable(1);
        footerTable.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_ICE_BG);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(6f);

        Font legalTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, COLOR_PITCH_DARK);
        Font legalBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, COLOR_TEXT_MUTED);

        Paragraph titlePara = new Paragraph("SALVAGUARDA LEGAL Y PROTECCIÓN INTEGRAL DEL MENOR (FIFA GUARDIANS / NORMATIVA APLICABLE):", legalTitleFont);
        titlePara.setSpacingAfter(2f);

        String legalText = "Este documento es expedido exclusivamente con fines deportivos, de visoría y convocatorias formativas. "
                + "Se prohíbe expresamente la difusión no autorizada de datos de contacto de niños y adolescentes. "
                + "Cualquier comunicación o acercamiento formal debe canalizarse a través del representante legal acreditado y club afiliado mediante los canales seguros de Talentus Scout.\n"
                + "Enlace oficial al expediente: " + frontendBaseUrl + "/atleta/" + athlete.getSlug();

        Paragraph bodyPara = new Paragraph(legalText, legalBodyFont);
        bodyPara.setLeading(8.5f);

        cell.addElement(titlePara);
        cell.addElement(bodyPara);

        footerTable.addCell(cell);
        document.add(footerTable);
    }

    private String resolveBadgeTitle(BadgeType type, String orgName) {
        if (type == null) return "Insignia Verificada";
        switch (type) {
            case FEDERATION_LICENSE:
                return "Licencia Federada Oficial (" + orgName + ")";
            case CLUB_AFFILIATION:
                return "Ficha Activa de Club (" + orgName + ")";
            case MEDICAL_ANTHRO:
                return "Certificación Médica y Antropométrica Oficial";
            case SCOUT_ENDORSEMENT:
                return "Respaldo y Validación de Scout Acreditado";
            default:
                return "Certificación Oficial Verificada";
        }
    }

    private byte[] generateQrCodeBytes(String text, int width, int height) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        return pngOutputStream.toByteArray();
    }
}
