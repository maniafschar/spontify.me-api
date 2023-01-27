package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.Transient;

import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

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
	private String description;
	private String email;
	private String image;
	private String imageList;
	private String marketingMail;
	private String name;
	private String street;
	private String number;
	private String subcategories;
	private String telephone;
	private String town;
	private String url;
	private String urlInternal;
	private String zipCode;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getAddress2() {
		return address2;
	}

	public void setAddress2(String address2) {
		this.address2 = address2;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(String imageList) {
		this.imageList = imageList;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getTown() {
		return town;
	}

	public void setTown(String town) {
		this.town = town;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getUrlInternal() {
		return urlInternal;
	}

	public void setUrlInternal(String urlInternal) {
		this.urlInternal = urlInternal;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getMarketingMail() {
		return marketingMail;
	}

	public void setMarketingMail(String marketingMail) {
		this.marketingMail = marketingMail;
	}

	public String getTelephone() {
		return telephone;
	}

	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getSubcategories() {
		return subcategories;
	}

	public void setSubcategories(String subcategories) {
		this.subcategories = subcategories;
	}

	public void setLatitude(Float latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(Float longitude) {
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

	public void setGoogleRating(Float googleRating) {
		this.googleRating = googleRating;
	}

	public Integer getGoogleRatingTotal() {
		return googleRatingTotal;
	}

	public void setGoogleRatingTotal(Integer googleRatingTotal) {
		this.googleRatingTotal = googleRatingTotal;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		if (user.equals(getContactId()))
			return true;
		final QueryParams params = new QueryParams(Query.location_list);
		params.setUser(repository.one(Contact.class, user));
		params.setSearch("location.contactId=" + user);
		return repository.list(params).size() > 4;
	}
}