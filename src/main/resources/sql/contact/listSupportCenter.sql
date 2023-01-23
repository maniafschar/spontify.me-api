SELECT
	contact.age,
	contact.ageDivers,
	contact.ageFemale,
	contact.ageMale,
	contact.aboutMe,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.createdAt,
	contact.device,
	contact.gender,
	contact.id,
	contact.idDisplay,
	contact.image,
	contact.language,
	contact.lastLogin,
	contact.modifiedAt,
	contact.os,
	contact.pseudonym,
	contact.rating,
	contact.search,
	contact.verified,
	contact.version
FROM
	Contact contact
WHERE
	{search}
ORDER BY
	contact.id DESC