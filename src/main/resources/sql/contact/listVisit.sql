SELECT
	contact.age,
	contact.aboutMe,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.budget,
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
	contactVisit.modifiedAt,
	contactLink.status,
	'' as geolocationDistance
FROM
	ContactVisit contactVisit,
	Contact contact
	left join ContactLink contactLink on contactLink.contactId={USERID} and
	contactLink.contactId2=contact.id or contactLink.contactId2={USERID} and
	contactLink.contactId=contact.id
WHERE
	contact.id<>{USERID} and contact.verified=1 and
	{search}
ORDER BY
	contactVisit.modifiedAt DESC