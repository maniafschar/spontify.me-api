package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.listener.ContactListener;

@Entity
@EntityListeners(ContactListener.class)
public class Contact extends BaseEntity {
	private Boolean active = true;
	private Boolean findMe = true;
	private Boolean guide = false;
	private Boolean notificationEngagement = true;
	private Boolean notificationBirthday = true;
	private Boolean notificationChat = true;
	private Boolean notificationFriendRequest = true;
	private Boolean notificationMarkEvent = true;
	private Boolean notificationVisitLocation = true;
	private Boolean notificationVisitProfile = true;
	private Boolean search = true;
	private Boolean verified = false;
	private Date birthday;
	@Enumerated(EnumType.STRING)
	private Device device;
	@Enumerated(EnumType.STRING)
	private OS os;
	private Timestamp lastLogin;
	private Timestamp visitPage;
	private Float latitude;
	private Float longitude;
	private Long passwordReset;
	private Short age;
	private Short birthdayDisplay;
	private Short gender;
	private Short rating;
	private String aboutMe;
	private String ageDivers;
	private String ageFemale;
	private String ageMale;
	private String appleId;
	private String attr;
	private String attrEx;
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
	private String attrInterest;
	private String attrInterestEx;
	private String budget;
	private String email;
	private String emailVerified;
	private String facebookId;
	private String fbToken;
	private String filter;
	private String idDisplay;
	private String image;
	private String imageList;
	private String language;
	private String loginLink;
	@Column(columnDefinition = "TEXT")
	private String password;
	private String paypalMerchantId;
	private String pseudonym;
	private String pushSystem;
	private String pushToken;
	private String storage;
	private String timezone;
	private String version;

	public enum OS {
		android, ios, web
	}

	public enum Device {
		computer, phone, tablet
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

	public String getImageList() {
		return imageList;
	}

	public void setImageList(String imageList) {
		this.imageList = imageList;
	}

	public Boolean getFindMe() {
		return findMe;
	}

	public void setFindMe(Boolean findMe) {
		this.findMe = findMe;
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

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getAboutMe() {
		return aboutMe;
	}

	public void setAboutMe(String aboutMe) {
		this.aboutMe = aboutMe;
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

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public Boolean getSearch() {
		return search;
	}

	public void setSearch(Boolean search) {
		this.search = search;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	public String getBudget() {
		return budget;
	}

	public void setBudget(String budget) {
		this.budget = budget;
	}

	public String getAttrInterest() {
		return attrInterest;
	}

	public void setAttrInterest(String attrInterest) {
		this.attrInterest = attrInterest;
	}

	public String getAttr() {
		return attr;
	}

	public void setAttr(String attr) {
		this.attr = attr;
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

	public String getAttrInterestEx() {
		return attrInterestEx;
	}

	public void setAttrInterestEx(String attrInterestEx) {
		this.attrInterestEx = attrInterestEx;
	}

	public String getAttrEx() {
		return attrEx;
	}

	public void setAttrEx(String attrEx) {
		this.attrEx = attrEx;
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

	public Boolean getGuide() {
		return guide;
	}

	public void setGuide(Boolean guide) {
		this.guide = guide;
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

	public String getPaypalMerchantId() {
		return paypalMerchantId;
	}

	public void setPaypalMerchantId(String paypalMerchantId) {
		this.paypalMerchantId = paypalMerchantId;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getId());
	}
}