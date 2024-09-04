package com.jq.findapp.service.backend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class MarketingImage {
	private final int factor = 2;

	@Test
	public void createFanclub() throws Exception {
		create("Fanclub", "Auch kein Bock, allein zu schauen?", new Color(0, 0, 74), 218f, "https://fan-club.online");
	}

	@Test
	public void createAfterwork() throws Exception {
		create("Afterwork", "Auch kein Bock allein zu feiern?", Color.WHITE, 295f, "https://after-work.events");
	}

	private void create(String appName, String claim, Color textColor, float textSize, String url) throws Exception {
		final String prefix = "/image/marketing/" + appName.toLowerCase() + "/";
		final Font font = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
				.deriveFont(textSize);

		// background
		BufferedImage image = ImageIO.read(getClass().getResourceAsStream(prefix + "background.jpg"));
		final int width = image.getWidth(), height = image.getHeight();
		final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = (Graphics2D) output.getGraphics();
		final int size = (int) (0.4 * height), padding = 150, extraPaddingLogo = 55;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.drawImage(image, 0, 0, width, height, 0, 0, image.getWidth(), image.getHeight() * width / image.getWidth(),
				null);

		// qr code
		image = createQRCode(url, textColor);
		final int p = padding / 2;
		g2.drawImage(image, width - size - p, height - size - p, width - p, height - p,
				0, 0, image.getWidth(), image.getHeight(), null);

		// claim & shadow
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
		g2.setFont(font);
		g2.setColor(new Color(255, 255, 255, 75));
		final int shadow = 8;
		g2.drawString(claim, (width - g2.getFontMetrics().stringWidth(claim)) / 2 + shadow,
				padding + g2.getFontMetrics().getHeight() + shadow);
		g2.setColor(textColor);
		g2.drawString(claim, (width - g2.getFontMetrics().stringWidth(claim)) / 2,
				padding + g2.getFontMetrics().getHeight());

		// logo & app name
		image = ImageIO.read(getClass().getResourceAsStream(prefix + "logo.png"));
		final double logoFactor = 0.61;
		g2.drawImage(image, 2 * padding, height - size + extraPaddingLogo,
				2 * padding + (int) (size * logoFactor),
				height - (int) (size * (1 - logoFactor)) + extraPaddingLogo,
				0, 0, image.getWidth(), image.getHeight(), null);
		g2.setColor(textColor);
		g2.drawString(appName,
				2 * padding + ((int) (size * logoFactor) - g2.getFontMetrics().stringWidth(appName)) / 2,
				height - g2.getFontMetrics().getHeight() + extraPaddingLogo - 40);

		output.flush();
		ImageIO.write(output, "png", new FileOutputStream("test.png"));
	}

	private BufferedImage createQRCode(final String url, final Color color) {
		try {
			final Map<EncodeHintType, ErrorCorrectionLevel> hintMap = new HashMap<>();
			hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
			final QRCodeWriter qrCodeWriter = new QRCodeWriter();
			final BitMatrix byteMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 500, 500, hintMap);
			final int matrixWidth = byteMatrix.getWidth();
			final BufferedImage image = new BufferedImage(matrixWidth, matrixWidth, BufferedImage.TYPE_INT_ARGB);
			image.createGraphics();
			final Graphics2D graphics = (Graphics2D) image.getGraphics();
			graphics.setColor(color);
			for (int i = 0; i < matrixWidth; i++) {
				for (int j = 0; j < matrixWidth; j++) {
					if (byteMatrix.get(i, j))
						graphics.fillRect(i, j, 1, 1);
				}
			}
			graphics.dispose();
			return image;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Test
	public void createAfterworkFBTitel() throws Exception {
		final int width = 851, height = 315;
		final BufferedImage output = new BufferedImage(factor * width, factor * height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = (Graphics2D) output.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setPaint(
				new GradientPaint(0, factor * -40, new Color(255, 4, 250), 0, factor * height,
						new Color(80, 0, 75)));
		g2.fill(new Rectangle.Float(0, 0, factor * width, factor * height * 0.6f));
		g2.setPaint(
				new GradientPaint(0, factor * height * 0.6f, new Color((int) (150 * 1.2), (int) (160 * 1.2), 0), 0,
						factor * height, new Color(210, 255, 20)));
		g2.fill(new Rectangle.Float(0, factor * height * 0.6f, factor * width, factor * height));
		final Font font = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"));
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
		g2.setFont(font);
		g2.setColor(Color.BLACK);
		paint(g2, "business", 25, 55, 110);
		paint(g2, "party", 180, 20, 160);
		paint(g2, "restaurant", 380, 60, 100);
		paint(g2, "museum", 510, 30, 130);
		paint(g2, "sport", 680, 40, 140);
		output.flush();
		ImageIO.write(output, "png", new FileOutputStream("test.png"));
	}

	private void paint(final Graphics2D g2, final String name, final int x, final int y, final int width)
			throws IOException {
		final BufferedImage image = ImageIO.read(getClass().getResourceAsStream("/image/" + name + ".jpg"));
		final int height = image.getHeight() * width / image.getWidth();
		g2.drawImage(image, factor * x, factor * y, factor * (x + width), factor * (y + height), 0, 0, image.getWidth(),
				image.getHeight(), null);
		final BufferedImage reflection = new BufferedImage(factor * width, factor * height,
				BufferedImage.TYPE_INT_ARGB);
		final Graphics2D rg = reflection.createGraphics();
		rg.scale(1, -1);
		rg.drawImage(image, 0, -factor * height, factor * width, 0, 0, 0, image.getWidth(), image.getHeight(), null);
		rg.setComposite(AlphaComposite.DstIn);
		rg.setPaint(
				new GradientPaint(0, -factor * height / 4, new Color(0, 0, 0, 0), 0, 0, new Color(255, 255, 255, 50)));
		rg.fillRect(0, -factor * height, factor * width, factor * height);
		rg.dispose();
		reflection.flush();
		g2.drawImage(reflection, factor * x, factor * (y + height + 5 * width / 150), null);
		g2.setPaint(null);
		g2.setFont(g2.getFont().deriveFont(factor * (20f * width / 150)));
		g2.drawString(name, factor * x + (factor * width - g2.getFontMetrics().stringWidth(name)) / 2,
				factor * (y + height + 30 * width / 150));
	}
}
