SELECT
	contact.age,
	contact.ageDivers,
	contact.ageFemale,
	contact.ageMale,
	contact.aboutMe,
	contact.birthday,
	contact.birthdayDisplay,
	contact.bluetooth,
	contact.gender,
	contact.id,
	contact.idDisplay,
	contact.image,
	contact.imageList,
	contact.language,
	contact.latitude,
	contact.longitude,
	contact.notificationBirthday,
	contact.notificationChat,
	contact.notificationEngagement,
	contact.notificationFriendRequest,
	contact.notificationMarkEvent,
	contact.notificationVisitLocation,
	contact.notificationVisitProfile,
	contact.paypalMerchantId,
	contact.pseudonym,
	contact.rating,
	contact.search,
	contact.skills,
	contact.skillsText,
	contact.storage,
	contact.verified,
	contact.visitPage,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	'' as geolocationDistance
FROM
	Contact contact left join
	ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	contact.verified=1 and
	{search}