select
	event.id,
	location.name,
	location.latitude,
	location.longitude,
	'' as geolocationDistance
FROM
	Event event left join Location location on event.locationId=location.id,
	Contact contact
WHERE
	TO_DAYS(event.startDate)-14<=TO_DAYS(current_timestamp) and
	TO_DAYS(event.endDate)>=TO_DAYS(current_timestamp) and
	(
		(REGEXP_LIKE(event.skills, '{USERSKILLS}')=1 or REGEXP_LIKE(event.skillsText, '{USERSKILLSTEXT}')=1) and
		(
			event.price>0 or
			{USERGENDER}=1 and contact.ageMale like '%,%' and {USERAGE}>=substring(contact.ageMale,1,2) and {USERAGE}<=substring(contact.ageMale,4,2) or
			{USERGENDER}=2 and contact.ageFemale like '%,%' and {USERAGE}>=substring(contact.ageFemale,1,2) and {USERAGE}<=substring(contact.ageFemale,4,2) or
			{USERGENDER}=3 and contact.ageDivers like '%,%' and {USERAGE}>=substring(contact.ageDivers,1,2) and {USERAGE}<=substring(contact.ageDivers,4,2)
		)
	) and
	event.contactId=contact.id and
	{search}
GROUP BY
	event.id,
	contact.id