SELECT
	contact.age,
	contact.aboutMe,
	contact.authenticate,
	contact.description,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.id,
	contact.idDisplay,
	contact.image,
	contact.imageList,
	contact.latitude,
	contact.longitude,
	contact.pseudonym,
	contact.rating,
	contactVisit.id,
	contactVisit.count,
	contactVisit.createdAt,
	contactVisit.modifiedAt,
	contactLink.status,
	case when contactVisit.modifiedAt is null then contactVisit.createdAt else contactVisit.modifiedAt end as time,
	'' as geolocationDistance
FROM
	ContactVisit contactVisit,
	Contact contact
	left join ContactLink contactLink on contactLink.contactId={USERID} and
	contactLink.contactId2=contact.id or contactLink.contactId2={USERID} and
	contactLink.contactId=contact.id
WHERE
	contact.id<>{USERID} and contact.verified=true and
	{search}
ORDER BY
	time DESC