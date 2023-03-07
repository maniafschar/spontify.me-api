SELECT
	contact.id,
	contact.authenticate,
	contact.birthday,
	contact.image,
	contact.imageList,
	contact.language,
	contact.pseudonym,
	contact.aboutMe,
	contact.description,
	contact.idDisplay,
	contact.age,
	contact.birthdayDisplay,
	contact.gender,
	contact.rating,
	block.id,
	block.note,
	block.reason
FROM
	Contact contact,
	Block block
WHERE
	block.contactId2=contact.id and
	block.contactId={USERID}