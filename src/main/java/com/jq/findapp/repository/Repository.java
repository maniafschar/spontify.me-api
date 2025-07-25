package com.jq.findapp.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.hibernate.query.sqm.ParsingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Client;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.util.Strings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

@org.springframework.stereotype.Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
@Component
public class Repository {
	@PersistenceContext
	private EntityManager em;

	@Autowired
	private Listeners listeners;

	@SuppressWarnings("unchecked")
	public Result list(final QueryParams params) {
		final GeoLocationProcessor geo = new GeoLocationProcessor(params);
		final Result result = params.getQuery().createResult();
		final String sql = prepareSql(params);
		final List<Object> data;
		try {
			data = em.createQuery(sql).getResultList();
		} catch (final Exception ex) {
			throw new ParsingException(ex.getMessage() + "\n" + sql);
		}
		if (data != null && data.size() > 0) {
			if (params.getLimit() > 0 && params.getSearchGeoLocation() == null) {
				final int max = data.size() - params.getLimit();
				for (int i = 0; i < max; i++)
					data.remove(params.getLimit());
			}
			data.stream().forEach(e -> {
				result.getList().add(e instanceof Object[] ? (Object[]) e : new Object[] { e });
			});
			geo.postProcessor(result.getList());
			if (params.getLimit() > 0 && params.getSearchGeoLocation() != null) {
				final int max = result.size() - params.getLimit();
				for (int i = 0; i < max; i++)
					result.getList().remove(params.getLimit() + 1);
			}
			Attachment.resolve(result.getList());
			for (int i = 0; i < result.getHeader().length; i++) {
				if ("contact.longitude".equals(result.getHeader()[i])
						|| "contact.latitude".equals(result.getHeader()[i])
						|| "contact.password".equals(result.getHeader()[i])) {
					for (int i2 = 1; i2 < result.getList().size(); i2++)
						result.getList().get(i2)[i] = null;
				} else if ("contactMarketing.storage".equals(result.getHeader()[i])) {
					for (int i2 = 1; i2 < result.getList().size(); i2++)
						result.getList().get(i2)[i] = Strings.EMAIL.matcher(result.getList().get(i2)[i].toString())
								.replaceAll("");
				}
			}
		}
		return result;
	}

	private String prepareSql(final QueryParams params) {
		String search = Strings.isEmpty(params.getSearch()) ? "1=1" : sanatizeSearchToken(params);
		if (params.getSearchGeoLocation() != null)
			search += " and " + params.getSearchGeoLocation();
		if (params.getQuery().addBlock && params.getUser() != null) {
			if (params.getQuery().name().startsWith("contact_") || params.getQuery().name().startsWith("event_"))
				search += " and (select b.id from Block b where b.contactId=contact.id and b.contactId2={USERID} or b.contactId={USERID} and b.contactId2=contact.id) is null";
			if (params.getQuery().name().startsWith("location_") || params.getQuery().name().startsWith("event_"))
				search += " and (select b.id from Block b where b.contactId={USERID} and b.locationId=location.id) is null";
			if (params.getQuery().name().startsWith("event_"))
				search += " and (select b.id from Block b where b.contactId={USERID} and b.eventId=event.id) is null";
		}
		String s = params.getQuery().getSql().replace("{search}", search);
		if (s.contains("{ID}"))
			s = s.replaceAll("\\{ID}", "" + params.getId());
		if (s.contains("{CLIENTID}"))
			s = s.replaceAll("\\{CLIENTID}", "" + params.getUser().getClientId());
		if (s.contains("{ADMINID}"))
			s = s.replaceAll("\\{ADMINID}", "" + one(Client.class, params.getUser().getClientId()).getAdminId());
		if (s.contains("{USERAGE}"))
			s = s.replaceAll("\\{USERAGE}",
					"" + (params.getUser().getAge() == null ? 0 : params.getUser().getAge()));
		if (s.contains("{USERGENDER}"))
			s = s.replaceAll("\\{USERGENDER}",
					"" + (params.getUser().getGender() == null ? 0 : params.getUser().getGender()));
		if (s.contains("{USERSKILLS}"))
			s = s.replaceAll("\\{USERSKILLS}",
					params.getUser().getSkills() == null
							|| params.getUser().getSkills().trim().length() == 0 ? "-"
									: params.getUser().getSkills());
		if (s.contains("{USERSKILLSTEXT}"))
			s = s.replaceAll("\\{USERSKILLSTEXT}",
					params.getUser().getSkillsText() == null || params.getUser().getSkillsText().trim().length() == 0
							? "-"
							: params.getUser().getSkillsText());
		if (s.contains("{USERID}"))
			s = s.replaceAll("\\{USERID}", "" + params.getUser().getId());
		return s;
	}

	private String sanatizeSearchToken(final QueryParams params) {
		if (params.getSearch() == null)
			return "";
		final StringBuilder s = new StringBuilder(params.getSearch().toLowerCase());
		int p, p2;
		while ((p = s.indexOf("'")) > -1) {
			p2 = p;
			do {
				p2 = s.indexOf("'", p2 + 1);
			} while (p2 > 0 && "\\".equals(s.substring(p2 - 1, p2)));
			if (p2 < 0)
				throw new IllegalArgumentException(
						"Invalid quote in " + params.getQuery().name() + " search: " + params.getSearch());
			s.delete(p, p2 + 1);
		}
		if (s.indexOf(";") > -1 || s.indexOf("union") > -1 || s.indexOf("update") > -1
				|| s.indexOf("insert") > -1 || s.indexOf("delete") > -1)
			throw new IllegalArgumentException(
					"Invalid expression in " + params.getQuery().name() + " search: " + params.getSearch());
		return params.getSearch();
	}

	@SuppressWarnings("unchecked")
	public List<? extends BaseEntity> list(final String hql) {
		try {
			return (List<BaseEntity>) em.createQuery(hql,
					Class.forName(BaseEntity.class.getPackage().getName() + "." + hql.split(" ")[1])).getResultList();
		} catch (ClassNotFoundException ex) {
			throw new RuntimeException(ex);
		}
	}

	public <T extends BaseEntity> T one(final Class<T> clazz, final BigInteger id) {
		final T entity = em.find(clazz, id);
		if (entity != null)
			entity.historize();
		return entity;
	}

	public Map<String, Object> one(final QueryParams params) {
		final Result result = list(params);
		if (result.size() < 1)
			return null;
		if (result.size() > 1)
			throw new RuntimeException(
					params.getQuery() + " returned " + result.size() + " objects for " + params.getSearch());
		return result.get(0);
	}

	public boolean save(final BaseEntity entity) throws IllegalArgumentException {
		if (entity.modified()) {
			try {
				Attachment.save(entity);
				if (entity.getId() == null) {
					if (entity.getCreatedAt() == null)
						entity.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
					listeners.prePersist(entity);
					em.persist(entity);
					listeners.postPersist(entity);
				} else {
					entity.setModifiedAt(new Timestamp(Instant.now().toEpochMilli()));
					listeners.preUpdate(entity);
					em.merge(entity);
					listeners.postUpdate(entity);
				}
				em.flush();
				return true;
			} catch (PersistenceException ex) {
				throw new RuntimeException(ex);
			}
		}
		return false;
	}

	public void delete(final BaseEntity entity) throws IllegalArgumentException {
		try {
			listeners.preRemove(entity);
			em.remove(em.contains(entity) ? entity : em.merge(entity));
			Attachment.delete(entity);
			listeners.postRemove(entity);
		} catch (PersistenceException ex) {
			throw new RuntimeException(ex);
		}
	}

	public int executeUpdate(final String hql, final Object... params) {
		final jakarta.persistence.Query query = em.createQuery(hql);
		if (params != null) {
			for (int i = 0; i < params.length; i++)
				query.setParameter(i + 1, params[i]);
		}
		return query.executeUpdate();
	}

	public String cleanUpAttachments() throws Exception {
		final long time = System.currentTimeMillis();
		final List<String> ids = new ArrayList<>();
		final Map<String, List<String>> attachmentEntities = Attachment.getAttachmentEntities();
		for (final String entity : attachmentEntities.keySet()) {
			final List<? extends BaseEntity> list = list("from " + entity + attachmentEntities.get(entity).stream()
					.collect(Collectors.joining(" like '%" + Attachment.SEPARATOR + "%' or ", " where ",
							" like '%" + Attachment.SEPARATOR + "%'")));
			list.stream().forEach(e -> {
				attachmentEntities.get(entity).stream().forEach(field -> {
					try {
						final Method m = e.getClass()
								.getDeclaredMethod("get" + field.substring(0, 1).toUpperCase() + field.substring(1));
						m.setAccessible(true);
						final Object o = m.invoke(e);
						if (o != null && o.toString().contains("" + Attachment.SEPARATOR))
							ids.add(Attachment.getFilename(o.toString()));
					} catch (final Exception ex) {
						throw new RuntimeException(ex);
					}
				});
			});
		}
		return cleanUpAttachmentDir(Attachment.PATH, time, ids) +
				cleanUpAttachmentDir(Attachment.PATH + Attachment.PUBLIC, time, ids);
	}

	private String cleanUpAttachmentDir(final String dir, final long time, final List<String> ids) throws Exception {
		final String[] dirs = new File(dir).list();
		final String deleted = "deleted";
		Path path = Paths.get(dir + deleted);
		if (!Files.exists(path))
			Files.createDirectory(path);
		int count = 0;
		for (int i = 0; i < dirs.length; i++) {
			if (!dirs[i].equals(deleted)
					&& !Attachment.PUBLIC.equals(dirs[i] + '/')) {
				final String[] files = new File(dir + dirs[i]).list();
				if (files != null) {
					for (int i2 = 0; i2 < files.length; i2++) {
						if (!ids.contains(dirs[i] + '/' + files[i2])) {
							path = Path.of(dir + dirs[i] + '/' + files[i2]);
							if (path.toFile().isFile() && path.toFile().lastModified() < time) {
								Files.move(path, Path.of(dir + '/' + deleted + '/' + files[i2]),
										StandardCopyOption.ATOMIC_MOVE);
								count++;
							}
						}
					}
				}
			}
		}
		return count > 0 ? dir.substring(0, dir.length() - 1) + ": " + count + '\n' : "";
	}

	public static class Attachment {
		public final static String SEPARATOR = "\u0015";
		public final static String PATH = "attachments/";
		public final static String PUBLIC = "PUBLIC/";
		private final static Pattern RESOLVABLE_COLUMNS = Pattern.compile(".*(image|note|storage).*");
		private final static Map<String, List<String>> resolvableEntities = new HashMap<>();

		public static String createImage(final String name, final byte[] data) {
			return name + SEPARATOR + Base64.getEncoder().encodeToString(data);
		}

		private static void resolve(final List<Object[]> list) {
			for (int i = 0; i < list.get(0).length; i++) {
				final String[] s = ((String) list.get(0)[i]).split("\\.");
				if (s.length > 1 && RESOLVABLE_COLUMNS.matcher(s[1]).matches()) {
					for (int i2 = 1; i2 < list.size(); i2++)
						list.get(i2)[i] = resolve((String) list.get(i2)[i]);
				}
			}
		}

		static void delete(final BaseEntity entity) {
			final Field[] fields = entity.getClass().getDeclaredFields();
			for (final Field field : fields) {
				if (RESOLVABLE_COLUMNS.matcher(field.getName()).matches()) {
					try {
						field.setAccessible(true);
						final String value = "" + field.get(entity);
						if (value.contains(SEPARATOR)) {
							final File f = new File(PATH + (value.contains(".") ? PUBLIC : "") + getFilename(value));
							if (f.exists())
								f.delete();
						}
					} catch (final Exception e) {
						throw new RuntimeException("Failed on " + field.getName(), e);
					}
				}
			}
		}

		public static void save(final BaseEntity entity) {
			final Field[] fields = entity.getClass().getDeclaredFields();
			for (final Field field : fields) {
				if (RESOLVABLE_COLUMNS.matcher(field.getName()).matches()) {
					try {
						field.setAccessible(true);
						field.set(entity,
								save(field.getName().contains("image"), (String) field.get(entity),
										(String) entity.old(field.getName())));
					} catch (final Exception e) {
						throw new RuntimeException("Failed on " + field.getName(), e);
					}
				}
			}
		}

		private static long getMax(final String dir, long max) {
			final File f = new File(dir.replace('/', File.separatorChar));
			if (f.exists() && !f.isFile()) {
				final String[] s = f.list();
				final Pattern number = Pattern.compile("^\\d+(\\.[a-zA-Z0-9]{2,5})?$");
				long t;
				for (int i = 0; i < s.length; i++) {
					if (number.matcher(s[i]).matches()) {
						try {
							t = Long.parseLong(s[i].indexOf('.') > -1 ? s[i].substring(0, s[i].indexOf('.')) : s[i]);
							if (max < t)
								max = t;
						} catch (final NumberFormatException ex) {
							throw new NumberFormatException("Failed to parse attachment " + dir + s[i]);
						}
					}
				}
			}
			return max;
		}

		public static byte[] getFile(final String id) {
			try {
				return IOUtils.toByteArray(new FileInputStream(PATH + (id.contains(".") ? PUBLIC : "")
						+ (id.contains(SEPARATOR) ? getFilename(id) : id)));
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		private static String getFilename(final String id) {
			final String[] ids = id.split(SEPARATOR);
			final long i = Long.valueOf(ids[1]), diff = 10000;
			long d = 0;
			do {
				d += diff;
			} while (i >= d);
			return d + "/" + i + (ids[0].indexOf('.') < 0 ? "" : ids[0].substring(ids[0].lastIndexOf('.')));
		}

		private static final long getNextAttachmentID(final String dir) {
			long max = getMax(dir, -1);
			if (max > -1)
				max = getMax(dir + max, max - 10000);
			return max + 1;
		}

		public static String resolve(String value) {
			if (value != null && value.contains(SEPARATOR) && value.indexOf(SEPARATOR) < 8
					&& value.indexOf(SEPARATOR) == value.lastIndexOf(SEPARATOR)) {
				try {
					final String n = getFilename(value);
					if (n.contains("."))
						value = n;
					else
						value = new String(IOUtils.toByteArray(new FileInputStream(PATH + n)),
								StandardCharsets.UTF_8);
				} catch (final Exception ex) {
					throw new RuntimeException("Failed on " + value, ex);
				}
			}
			return value;
		}

		private static synchronized String save(final boolean publicDir, final String value, final String old)
				throws IOException {
			if (Strings.isEmpty(value)) {
				if (old != null && old.contains(SEPARATOR))
					new File(PATH + (old.contains(".") ? PUBLIC : "") + getFilename(old)).delete();
				return value;
			}
			final String id;
			// value = ".jpg" + SEPARATOR + "base64data";
			// value = "some text";
			final String[] s;
			if (publicDir) {
				if (value.contains(SEPARATOR))
					s = value.split(SEPARATOR);
				else
					throw new IllegalArgumentException("IMAGE_FORMAT_EXCEPTION missing separeator: "
							+ value.substring(0, Math.min(50, value.length())));
			} else
				s = new String[] { "", value };
			if (publicDir && s[1].length() > 20) {
				final byte[] data = Base64.getDecoder().decode(s[1]);
				if (!Strings.isEmpty(old)) {
					final Path path = Paths.get(PATH + PUBLIC + getFilename(old));
					if (Files.exists(path)) {
						if (Arrays.equals(IOUtils.toByteArray(new FileInputStream(path.toFile())), data))
							return old;
						new File(PATH + PUBLIC + getFilename(old)).delete();
					}
				}
				// new image, we need a new id because of browser caching
				id = s[0] + SEPARATOR + getNextAttachmentID(PATH + PUBLIC);
				final Path path = Paths.get(PATH + PUBLIC + getFilename(id));
				if (!Files.exists(path.getParent()))
					Files.createDirectory(path.getParent());
				IOUtils.write(data, new FileOutputStream(path.toFile()));
			} else if (s[1].length() > 255) {
				// it's a text bigger than 255, store in file
				if (old != null && old.startsWith(SEPARATOR)) {
					// overwrite old value, which is stored in a file
					// old = SEPARATOR + "15603"
					new File(PATH + getFilename(old)).delete();
					id = old;
				} else {
					id = SEPARATOR + getNextAttachmentID(PATH);
					final Path path = Paths.get(PATH + getFilename(id));
					if (!Files.exists(path.getParent()))
						Files.createDirectory(path.getParent());
				}
				IOUtils.write(s[1].getBytes(StandardCharsets.UTF_8), new FileOutputStream(PATH + getFilename(id)));
			} else
				id = value;
			return id;
		}

		private static Map<String, List<String>> getAttachmentEntities() throws Exception {
			if (resolvableEntities.size() == 0) {
				final String src = BaseEntity.class.getProtectionDomain().getCodeSource().getLocation().getFile();
				try (ZipFile zip = new ZipFile(src.substring(src.indexOf(':') + 1, src.indexOf('!')))) {
					final Enumeration<? extends ZipEntry> zipEntries = zip.entries();
					while (zipEntries.hasMoreElements()) {
						final ZipEntry entry = zipEntries.nextElement();
						if (entry.getName().contains(BaseEntity.class.getPackageName().replace('.', '/'))
								&& entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
							final Class<?> entity = Class
									.forName(BaseEntity.class.getPackageName() + '.'
											+ entry.getName().substring(entry.getName().lastIndexOf("/") + 1)
													.replace(".class", ""));
							final Field[] fields = entity.getDeclaredFields();
							for (int i = 0; i < fields.length; i++) {
								if (RESOLVABLE_COLUMNS.matcher(fields[i].getName()).matches()) {
									if (!resolvableEntities.containsKey(entity.getSimpleName()))
										resolvableEntities.put(entity.getSimpleName(), new ArrayList<>());
									resolvableEntities.get(entity.getSimpleName()).add(fields[i].getName());
								}
							}
						}
					}
				}
			}
			return resolvableEntities;
		}
	}
}
