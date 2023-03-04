SELECT
	contact.age,
	contact.ageDivers,
	contact.ageFemale,
	contact.ageMale,
	contact.aboutMe,
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
	contactGroupLink.contactGroupId,
	contactGroupLink.id,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	'' as geolocationDistance
FROM
	ContactGroupLink contactGroupLink,
	Contact contact
	left join ContactLink contactLink on contactLink.contactId={USERID} and
	contactLink.contactId2=contact.id or contactLink.contactId2={USERID} and
	contactLink.contactId=contact.id
WHERE
	contactGroupLink.contactId2=contact.id and
	{search}