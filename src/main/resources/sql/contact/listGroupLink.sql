SELECT
	contact.aboutMe,
	contact.age,
	contact.attrEx,
	contact.attr,
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
	contactGroupLink.contactGroupId,
	contactGroupLink.id,
	contactLink.status,
	'' as geolocationDistance,
	(
		select
			cwtd.message
		from
			ContactWhatToDo cwtd
		where
			cwtd.id=
			(
				select
					max(cwtd2.id)
				from
					ContactWhatToDo cwtd2
				where
					cwtd2.contactId=contact.id and
					cwtd2.active=true and
					cwtd2.time>current_timestamp
			)
	) as contactWhatToDoMessage
FROM
	ContactGroupLink contactGroupLink,
	Contact contact
	left join ContactLink contactLink on contactLink.contactId={USERID} and
	contactLink.contactId2=contact.id or contactLink.contactId2={USERID} and
	contactLink.contactId=contact.id
WHERE
	contactGroupLink.contactId2=contact.id and
	{search}