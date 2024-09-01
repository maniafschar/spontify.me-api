package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class MarketingCards {

	@Test
	public void createPdf() throws Exception {
		// given
		final JsonNode addresses = new ObjectMapper()
				.readTree(getClass().getResourceAsStream("/json/marketingAddresses.json"));
		final PDF pdf = new PDF(addresses);

		// when
		final byte[] data = pdf.toBytes();

		// then
		assertNotNull(data);
		try (final OutputStream out = new FileOutputStream("test.pdf")) {
			out.write(data);
		}
	}

	private class PDF {
		private final PDDocument document = new PDDocument();
		private PDPage page;
		private PDPageContentStream stream;
		private final PDFont font;
		private final SimpleDateFormat dfReader = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		private final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy");
		private int countCards = 0;
		private int countAddresses = 0;
		private final String usAddress = "Fanclub · JNet Quality Consulting GmbH · Whistlerweg 27b · 81479 München";
		private final String us = "Fanclub ist ein Produkt der\n"
				+ "JNet Quality Consulting GmbH\n"
				+ "Wir vernetzen Fußball-Fans.\n\n"
				+ "Für Fragen rund um die App:\n"
				+ "+49 172 6379434\n"
				+ "support@fan-club.online";
		private final String text = "Lieber Sky Sportsbar Kunde,\n\n"
				+ "vielen Dank für Dein Feedback vom {date}, anbei die {cards} Marketing-Aufkleber.\n"
				+ "Weitere Schritte, um die besten Fans Deiner Region bei Dir feiern zu lassen:\n\n"
				+ "•   erstelle ein Serien-Termin für alle Spiele Eures Lieblingsklubs\n"
				+ "•   erstelle auch Events außerhalb von Spielen, z.B. Stammtische am Montag zum Nachtarock\n"
				+ "    des Bundesligawochenendes, vielleicht sogar mit einem Happy Hour Getränk oder ähnliches\n"
				+ "•   alle Veranstalltungen können mit einem zusätzlichen Klick automatisch auf unseren Social Media\n"
				+ "    Seiten veröffentlicht werden, so dass noch mehr Leute außerhalb unserer Community davon erfahen\n"
				+ "•   platziere den einen oder anderen Aufkleber prominent in Deiner Location\n"
				+ "•   lege die restlichen Aufkleber aus, so dass Deine Gäste sie sehen und mitnehmen können\n\n"
				+ "Wir helfen Dir gerne bei der Einrichtung, unsere Kontaktdaten stehen auf der Rückseite.";

		private PDF(final JsonNode addresses) throws IOException {
			font = PDType0Font.load(document, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"),
					true);
			addresses.elements().forEachRemaining(address -> {
				if (address.has("cards") && address.get("cards").get(0).asInt() > 0) {
					addMarketing(address);
					addAddress(address);
					countCards += address.get("cards").get(0).asInt();
					countAddresses++;
				}
			});
			addStatistics();
		}

		private byte[] toBytes() throws Exception {
			stream.close();
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			document.close();
			out.flush();
			return out.toByteArray();
		}

		private void addAddress(final JsonNode address) {
			createPage();
			int y = 0;
			addText(usAddress, 26, 45, 1.8f);
			System.out.println(address.get("name").asText());
			addText(address.get("name").asText(), 26, 39, 3.5f);
			for (String s : address.get("address").asText().split("\n"))
				addText(s, 26, 34 - (y++ * 5), 3.5f);
			for (String s : us.split("\n"))
				addText(s, (int) page.getBBox().getWidth() - 60, (int) page.getBBox().getHeight() - (y++ * 4),
						2.5f);
			addQRCode("https://fan-club.online?c=1" +
					(address.get("account").get(0).asInt() == 1 ? ""
							: "&i=" + address.get("locationId").asText() + "&h="
									+ address.get("hash").asText()));
		}

		private void addMarketing(final JsonNode address) {
			createPage();
			addBackground(new Color(246, 194, 166), new Color(245, 239, 232));
			addImage("/image/marketing/fanclub/logo.png", (int) page.getBBox().getWidth() - 38,
					(int) page.getBBox().getHeight() - 38, 30);
			try {
				int y = 0;
				for (String s : text
						.replace("{date}", df.format(dfReader.parse(address.get("createdAt").asText())))
						.replace("{cards}", "" + address.get("cards").get(0).asInt()).split("\n"))
					addText(s, 10, 85 - (y++ * 5), 3.5f);
			} catch (ParseException ex) {
				throw new RuntimeException(ex);
			}
		}

		private void addQRCode(final String url) {
			try {
				final Map<EncodeHintType, ErrorCorrectionLevel> hintMap = new HashMap<>();
				hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
				final QRCodeWriter qrCodeWriter = new QRCodeWriter();
				final BitMatrix byteMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 500, 500, hintMap);
				final int matrixWidth = byteMatrix.getWidth();
				final BufferedImage image = new BufferedImage(matrixWidth, matrixWidth, BufferedImage.TYPE_INT_RGB);
				image.createGraphics();
				final Graphics2D graphics = (Graphics2D) image.getGraphics();
				graphics.setColor(Color.WHITE);
				graphics.fillRect(0, 0, matrixWidth, matrixWidth);
				graphics.setColor(Color.BLACK);
				for (int i = 0; i < matrixWidth; i++) {
					for (int j = 0; j < matrixWidth; j++) {
						if (byteMatrix.get(i, j))
							graphics.fillRect(i, j, 1, 1);
					}
				}
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(image, "png", out);
				final PDImageXObject pdImage = PDImageXObject.createFromByteArray(document,
						out.toByteArray(), "qr");
				stream.drawImage(pdImage, (int) page.getBBox().getWidth() - 65, 11.5f, 40, 40);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		private void addStatistics() {
			createPage();
			addText("Auftragsvolumen:", 50, 50, 4);
			addText(countCards + " Aufkleber", 50, 45, 4);
			addText(countAddresses + " Adressen", 50, 40, 4);
		}

		private void createPage() {
			try {
				if (stream != null)
					stream.close();
				page = new PDPage(new PDRectangle(210, 99));
				document.addPage(page);
				stream = new PDPageContentStream(document, page);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		private void addText(String text, int x, int y, float size) {
			try {
				stream.beginText();
				stream.newLineAtOffset(x, y);
				stream.setFont(font, size);
				stream.showText(text);
				stream.endText();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		private void addImage(String image, int x, int y, int width) {
			try {
				final PDImageXObject pdImage = PDImageXObject.createFromByteArray(document,
						IOUtils.toByteArray(getClass().getResourceAsStream(image)),
						"logo");
				stream.drawImage(pdImage, x, y, width, width * pdImage.getHeight() / pdImage.getWidth());
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		private void addBackground(Color dark, Color bright) {
			try {
				final COSDictionary fdict = new COSDictionary();
				fdict.setInt(COSName.FUNCTION_TYPE, 2);
				COSArray domain = new COSArray();
				domain.add(COSInteger.get(0));
				domain.add(COSInteger.get(1));
				final COSArray c0 = new COSArray();
				c0.add(COSFloat.get("" + (((double) bright.getRed()) / 255)));
				c0.add(COSFloat.get("" + (((double) bright.getGreen()) / 255)));
				c0.add(COSFloat.get("" + (((double) bright.getBlue()) / 255)));
				final COSArray c1 = new COSArray();
				c1.add(COSFloat.get("" + (((double) dark.getRed()) / 255)));
				c1.add(COSFloat.get("" + (((double) dark.getGreen()) / 255)));
				c1.add(COSFloat.get("" + (((double) dark.getBlue()) / 255)));
				fdict.setItem(COSName.DOMAIN, domain);
				fdict.setItem(COSName.C0, c0);
				fdict.setItem(COSName.C1, c1);
				fdict.setInt(COSName.N, 1);
				final PDFunctionType2 func = new PDFunctionType2(fdict);
				final PDShadingType2 axialShading = new PDShadingType2(new COSDictionary());
				axialShading.setColorSpace(PDDeviceRGB.INSTANCE);
				axialShading.setShadingType(PDShading.SHADING_TYPE2);
				final COSArray coords1 = new COSArray();
				coords1.add(COSInteger.get(0));
				coords1.add(COSInteger.get(0));
				coords1.add(COSInteger.get((int) page.getBBox().getWidth()));
				coords1.add(COSInteger.get((int) page.getBBox().getHeight()));
				axialShading.setCoords(coords1);
				axialShading.setFunction(func);
				stream.shadingFill(axialShading);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}