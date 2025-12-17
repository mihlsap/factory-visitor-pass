package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.training.UserTrainingDto;
import com.fvps.backend.domain.entities.User;
import com.fvps.backend.services.PdfGeneratorService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Service
public class PdfGeneratorServiceImpl implements PdfGeneratorService {

    private final Clock clock;
    private final MessageSource messageSource;
    private final Locale defaultLocale;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li><b>Libraries:</b> Uses <i>OpenPDF</i> ({@code com.lowagie}) for layout and <i>ZXing</i> for QR code generation.</li>
     * <li><b>Visual Logic:</b> Security levels are colour-coded (Level 1=Green to Level 4=Red) for quick visual identification by security guards.</li>
     * <li><b>Resilience:</b> If the user's photo or the QR code fails to generate/load, the method catches the exception
     * locally and renders a placeholder text (e.g. "[NO PHOTO]") instead of failing the entire document generation.</li>
     * <li><b>QR Content:</b> The QR code embeds the User's UUID. Security personnel scan this to verify the *current*
     * status in the system, preventing use of revoked (printed) passes.</li>
     * </ul>
     * </p>
     */
    @Override
    public byte[] generatePassPdf(User user, List<UserTrainingDto> validTrainings) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            String titleText = messageSource.getMessage("pdf.pass.title", null, "FACTORY VISITOR PASS", defaultLocale);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph(titleText, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 2});

            PdfPCell photoCell = new PdfPCell();
            photoCell.setBorder(Rectangle.NO_BORDER);
            try {
                if (user.getPhotoUrl() != null) {
                    Path photoPath = Paths.get(uploadDir).resolve(user.getPhotoUrl());
                    Image userImage = Image.getInstance(photoPath.toString());
                    userImage.scaleToFit(120, 150);
                    photoCell.addElement(userImage);
                } else {
                    String noPhotoText = messageSource.getMessage("pdf.pass.no_photo", null, "[NO PHOTO]", defaultLocale);
                    photoCell.addElement(new Paragraph(noPhotoText));
                }
            } catch (Exception e) {
                String photoErrorText = messageSource.getMessage("pdf.pass.photo_error", null, "[PHOTO ERROR]", defaultLocale);
                photoCell.addElement(new Paragraph(photoErrorText));
            }
            headerTable.addCell(photoCell);

            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(Rectangle.NO_BORDER);

            String labelName = messageSource.getMessage("pdf.pass.label.name", null, "Name:", defaultLocale);
            String labelCompany = messageSource.getMessage("pdf.pass.label.company", null, "Company:", defaultLocale);
            String labelPhone = messageSource.getMessage("pdf.pass.label.phone", null, "Phone:", defaultLocale);
            String labelEmail = messageSource.getMessage("pdf.pass.label.email", null, "Email:", defaultLocale);
            String internalEmployee = messageSource.getMessage("pdf.pass.label.internal_employee", null, "Internal Employee", defaultLocale);

            infoCell.addElement(new Paragraph(labelName + " " + user.getName() + " " + user.getSurname()));
            infoCell.addElement(new Paragraph(labelCompany + " " + (user.getCompanyName() != null ? user.getCompanyName() : internalEmployee)));
            infoCell.addElement(new Paragraph(labelPhone + " " + (user.getPhoneNumber() != null ? user.getPhoneNumber() : "-")));
            infoCell.addElement(new Paragraph(labelEmail + " " + user.getEmail()));

            Color levelColor = switch (user.getClearanceLevel()) {
                case 1 -> Color.GREEN;
                case 2 -> Color.YELLOW;
                case 3 -> Color.ORANGE;
                case 4 -> Color.RED;
                default -> Color.BLACK;
            };

            String labelClearance = messageSource.getMessage("pdf.pass.label.clearance_level", null, "SECURITY CLEARANCE: LEVEL", defaultLocale);
            Font levelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, levelColor);
            Paragraph levelPara = new Paragraph(
                    labelClearance + " " + user.getClearanceLevel(),
                    levelFont
            );
            infoCell.addElement(levelPara);

            try {
                String qrContent = user.getId().toString();
                Image qrImage = generateQrCodeImage(qrContent);
                qrImage.scaleToFit(100, 100);
                infoCell.addElement(new Paragraph(" "));
                infoCell.addElement(qrImage);
            } catch (Exception e) {
                String qrErrorText = messageSource.getMessage("pdf.pass.qr_error", null, "[QR ERROR]", defaultLocale);
                infoCell.addElement(new Paragraph(qrErrorText));
            }

            headerTable.addCell(infoCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            PdfPTable trainingTable = new PdfPTable(2);
            trainingTable.setWidthPercentage(100);

            String colTraining = messageSource.getMessage("pdf.pass.table.training", null, "Training Title", defaultLocale);
            String colValidUntil = messageSource.getMessage("pdf.pass.table.valid_until", null, "Valid Until", defaultLocale);

            trainingTable.addCell(new Paragraph(colTraining, tableHeaderFont));
            trainingTable.addCell(new Paragraph(colValidUntil, tableHeaderFont));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (UserTrainingDto training : validTrainings) {
                trainingTable.addCell(training.getTraining().getTitle());
                trainingTable.addCell(training.getValidUntil().format(formatter));
            }

            document.add(trainingTable);

            document.add(new Paragraph(" "));

            String footerText = messageSource.getMessage("pdf.pass.footer.generated_by", null, "Document generated automatically by FVPS system.", defaultLocale);
            Paragraph footer = new Paragraph(footerText, FontFactory.getFont(FontFactory.HELVETICA, 10));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);
            document.add(new Paragraph(" "));

            Font disclaimerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);
            String disclaimerText = messageSource.getMessage("pdf.pass.footer.disclaimer", null, "Disclaimer: Access rights are verified dynamically via QR Code.", defaultLocale);
            Paragraph disclaimer = new Paragraph(disclaimerText, disclaimerFont);
            disclaimer.setAlignment(Element.ALIGN_CENTER);
            document.add(disclaimer);

            String generatedOnLabel = messageSource.getMessage("pdf.pass.footer.generated_on", null, "Generated on:", defaultLocale);
            Paragraph timestamp = new Paragraph(
                    generatedOnLabel + " " + LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    disclaimerFont
            );
            timestamp.setAlignment(Element.ALIGN_CENTER);
            document.add(timestamp);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF pass", e);
        }
    }

    /**
     * Generates a QR code image from the provided text.
     *
     * @param text the content to encode in the QR (e.g. User UUID).
     * @return an iText {@link Image} object ready to be added to the PDF.
     * @throws Exception if ZXing fails to encode the barcode.
     */
    private Image generateQrCodeImage(String text) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        return Image.getInstance(pngOutputStream.toByteArray());
    }
}