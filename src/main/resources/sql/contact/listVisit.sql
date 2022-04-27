SELECT
	contact.age,
	contact.aboutMe,
	contact.attr,
	contact.attrEx,
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
	contactVisit.id,
	contactVisit.count,
	contactVisit.modifiedAt,
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
	ContactVisit contactVisit,
	Contact contact
	left join ContactLink contactLink on contactLink.contactId={USERID} and
	contactLink.contactId2=contact.id or contactLink.contactId2={USERID} and
	contactLink.contactId=contact.id
WHERE
	{search}
ORDER BY
	contactVisit.modifiedAt DESC