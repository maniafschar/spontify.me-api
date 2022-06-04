package com.jq.findapp.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.Query.Result;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
@Component
public class Repository {
	@PersistenceContext
	private EntityManager em;

	@Value("${account.delete.sql}")
	private String accountDeleteSql;

	public Result list(final QueryParams params) {
		final GeoLocationProcessor geo = new GeoLocationProcessor(params);
		final Result result = params.getQuery().createResult();
		final String sql = params.getQuery().prepareSql(params);
		final List<Object> data = em.createQuery(sql).getResultList();
		if (data != null && data.size() > 0) {
			data.stream()
					.forEach(e -> {
						result.getList().add(e instanceof Object[] ? (Object[]) e : new Object[] { e });
					});
			geo.postProcessor(result.getList());
			if (params.getLimit() > 0) {
				final int max = result.size() - params.getLimit();
				for (int i = 0; i < max; i++)
					result.getList().remove(params.getLimit() + 1);
			}
			for (int i = 0; i < result.getHeader().length; i++) {
				if ("contact.longitude".equals(result.getHeader()[i])
						|| "contact.latitude".equals(result.getHeader()[i])
						|| "contact.password".equals(result.getHeader()[i])) {
					for (int i2 = 1; i2 < result.getList().size(); i2++)
						result.getList().get(i2)[i] = null;
				}
			}
			Attachment.resolve(result.getList());
		}
		return result;
	}

	public <T extends BaseEntity> T one(final Class<T> clazz, final BigInteger id) {
		return em.find(clazz, id);
	}

	public Map<String, Object> one(QueryParams params) {
		final Result result = list(params);
		if (result.size() < 1)
			return null;
		if (result.size() > 1)
			throw new RuntimeException(
					params.getQuery() + " returned " + result.size() + " objects for " + params.getSearch());
		return result.get(0);
	}

	public void save(final BaseEntity entity) throws Exception {
		Attachment.save(entity);
		if (entity.getId() == null) {
			entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
			em.persist(entity);
		} else {
			entity.setModifiedAt(new Timestamp(System.currentTimeMillis()));
			em.merge(entity);
		}
	}

	public void delete(final BaseEntity entity) throws Exception {
		em.remove(entity);
		Attachment.delete(entity);
	}

	public void deleteAccount(final BigInteger user) throws Exception {
		final String[] sqls = accountDeleteSql.split(";");
		for (String sql : sqls) {
			try {
				sql = sql.replaceAll("\\{ID}", "" + user).trim();
				if (sql.startsWith("from ")) {
					final List<?> list = em.createQuery(sql,
							Class.forName(BaseEntity.class.getPackage().getName() + "." + sql.split(" ")[1]))
							.getResultList();
					for (Object e : list)
						delete((BaseEntity) e);
				} else
					executeUpdate(sql);
			} catch (Exception ex) {
				throw new RuntimeException("ERROR SQL on account delete: " + ex.getMessage() + "\n" + sql);
			}
		}
	}

	public void executeUpdate(String sql) {
		em.createQuery(sql).executeUpdate();
	}

	public static class Attachment {
		private final static Pattern RESOLVABLE_COLUMNS = Pattern.compile(".*(IMAGE|NOTE|STORAGE).*",
				Pattern.CASE_INSENSITIVE);
		private final static String PATH = "attachments/";
		private final static String PUBLIC = "PUBLIC/";
		private final static String SEPARATOR = "\u0015";

		public static String createImage(String name, byte[] data) {
			return name + SEPARATOR + Base64.getEncoder().encodeToString(data);
		}

		private static void resolve(List<Object[]> list) {
			for (int i = 0; i < list.get(0).length; i++) {
				if (RESOLVABLE_COLUMNS.matcher((String) list.get(0)[i]).matches()) {
					for (int i2 = 1; i2 < list.size(); i2++)
						list.get(i2)[i] = resolve((String) list.get(i2)[i]);
				}
			}
		}

		static void delete(BaseEntity entity) {
			final Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {
				if (RESOLVABLE_COLUMNS.matcher(field.getName()).matches()) {
					try {
						field.setAccessible(true);
						final String value = "" + field.get(entity);
						if (value.contains(SEPARATOR)) {
							final File f = new File(PATH + (value.contains(".") ? PUBLIC : "") + getFilename(value));
							if (f.exists())
								f.delete();
						}
					} catch (Exception e) {
						throw new RuntimeException("Failed on " + field.getName(), e);
					}
				}
			}
		}

		private static void save(BaseEntity entity) {
			final Field[] fields = entity.getClass().getDeclaredFields();
			for (Field field : fields) {
				if (RESOLVABLE_COLUMNS.matcher(field.getName()).matches()) {
					try {
						field.setAccessible(true);
						field.set(entity,
								save((String) field.get(entity), (String) entity.old(field.getName())));
					} catch (Exception e) {
						throw new RuntimeException("Failed on " + field.getName(), e);
					}
				}
			}
		}

		private static long getMax(String dir, long max) {
			final File f = new File(dir.replace('/', File.separatorChar));
			if (f.exists() && !f.isFile()) {
				final String[] s = f.list();
				long t;
				for (int i = 0; i < s.length; i++) {
					if (!"deleted".equals(s[i]) && !"PUBLIC".equals(s[i])) {
						if (s[i].indexOf('.') > -1)
							s[i] = s[i].substring(0, s[i].indexOf('.'));
						t = Long.parseLong(s[i]);
						if (max < t)
							max = t;
					}
				}
			}
			return max;
		}

		private static String getFilename(String id) {
			final String[] ids = id.split(SEPARATOR);
			final long i = Long.valueOf(ids[1]), diff = 10000;
			long d = 0;
			do {
				d += diff;
			} while (i >= d);
			return d + "/" + i + (ids[0].indexOf('.') < 0 ? "" : ids[0].substring(ids[0].lastIndexOf('.')));
		}

		private static final long getNextAttachmentID(final String dir) {
			long subDir = getMax(dir, -1), max = -1;
			if (subDir > -1)
				max = getMax(dir + subDir, max);
			return max + 1;
		}

		private static String resolve(String value) {
			if (value != null && value.contains(SEPARATOR)) {
				try {
					final String n = getFilename(value);
					if (n.contains("."))
						value = n;
					else
						value = new String(IOUtils.toByteArray(new FileInputStream(PATH + n)),
								StandardCharsets.UTF_8);
				} catch (Exception ex) {
					throw new RuntimeException("Failed on " + value, ex);
				}
			}
			return value;
		}

		private static synchronized String save(String value, String old) throws IOException {
			if (value == null) {
				if (old != null && old.contains(SEPARATOR))
					new File(PATH + (old.contains(".") ? PUBLIC : "") + getFilename(old)).delete();
				return null;
			}
			final String id;
			// value = ".jpg" + SEPARATOR + "base64data";
			// value = "some text";
			final String[] s = value.contains(SEPARATOR) ? value.split(SEPARATOR) : new String[] { "", value };
			if (s[0].contains(".") && s[1].length() > 20) {
				// new image, we need a new id because of browser caching
				id = s[0] + SEPARATOR + getNextAttachmentID(PATH + PUBLIC);
				IOUtils.write(Base64.getDecoder().decode(s[1]), new FileOutputStream(PATH + PUBLIC + getFilename(id)));
				if (old != null)
					new File(PATH + PUBLIC + getFilename(old)).delete();
			} else if (s[1].length() > 255) {
				// it's a text bigger than 255, store in file
				if (old != null && old.startsWith(SEPARATOR)) {
					// overwrite old value, which is stored in a file
					// old = SEPARATOR + "15603"
					new File(PATH + getFilename(old)).delete();
					id = old;
				} else
					id = SEPARATOR + getNextAttachmentID(PATH);
				IOUtils.write(s[1].getBytes(StandardCharsets.UTF_8), new FileOutputStream(PATH + getFilename(id)));
			} else
				id = value;
			return id;
		}
	}
}