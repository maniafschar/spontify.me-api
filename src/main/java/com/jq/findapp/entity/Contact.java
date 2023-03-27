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
	private Boolean active = true;
	private Boolean authenticate = false;
	private Boolean bluetooth = true;
	private Boolean notificationEngagement = true;
	private Boolean notificationBirthday = true;
	private Boolean notificationChat = true;
	private Boolean notificationFriendRequest = true;
	private Boolean notificationMarkEvent = true;
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
		admin, clientAdmin
	}

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(BigInteger clientId) {
		this.clientId = clientId;
	}

	public OS getOs() {
		return os;
	}

	public void setOs(OS os) {
		this.os = os;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(String emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getImageAuthenticate() {
		return imageAuthenticate;
	}

	public void setImageAuthenticate(String imageAuthenticate) {
		this.imageAuthenticate = imageAuthenticate;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(String imageList) {
		this.imageList = imageList;
	}

	public Boolean getBluetooth() {
		return bluetooth;
	}

	public void setBluetooth(Boolean bluetooth) {
		this.bluetooth = bluetooth;
	}

	public Short getFee() {
		return fee;
	}

	public void setFee(Short fee) {
		this.fee = fee;
	}

	public Date getFeeDate() {
		return feeDate;
	}

	public void setFeeDate(Date feeDate) {
		this.feeDate = feeDate;
	}

	public Date getBirthday() {
		return birthday;
	}

	public void setBirthday(Date birthday) {
		this.birthday = birthday;
	}

	public Boolean getVerified() {
		return verified;
	}

	public void setVerified(Boolean verified) {
		this.verified = verified;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Boolean getAuthenticate() {
		return authenticate;
	}

	public void setAuthenticate(Boolean authenticate) {
		this.authenticate = authenticate;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getIdDisplay() {
		return idDisplay;
	}

	public void setIdDisplay(String idDisplay) {
		this.idDisplay = idDisplay;
	}

	public Timestamp getVisitPage() {
		return visitPage;
	}

	public void setVisitPage(Timestamp visitPage) {
		this.visitPage = visitPage;
	}

	public Timestamp getRecommend() {
		return recommend;
	}

	public void setRecommend(Timestamp recommend) {
		this.recommend = recommend;
	}

	public Short getAge() {
		return age;
	}

	public void setAge(Short age) {
		this.age = age;
	}

	public String getFacebookId() {
		return facebookId;
	}

	public void setFacebookId(String facebookId) {
		this.facebookId = facebookId;
	}

	public String getUrls() {
		return urls;
	}

	public void setUrls(String urls) {
		this.urls = urls;
	}

	public String getFbToken() {
		return fbToken;
	}

	public void setFbToken(String fbToken) {
		this.fbToken = fbToken;
	}

	public Short getBirthdayDisplay() {
		return birthdayDisplay;
	}

	public void setBirthdayDisplay(Short birthdayDisplay) {
		this.birthdayDisplay = birthdayDisplay;
	}

	public Short getGender() {
		return gender;
	}

	public void setGender(Short gender) {
		this.gender = gender;
	}

	public String getLoginLink() {
		return loginLink;
	}

	public void setLoginLink(String loginLink) {
		this.loginLink = loginLink;
	}

	public String getPushToken() {
		return pushToken;
	}

	public void setPushToken(String pushToken) {
		this.pushToken = pushToken;
	}

	public String getPushSystem() {
		return pushSystem;
	}

	public void setPushSystem(String pushSystem) {
		this.pushSystem = pushSystem;
	}

	public Boolean getNotificationFriendRequest() {
		return notificationFriendRequest;
	}

	public void setNotificationFriendRequest(Boolean notificationFriendRequest) {
		this.notificationFriendRequest = notificationFriendRequest;
	}

	public Boolean getNotificationChat() {
		return notificationChat;
	}

	public void setNotificationChat(Boolean notificationChat) {
		this.notificationChat = notificationChat;
	}

	public Boolean getNotificationBirthday() {
		return notificationBirthday;
	}

	public void setNotificationBirthday(Boolean notificationBirthday) {
		this.notificationBirthday = notificationBirthday;
	}

	public Boolean getNotificationVisitProfile() {
		return notificationVisitProfile;
	}

	public void setNotificationVisitProfile(Boolean notificationVisitProfile) {
		this.notificationVisitProfile = notificationVisitProfile;
	}

	public Boolean getNotificationVisitLocation() {
		return notificationVisitLocation;
	}

	public void setNotificationVisitLocation(Boolean notificationVisitLocation) {
		this.notificationVisitLocation = notificationVisitLocation;
	}

	public Boolean getNotificationMarkEvent() {
		return notificationMarkEvent;
	}

	public void setNotificationMarkEvent(Boolean notificationMarkEvent) {
		this.notificationMarkEvent = notificationMarkEvent;
	}

	public String getAgeMale() {
		return ageMale;
	}

	public void setAgeMale(String ageMale) {
		this.ageMale = ageMale;
	}

	public String getAgeFemale() {
		return ageFemale;
	}

	public void setAgeFemale(String ageFemale) {
		this.ageFemale = ageFemale;
	}

	public String getPseudonym() {
		return pseudonym;
	}

	public void setPseudonym(String pseudonym) {
		this.pseudonym = pseudonym;
	}

	public Boolean getSearch() {
		return search;
	}

	public void setSearch(Boolean search) {
		this.search = search;
	}

	public Boolean getTeaser() {
		return teaser;
	}

	public void setTeaser(Boolean teaser) {
		this.teaser = teaser;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public void setLongitude(Float longitude) {
		this.longitude = longitude;
	}

	public void setLatitude(Float latitude) {
		this.latitude = latitude;
	}

	public String getAppleId() {
		return appleId;
	}

	public void setAppleId(String appleId) {
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

	public void setPassword(String password) {
		this.password = password;
	}

	public String getAgeDivers() {
		return ageDivers;
	}

	public void setAgeDivers(String ageDivers) {
		this.ageDivers = ageDivers;
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(Device device) {
		this.device = device;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public void setPasswordReset(Long passwordReset) {
		this.passwordReset = passwordReset;
	}

	public Long getPasswordReset() {
		return passwordReset;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public Timestamp getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(Timestamp lastLogin) {
		this.lastLogin = lastLogin;
	}

	public Boolean getNotificationEngagement() {
		return notificationEngagement;
	}

	public void setNotificationEngagement(Boolean notificationEngagement) {
		this.notificationEngagement = notificationEngagement;
	}

	public String getSkills() {
		return skills;
	}

	public void setSkills(String skills) {
		this.skills = skills;
	}

	public String getSkillsText() {
		return skillsText;
	}

	public void setSkillsText(String skillsText) {
		this.skillsText = skillsText;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getId());
	}
}