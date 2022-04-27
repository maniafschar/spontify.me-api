package com.jq.findapp.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.jq.findapp.api.DBApi.WriteEntity;
import com.jq.findapp.repository.Repository.Attachment;

public class EntityUtil {
	public static final int IMAGE_SIZE = 800;
	public static final int IMAGE_THUMB_SIZE = 100;

	public static void addImageList(WriteEntity entity) throws IOException {
		if (entity.getValues() != null && entity.getValues().containsKey("image")) {
			final String data = (String) entity.getValues().get("image");
			final byte[] b = scaleImage(Base64.getDecoder().decode(
					data.substring(data.indexOf('\u0015') + 1)), IMAGE_THUMB_SIZE);
			entity.getValues().put("imageList", Attachment.createImage(".jpg", b));
		}
	}

	public static byte[] scaleImage(byte[] data, int size) throws IOException {
		final BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(data));
		int width = originalImage.getWidth();
		int height = originalImage.getHeight();
		int x = 0, y = 0;
		if (width > height) {
			x = (width - height) / 2;
			width = height;
		} else {
			y = (height - width) / 2;
			height = width;
		}
		final BufferedImage resizedImage = new BufferedImage(size, size,
				originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
		final Graphics2D g = resizedImage.createGraphics();
		g.drawImage(originalImage, 0, 0, size, size, x, y, x + width, y + height, null);
		g.dispose();
		resizedImage.flush();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(resizedImage, "jpg", out);
		return out.toByteArray();
	}
}
