package com.jq.findapp.entity;

import java.math.BigInteger;

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
	private String urlInternal;
	private String zipCode;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(final String category) {
		this.category = category;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(final String address) {
		this.address = address;
	}

	public String getAddress2() {
		return address2;
	}

	public void setAddress2(final String address2) {
		this.address2 = address2;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(final String imageList) {
		this.imageList = imageList;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(final String zipCode) {
		this.zipCode = zipCode;
	}

	public String getTown() {
		return town;
	}

	public void setTown(final String town) {
		this.town = town;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(final String secret) {
		this.secret = secret;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(final String street) {
		this.street = street;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(final String number) {
		this.number = number;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(final String country) {
		this.country = country;
	}

	public String getUrlInternal() {
		return urlInternal;
	}

	public void setUrlInternal(final String urlInternal) {
		this.urlInternal = urlInternal;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public String getMarketingMail() {
		return marketingMail;
	}

	public void setMarketingMail(final String marketingMail) {
		this.marketingMail = marketingMail;
	}

	public String getTelephone() {
		return telephone;
	}

	public void setTelephone(final String telephone) {
		this.telephone = telephone;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(final Short rating) {
		this.rating = rating;
	}

	public String getSubcategories() {
		return subcategories;
	}

	public void setSubcategories(final String subcategories) {
		this.subcategories = subcategories;
	}

	public String getSkills() {
		return skills;
	}

	public void setSkills(final String skills) {
		this.skills = skills;
	}

	public String getSkillsText() {
		return skillsText;
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
		return latitude;
	}

	public Float getLongitude() {
		return longitude;
	}

	public Float getGoogleRating() {
		return googleRating;
	}

	public void setGoogleRating(final Float googleRating) {
		this.googleRating = googleRating;
	}

	public Integer getGoogleRatingTotal() {
		return googleRatingTotal;
	}

	public void setGoogleRatingTotal(final Integer googleRatingTotal) {
		this.googleRatingTotal = googleRatingTotal;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		if (user.equals(getContactId()))
			return true;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch("location.contactId=" + user);
		return repository.list(params).size() > 4;
	}
}
