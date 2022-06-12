SELECT
	contact.aboutMe,
	contact.age,
	contact.attr,
	contact.attrEx,
	contact.attr0,
	contact.attr0Ex,
	contact.attr1,
	contact.attr1Ex,
	contact.attr2,
	contact.attr2Ex,
	contact.attr3,
	contact.attr3Ex,
	contact.attr4,
	contact.attr4Ex,
	contact.attr5,
	contact.attr5Ex,
	contact.attrInterest,
	contact.attrInterestEx,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.guide,
	contact.id,
	contact.idDisplay,
	contact.image,
	contact.imageList,
	contact.latitude,
	contact.longitude,
	contact.pseudonym,
	contact.rating,
	contactNotification.id,
	contactNotification.text,
	contactNotification.action,
	contactNotification.createdAt,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	'' as geolocationDistance
FROM
	Contact contact
	left join ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id 
	or 
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id,
	ContactNotification contactNotification
WHERE
	contactNotification.contactId2=contact.id and
	contactNotification.contactId={USERID} and
	contactNotification.contactId2=contact.id and
	{search}
GROUP BY
	contactNotification.id
ORDER BY
	contactNotification.createdAt DESC