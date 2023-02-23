SELECT
	contact.age,
	contact.aboutMe,
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
	contact.skills,
	contact.skillsText,
	'' as geolocationDistance
FROM
	Contact contact
WHERE
	contact.imageList is not null and
	{search}