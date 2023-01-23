select
	event.category,
	event.id,
	event.type,
	event.confirm,
	event.startDate,
	event.endDate,
	event.text,
	event.image,
	event.imageList,
	event.price,
	event.maxParticipants,
	event.visibility,
	contact.id,
	contact.pseudonym,
	contact.age,
	contact.aboutMe,
	contact.skills,
	contact.skillsText,
	contact.birthday,
	contact.birthdayDisplay,
	contact.gender,
	contact.imageList,
	contact.latitude,
	contact.longitude,
	contactLink.contactId,
	contactLink.contactId2,
	contactLink.id,
	contactLink.status,
	location.id,
	location.category,
	location.name,
	location.town,
	location.description,
	location.image,
	location.imageList,
	location.latitude,
	location.longitude,
	location.address,
	locationFavorite.id,
	locationFavorite.favorite,
	'' as geolocationDistance
FROM
	Event event left join EventParticipate eventParticipate on eventParticipate.contactId={USERID} and eventParticipate.eventId=event.id left join
	Location location on event.locationId=location.id left join
	LocationFavorite locationFavorite on locationFavorite.locationId=location.id and locationFavorite.contactId={USERID},
	Contact contact left join
	ContactLink contactLink on
		contactLink.contactId={USERID} and contactLink.contactId2=contact.id
	or
		contactLink.contactId2={USERID} and contactLink.contactId=contact.id
WHERE
	TO_DAYS(event.startDate)-14<=TO_DAYS(current_timestamp) and
	TO_DAYS(event.endDate)>=TO_DAYS(current_timestamp) and
	(
		event.visibility=3 or 
		event.contactId={USERID} or
		(
			select cl from ContactLink cl where cl.status=1 and (cl.contactId={USERID} and cl.contactId2=event.contactId or cl.contactId2={USERID} and cl.contactId=event.contactId)
		) is not null or
		event.visibility=2 and
		(REGEXP_LIKE(contact.skills, '{USERSKILLS}')=1 or REGEXP_LIKE(contact.skillsText, '{USERSKILLSTEXT}')=1) and
		(
			{USERGENDER}=1 and contact.ageMale like '%,%' and {USERAGE}>=substring(contact.ageMale,1,2) and {USERAGE}<=substring(contact.ageMale,4,2) or
			{USERGENDER}=2 and contact.ageFemale like '%,%' and {USERAGE}>=substring(contact.ageFemale,1,2) and {USERAGE}<=substring(contact.ageFemale,4,2) or
			{USERGENDER}=3 and contact.ageDivers like '%,%' and {USERAGE}>=substring(contact.ageDivers,1,2) and {USERAGE}<=substring(contact.ageDivers,4,2)
		)
	) and
	event.contactId=contact.id and
	{search}
GROUP BY
	event.id,
	contact.id,
	locationFavorite.id