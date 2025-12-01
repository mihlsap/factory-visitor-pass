package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.services.PdfGeneratorService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfGeneratorServiceImpl implements PdfGeneratorService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public byte[] generatePassPdf(User user, List<UserTrainingDto> validTrainings) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Nagłówek
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("PRZEPUSTKA ZAKŁADOWA", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ")); // Odstęp

            // 2. Tabela Główna (Zdjęcie + Dane)
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 2}); // Kolumna zdjęcia węższa

            // A. Zdjęcie Użytkownika
            PdfPCell photoCell = new PdfPCell();
            photoCell.setBorder(Rectangle.NO_BORDER);
            try {
                if (user.getPhotoUrl() != null) {
                    Path photoPath = Paths.get(uploadDir).resolve(user.getPhotoUrl());
                    Image userImage = Image.getInstance(photoPath.toString());
                    userImage.scaleToFit(120, 150);
                    photoCell.addElement(userImage);
                } else {
                    photoCell.addElement(new Paragraph("[BRAK ZDJĘCIA]"));
                }
            } catch (Exception e) {
                photoCell.addElement(new Paragraph("[BŁĄD ZDJĘCIA]"));
            }
            headerTable.addCell(photoCell);

            // B. Dane Użytkownika
            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(Rectangle.NO_BORDER);
            infoCell.addElement(new Paragraph("Imię i Nazwisko: " + user.getName() + " " + user.getSurname()));
            infoCell.addElement(new Paragraph("Firma: " + (user.getCompanyName() != null ? user.getCompanyName() : "Pracownik Wewnętrzny")));
            infoCell.addElement(new Paragraph("Email: " + user.getEmail()));

            // Kod QR
            try {
                // Link do weryfikacji (na razie localhost, potem domena)
                String qrContent = "http://localhost:8080/verify/" + user.getId();
                Image qrImage = generateQrCodeImage(qrContent);
                qrImage.scaleToFit(100, 100);
                infoCell.addElement(new Paragraph(" "));
                infoCell.addElement(qrImage);
            } catch (Exception e) {
                infoCell.addElement(new Paragraph("[QR ERROR]"));
            }

            headerTable.addCell(infoCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("-----------------------------------------------------------------------------"));
            document.add(new Paragraph(" "));

            // 3. Tabela Uprawnień
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            PdfPTable trainingTable = new PdfPTable(2);
            trainingTable.setWidthPercentage(100);

            trainingTable.addCell(new Paragraph("Nazwa Szkolenia", tableHeaderFont));
            trainingTable.addCell(new Paragraph("Ważne do", tableHeaderFont));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (UserTrainingDto training : validTrainings) {
                trainingTable.addCell(training.getTraining().getTitle());
                trainingTable.addCell(training.getValidUntil().format(formatter));
            }

            document.add(trainingTable);

            // 4. Stopka
            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Dokument wygenerowany automatycznie przez system FVPS.", FontFactory.getFont(FontFactory.HELVETICA, 10));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Błąd generowania PDF", e);
        }
    }

    private Image generateQrCodeImage(String text) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

        // Konwersja na obraz iStream
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        return Image.getInstance(pngOutputStream.toByteArray());
    }
}