package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Transient;

import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.listener.LocationListener;

@Entity
@EntityListeners(LocationListener.class)
public class Location extends BaseEntity {
	private BigInteger contactId;
	private BigInteger ownerId;
	private Boolean openTimesBankholiday;
	private Date paymentDate;
	private Date urlActive;
	private Float googleRating;
	private Float latitude;
	private Float longitude;
	private Float paymentAmount;
	private Integer googleRatingTotal;
	private Short rating;
	private String address;
	private String address2;
	private String attr0;
	private String attr0Ex;
	private String attr1;
	private String attr1Ex;
	private String attr2;
	private String attr2Ex;
	private String attr3;
	private String attr3Ex;
	private String attr4;
	private String attr4Ex;
	private String attr5;
	private String attr5Ex;
	private String bonus;
	private String budget;
	private String category;
	private String country;
	private String description;
	private String email;
	private String image;
	private String imageList;
	private String marketingMail;
	private String name;
	private String openTimesText;
	private String parkingOption;
	private String parkingText;
	private String street;
	private String number;
	private String subcategories;
	private String telephone;
	private String town;
	private String url;
	private String urlInternal;
	private String zipCode;

	public String getBudget() {
		return budget;
	}

	public void setBudget(String budget) {
		this.budget = budget;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(BigInteger ownerId) {
		this.ownerId = ownerId;
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

	public Date getPaymentDate() {
		return paymentDate;
	}

	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}

	public Date getUrlActive() {
		return urlActive;
	}

	public void setUrlActive(Date urlActive) {
		this.urlActive = urlActive;
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

	public String getParkingText() {
		return parkingText;
	}

	public void setParkingText(String parkingText) {
		this.parkingText = parkingText;
	}

	public String getParkingOption() {
		return parkingOption;
	}

	public void setParkingOption(String parkingOption) {
		this.parkingOption = parkingOption;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getOpenTimesText() {
		return openTimesText;
	}

	public void setOpenTimesText(String openTimesText) {
		this.openTimesText = openTimesText;
	}

	public Boolean getOpenTimesBankholiday() {
		return openTimesBankholiday;
	}

	public void setOpenTimesBankholiday(Boolean openTimesBankholiday) {
		this.openTimesBankholiday = openTimesBankholiday;
	}

	public String getSubcategories() {
		return subcategories;
	}

	public void setSubcategories(String subcategories) {
		this.subcategories = subcategories;
	}

	public void setPaymentAmount(Float paymentAmount) {
		this.paymentAmount = paymentAmount;
	}

	public void setLatitude(Float latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(Float longitude) {
		this.longitude = longitude;
	}

	public String getBonus() {
		return bonus;
	}

	public void setBonus(String bonus) {
		this.bonus = bonus;
	}

	public Float getPaymentAmount() {
		return paymentAmount;
	}

	public Float getLatitude() {
		return latitude;
	}

	public Float getLongitude() {
		return longitude;
	}

	public String getAttr0Ex() {
		return attr0Ex;
	}

	public void setAttr0Ex(String attr0Ex) {
		this.attr0Ex = attr0Ex;
	}

	public String getAttr1Ex() {
		return attr1Ex;
	}

	public void setAttr1Ex(String attr1Ex) {
		this.attr1Ex = attr1Ex;
	}

	public String getAttr2Ex() {
		return attr2Ex;
	}

	public void setAttr2Ex(String attr2Ex) {
		this.attr2Ex = attr2Ex;
	}

	public String getAttr3Ex() {
		return attr3Ex;
	}

	public void setAttr3Ex(String attr3Ex) {
		this.attr3Ex = attr3Ex;
	}

	public String getAttr4Ex() {
		return attr4Ex;
	}

	public void setAttr4Ex(String attr4Ex) {
		this.attr4Ex = attr4Ex;
	}

	public String getAttr5Ex() {
		return attr5Ex;
	}

	public void setAttr5Ex(String attr5Ex) {
		this.attr5Ex = attr5Ex;
	}

	public String getAttr0() {
		return attr0;
	}

	public void setAttr0(String attr0) {
		this.attr0 = attr0;
	}

	public String getAttr1() {
		return attr1;
	}

	public void setAttr1(String attr1) {
		this.attr1 = attr1;
	}

	public String getAttr2() {
		return attr2;
	}

	public void setAttr2(String attr2) {
		this.attr2 = attr2;
	}

	public String getAttr3() {
		return attr3;
	}

	public void setAttr3(String attr3) {
		this.attr3 = attr3;
	}

	public String getAttr4() {
		return attr4;
	}

	public void setAttr4(String attr4) {
		this.attr4 = attr4;
	}

	public String getAttr5() {
		return attr5;
	}

	public void setAttr5(String attr5) {
		this.attr5 = attr5;
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
		if (user.equals(getOwnerId()))
			return true;
		if (getOwnerId() != null)
			return false;
		if (user.equals(getContactId()))
			return true;
		final QueryParams params = new QueryParams(Query.location_list);
		params.setUser(repository.one(Contact.class, user));
		params.setSearch("location.contactId=" + user);
		return repository.list(params).size() > 4;
	}
}