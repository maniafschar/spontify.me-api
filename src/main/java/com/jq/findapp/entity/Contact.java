package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;

@Entity
public class Contact extends BaseEntity {
	private BigInteger clientId;
	private Boolean authenticate = false;
	private Boolean bluetooth = true;
	private Boolean notificationBirthday = true;
	private Boolean notificationChat = true;
	private Boolean notificationEngagement = true;
	private Boolean notificationFriendRequest = true;
	private Boolean notificationMarkEvent = true;
	private Boolean notificationNews = true;
	private Boolean notificationVisitLocation = true;
	private Boolean notificationVisitProfile = true;
	private Boolean search = true;
	private Boolean teaser = true;
	private Boolean verified = false;
	private Date birthday;
	private Date feeDate;
	@Enumerated(EnumType.STRING)
	private Device device;
	@Enumerated(EnumType.STRING)
	private OS os;
	@Enumerated(EnumType.STRING)
	private ContactType type;
	private Timestamp lastLogin;
	private Timestamp recommend;
	private Timestamp visitPage;
	private Float latitude;
	private Float longitude;
	private Long passwordReset;
	private Short age;
	private Short birthdayDisplay;
	private Short fee;
	private Short gender;
	private Short rating;
	private String ageDivers;
	private String ageFemale;
	private String ageMale;
	private String appleId;
	private String clubs;
	private String description;
	private String email;
	private String emailVerified;
	private String facebookId;
	private String fbToken;
	private String idDisplay;
	private String image;
	private String imageAuthenticate;
	private String imageList;
	private String language;
	private String loginLink;
	@Column(columnDefinition = "TEXT")
	private String password;
	private String pseudonym;
	private String pushSystem;
	private String pushToken;
	private String skills;
	private String skillsText;
	private String storage;
	private String timezone;
	private String urls;
	private String version;

	public enum OS {
		android, ios, web
	}

	public enum Device {
		computer, phone, tablet
	}

	public enum ContactType {
		admin, adminContent, demo
	}

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(final BigInteger clientId) {
		this.clientId = clientId;
	}

	public OS getOs() {
		return os;
	}

	public void setOs(final OS os) {
		this.os = os;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public void setClubs(final String clubs) {
		this.clubs = clubs;
	}

	public String getClubs() {
		return clubs;
	}

	public String getEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(final String emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getImageAuthenticate() {
		return imageAuthenticate;
	}

	public void setImageAuthenticate(final String imageAuthenticate) {
		this.imageAuthenticate = imageAuthenticate;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(final String imageList) {
		this.imageList = imageList;
	}

	public Boolean getBluetooth() {
		return bluetooth;
	}

	public void setBluetooth(final Boolean bluetooth) {
		this.bluetooth = bluetooth;
	}

	public Short getFee() {
		return fee;
	}

	public void setFee(final Short fee) {
		this.fee = fee;
	}

	public Date getFeeDate() {
		return feeDate;
	}

	public void setFeeDate(final Date feeDate) {
		this.feeDate = feeDate;
	}

	public Date getBirthday() {
		return birthday;
	}

	public void setBirthday(final Date birthday) {
		this.birthday = birthday;
	}

	public Boolean getVerified() {
		return verified;
	}

	public void setVerified(final Boolean verified) {
		this.verified = verified;
	}

	public Boolean getAuthenticate() {
		return authenticate;
	}

	public void setAuthenticate(final Boolean authenticate) {
		this.authenticate = authenticate;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(final String language) {
		this.language = language;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getIdDisplay() {
		return idDisplay;
	}

	public void setIdDisplay(final String idDisplay) {
		this.idDisplay = idDisplay;
	}

	public Timestamp getVisitPage() {
		return visitPage;
	}

	public void setVisitPage(final Timestamp visitPage) {
		this.visitPage = visitPage;
	}

	public Timestamp getRecommend() {
		return recommend;
	}

	public void setRecommend(final Timestamp recommend) {
		this.recommend = recommend;
	}

	public Short getAge() {
		return age;
	}

	public void setAge(final Short age) {
		this.age = age;
	}

	public String getFacebookId() {
		return facebookId;
	}

	public void setFacebookId(final String facebookId) {
		this.facebookId = facebookId;
	}

	public String getUrls() {
		return urls;
	}

	public void setUrls(final String urls) {
		this.urls = urls;
	}

	public String getFbToken() {
		return fbToken;
	}

	public void setFbToken(final String fbToken) {
		this.fbToken = fbToken;
	}

	public Short getBirthdayDisplay() {
		return birthdayDisplay;
	}

	public void setBirthdayDisplay(final Short birthdayDisplay) {
		this.birthdayDisplay = birthdayDisplay;
	}

	public Short getGender() {
		return gender;
	}

	public void setGender(final Short gender) {
		this.gender = gender;
	}

	public String getLoginLink() {
		return loginLink;
	}

	public void setLoginLink(final String loginLink) {
		this.loginLink = loginLink;
	}

	public String getPushToken() {
		return pushToken;
	}

	public void setPushToken(final String pushToken) {
		this.pushToken = pushToken;
	}

	public String getPushSystem() {
		return pushSystem;
	}

	public void setPushSystem(final String pushSystem) {
		this.pushSystem = pushSystem;
	}

	public Boolean getNotificationFriendRequest() {
		return notificationFriendRequest;
	}

	public void setNotificationFriendRequest(final Boolean notificationFriendRequest) {
		this.notificationFriendRequest = notificationFriendRequest;
	}

	public Boolean getNotificationChat() {
		return notificationChat;
	}

	public void setNotificationChat(final Boolean notificationChat) {
		this.notificationChat = notificationChat;
	}

	public Boolean getNotificationBirthday() {
		return notificationBirthday;
	}

	public void setNotificationBirthday(final Boolean notificationBirthday) {
		this.notificationBirthday = notificationBirthday;
	}

	public Boolean getNotificationVisitProfile() {
		return notificationVisitProfile;
	}

	public void setNotificationVisitProfile(final Boolean notificationVisitProfile) {
		this.notificationVisitProfile = notificationVisitProfile;
	}

	public Boolean getNotificationVisitLocation() {
		return notificationVisitLocation;
	}

	public void setNotificationVisitLocation(final Boolean notificationVisitLocation) {
		this.notificationVisitLocation = notificationVisitLocation;
	}

	public Boolean getNotificationMarkEvent() {
		return notificationMarkEvent;
	}

	public void setNotificationMarkEvent(final Boolean notificationMarkEvent) {
		this.notificationMarkEvent = notificationMarkEvent;
	}

	public Boolean getNotificationNews() {
		return notificationNews;
	}

	public void setNotificationNews(final Boolean notificationNews) {
		this.notificationNews = notificationNews;
	}

	public String getAgeMale() {
		return ageMale;
	}

	public void setAgeMale(final String ageMale) {
		this.ageMale = ageMale;
	}

	public String getAgeFemale() {
		return ageFemale;
	}

	public void setAgeFemale(final String ageFemale) {
		this.ageFemale = ageFemale;
	}

	public String getPseudonym() {
		return pseudonym;
	}

	public void setPseudonym(final String pseudonym) {
		this.pseudonym = pseudonym;
	}

	public Boolean getSearch() {
		return search;
	}

	public void setSearch(final Boolean search) {
		this.search = search;
	}

	public Boolean getTeaser() {
		return teaser;
	}

	public void setTeaser(final Boolean teaser) {
		this.teaser = teaser;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(final Short rating) {
		this.rating = rating;
	}

	public void setLongitude(final Float longitude) {
		this.longitude = longitude;
	}

	public void setLatitude(final Float latitude) {
		this.latitude = latitude;
	}

	public String getAppleId() {
		return appleId;
	}

	public void setAppleId(final String appleId) {
		this.appleId = appleId;
	}

	public Float getLongitude() {
		return longitude;
	}

	public Float getLatitude() {
		return latitude;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	public String getAgeDivers() {
		return ageDivers;
	}

	public void setAgeDivers(final String ageDivers) {
		this.ageDivers = ageDivers;
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(final Device device) {
		this.device = device;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	public void setPasswordReset(final Long passwordReset) {
		this.passwordReset = passwordReset;
	}

	public Long getPasswordReset() {
		return passwordReset;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(final String timezone) {
		this.timezone = timezone;
	}

	public Timestamp getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(final Timestamp lastLogin) {
		this.lastLogin = lastLogin;
	}

	public Boolean getNotificationEngagement() {
		return notificationEngagement;
	}

	public void setNotificationEngagement(final Boolean notificationEngagement) {
		this.notificationEngagement = notificationEngagement;
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

	public ContactType getType() {
		return type;
	}

	public void setType(final ContactType type) {
		this.type = type;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return user.equals(getId());
	}
}
