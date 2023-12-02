package com.jq.findapp.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;

import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;

public class EntityUtil {
	public static final int IMAGE_SIZE = 800;
	public static final int IMAGE_THUMB_SIZE = 100;

	public static void addImageList(final WriteEntity entity) throws IOException {
		if (entity.getValues() != null && entity.getValues().containsKey("image")) {
			try {
				entity.getClazz().getDeclaredMethod("getImageList");
				final String data = (String) entity.getValues().get("image");
				final byte[] b = scaleImage(Base64.getDecoder().decode(
						data.substring(data.indexOf('\u0015') + 1)), IMAGE_THUMB_SIZE);
				entity.getValues().put("imageList", Attachment.createImage(".jpg", b));
			} catch (final NoSuchMethodException e) {
				// entity does not have imageList, no need to add it
			}
		}
	}

	public static byte[] scaleImage(final byte[] data, final int size) throws IOException {
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
		final byte[] result = out.toByteArray();
		if (result == null || result.length < 100)
			throw new IllegalArgumentException("no image, length: " + (result == null ? -1 : result.length));
		return result;
	}

	public static String getImage(final String url, final int size, int minimum) {
		final byte[] data;
		final BufferedImage img;
		try {
			data = IOUtils.toByteArray(new URL(url));
			img = ImageIO.read(new ByteArrayInputStream(data));
			if (minimum == 0)
				minimum = 400;
			if (img.getWidth() > minimum && img.getHeight() > minimum)
				return Repository.Attachment.createImage(".jpg", scaleImage(data, size));
		} catch (final Exception ex) {
			throw new IllegalArgumentException("no image: " + url, ex);
		}
		throw new IllegalArgumentException(
				"no image: [size " + img.getWidth() + "x" + img.getHeight() + " too small] " + url);
	}

	public static BaseEntity createEntity(final WriteEntity entity, final Contact contact) throws Exception {
		final BaseEntity e = entity.getClazz().newInstance();
		try {
			if (e.getClass().getDeclaredMethod("getContactId") != null)
				entity.getValues().put("contactId", contact.getId());
		} catch (final NoSuchMethodException ex) {
			// no need to handle
		}
		try {
			if (e.getClass().getDeclaredMethod("getClientId") != null)
				entity.getValues().put("clientId", contact.getClientId());
		} catch (final NoSuchMethodException ex) {
			// no need to handle
		}
		addImageList(entity);
		e.populate(entity.getValues());
		return e;
	}
}
