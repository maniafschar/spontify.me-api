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
	contactBlock.id,
	contactBlock.note,
	contactBlock.reason 
FROM
	Contact contact,
	ContactBlock contactBlock
WHERE
	contactBlock.contactId2=contact.id and
	contactBlock.contactId={USERID}