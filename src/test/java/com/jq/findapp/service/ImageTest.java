package com.jq.findapp.service;

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

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

public class ImageTest {
	private final int factor = 2;

	@Test
	public void createFanclub() throws Exception {
		create("fanclub", "Fanclub", "Wer schaut denn gerne alleine?", 1, Color.BLACK);
	}

	@Test
	public void createAfterwork() throws Exception {
		create("afterwork", "Afterwork", "Wer geht denn alleine in den Feierabend?", 1, Color.WHITE);
	}

	private void create(String prefix, String appName, String claim, int no, Color textColor) throws Exception {
		prefix = "/image/marketing/" + prefix + "/";
		final int width = 5568, height = 3712;
		final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = (Graphics2D) output.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		BufferedImage image = ImageIO.read(getClass().getResourceAsStream(prefix + "background" + no + ".jpg"));
		g2.drawImage(image, 0, 0, width, height, 0, 0, image.getWidth(),
				image.getHeight(), null);
		image = ImageIO.read(getClass().getResourceAsStream(prefix + "qr" + no + ".png"));
		final int size = (int) (0.4 * height);
		g2.drawImage(image, width - size - 50, height - size - 50, width - 50, height - 50, 0, 0, image.getWidth(), image.getHeight(), null);
		image = ImageIO.read(getClass().getResourceAsStream(prefix + "logo.png"));
		g2.drawImage(image, 50, height - 100 - size, 50 + size, height - 100, 0, 0,
				image.getWidth(), image.getHeight(), null);
		final Font font = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
				.deriveFont(325f);
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
		g2.setColor(textColor);
		g2.setFont(font);
		g2.drawString(claim, (width - g2.getFontMetrics().stringWidth(claim)) / 2,
				50 + g2.getFontMetrics().getHeight());
		g2.drawString(appName, 50 + (size - g2.getFontMetrics().stringWidth(appName)) / 2, height - 50 - g2.getFontMetrics().getHeight());
		output.flush();
		ImageIO.write(output, "png", new FileOutputStream("test.png"));
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
