package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class Location extends BaseEntity {
	private BigInteger contactId;
	private Float googleRating;
	private Float latitude;
	private Float longitude;
	private Integer googleRatingTotal;
	private Short rating;
	private String address;
	private String address2;
	private String category;
	private String country;
	@Column(columnDefinition = "TEXT")
	private String description;
	private String email;
	private String image;
	private String imageList;
	private String marketingMail;
	private String name;
	private String street;
	private String number;
	private String secret;
	private String skills;
	private String skillsText;
	private String subcategories;
	private String telephone;
	private String town;
	private String url;
	private String zipCode;
	private Timestamp updatedAt;

	public BigInteger getContactId() {
		return this.contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getCategory() {
		return this.category;
	}

	public void setCategory(final String category) {
		this.category = category;
	}

	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getAddress() {
		return this.address;
	}

	public void setAddress(final String address) {
		this.address = address;
	}

	public String getAddress2() {
		return this.address2;
	}

	public void setAddress2(final String address2) {
		this.address2 = address2;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getImage() {
		return this.image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getImageList() {
		return this.imageList;
	}

	public void setImageList(final String imageList) {
		this.imageList = imageList;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getZipCode() {
		return this.zipCode;
	}

	public void setZipCode(final String zipCode) {
		this.zipCode = zipCode;
	}

	public String getTown() {
		return this.town;
	}

	public void setTown(final String town) {
		this.town = town;
	}

	public String getSecret() {
		return this.secret;
	}

	public void setSecret(final String secret) {
		this.secret = secret;
	}

	public String getStreet() {
		return this.street;
	}

	public void setStreet(final String street) {
		this.street = street;
	}

	public String getNumber() {
		return this.number;
	}

	public void setNumber(final String number) {
		this.number = number;
	}

	public String getCountry() {
		return this.country;
	}

	public void setCountry(final String country) {
		this.country = country;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public String getMarketingMail() {
		return this.marketingMail;
	}

	public void setMarketingMail(final String marketingMail) {
		this.marketingMail = marketingMail;
	}

	public String getTelephone() {
		return this.telephone;
	}

	public void setTelephone(final String telephone) {
		this.telephone = telephone;
	}

	public Short getRating() {
		return this.rating;
	}

	public void setRating(final Short rating) {
		this.rating = rating;
	}

	public String getSubcategories() {
		return this.subcategories;
	}

	public void setSubcategories(final String subcategories) {
		this.subcategories = subcategories;
	}

	public String getSkills() {
		return this.skills;
	}

	public void setSkills(final String skills) {
		this.skills = skills;
	}

	public String getSkillsText() {
		return this.skillsText;
	}

	public void setSkillsText(final String skillsText) {
		this.skillsText = skillsText;
	}

	public void setLatitude(final Float latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(final Float longitude) {
		this.longitude = longitude;
	}

	public Float getLatitude() {
		return this.latitude;
	}

	public Float getLongitude() {
		return this.longitude;
	}

	public Float getGoogleRating() {
		return this.googleRating;
	}

	public void setGoogleRating(final Float googleRating) {
		this.googleRating = googleRating;
	}

	public Integer getGoogleRatingTotal() {
		return this.googleRatingTotal;
	}

	public void setGoogleRatingTotal(final Integer googleRatingTotal) {
		this.googleRatingTotal = googleRatingTotal;
	}

	public Timestamp getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(final Timestamp updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		if (user.equals(this.getContactId()))
			return true;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch("location.contactId=" + user);
		return repository.list(params).size() > 4;
	}
}
