package com.jq.findapp.repository.listener;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class LocationListenerTest {
	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void save_errorDuplicateLatLng() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setLongitude(location.getLongitude() + 0.0001f);
		location.setLatitude(location.getLatitude() + 0.0002f);

		// when
		try {
			repository.save(location);
			throw new RuntimeException("IllegalArgumentException expected");
		} catch (IllegalArgumentException ex) {

			// then exact exception
			if (!ex.getMessage().startsWith("location exists: "))
				throw new RuntimeException("wrong exception message: " + ex.getMessage());
		}
	}

	@Test
	public void save_errorDuplicateNameLongVersion() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setName(location.getName().substring(0, location.getName().lastIndexOf(" ")));
		location.setLongitude(location.getLongitude() + 12f);
		location.setLatitude(location.getLatitude() + 5f);

		// when
		try {
			repository.save(location);
			throw new RuntimeException("IllegalArgumentException expected");
		} catch (IllegalArgumentException ex) {

			// then exact exception
			if (!ex.getMessage().startsWith("location exists: "))
				throw new RuntimeException("wrong exception message: " + ex.getMessage());
		}
	}

	@Test
	public void save_errorDuplicateNameShortVersion() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setName("Insert");
		location.setLongitude(location.getLongitude() + 12f);
		location.setLatitude(location.getLatitude() + 5f);

		// when
		try {
			repository.save(location);
			throw new RuntimeException("IllegalArgumentException expected");
		} catch (IllegalArgumentException ex) {

			// then exact exception
			if (!ex.getMessage().startsWith("location exists: "))
				throw new RuntimeException("wrong exception message: " + ex.getMessage());
		}
	}

	@Test
	public void save_errorDuplicateNameDifferentOrder() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setName("Update Inser th");
		location.setLongitude(location.getLongitude() + 12f);
		location.setLatitude(location.getLatitude() + 5f);

		// when
		try {
			repository.save(location);
			throw new RuntimeException("IllegalArgumentException expected");
		} catch (IllegalArgumentException ex) {

			// then exact exception
			if (!ex.getMessage().startsWith("location exists: "))
				throw new RuntimeException("wrong exception message: " + ex.getMessage());
		}
	}

	@Test
	public void save_almostDuplicateNameShortVersion() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setName("Inser");
		location.setLongitude(location.getLongitude() + 12f);
		location.setLatitude(location.getLatitude() + 5f);

		// when
		repository.save(location);

		// then no exception
	}

	@Test
	public void save_nearby() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		try {
			this.repository.save(createLocation());
		} catch (IllegalArgumentException ex) {
		}
		final Location location = createLocation();
		location.setName("abc");
		location.setLongitude(location.getLongitude() + 0.01f);
		location.setLatitude(location.getLatitude() - 0.01f);

		// when
		repository.save(location);

		// then no exception
	}

	private Location createLocation() throws Exception {
		final Location location = new Location();
		location.setLongitude(1f);
		location.setLatitude(1f);
		location.setName("Test The Location Insert Update");
		location.setAddress("Melchiorstr. 6\n81479 München");
		location.setStreet("Melchiorstr.");
		location.setNumber("6");
		location.setTown("München");
		location.setZipCode("81479");
		return location;
	}
}
