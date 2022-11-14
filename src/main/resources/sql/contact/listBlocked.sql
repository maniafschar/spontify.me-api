SELECT
	contact.id,
	contact.birthday,
	contact.image,
	contact.imageList,
	contact.language,
	contact.pseudonym,
	contact.aboutMe,
	contact.idDisplay,
	contact.age,
	contact.birthdayDisplay,
	contact.gender,
	contact.attr,
	contact.attrEx,
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