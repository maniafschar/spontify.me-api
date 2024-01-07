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
	TO_DAYS(event.endDate)>=TO_DAYS(current_timestamp) and
	(
		(cast(REGEXP_LIKE(event.skills, '{USERSKILLS}') as integer)=1 or cast(REGEXP_LIKE(event.skillsText, '{USERSKILLSTEXT}') as integer)=1) and
		(
			event.price>0 or
			{USERGENDER}=1 and contact.ageMale like '%,%' and {USERAGE}>=cast(substring(contact.ageMale,1,2) as integer) and {USERAGE}<=cast(substring(contact.ageMale,4,2) as integer) or
			{USERGENDER}=2 and contact.ageFemale like '%,%' and {USERAGE}>=cast(substring(contact.ageFemale,1,2) as integer) and {USERAGE}<=cast(substring(contact.ageFemale,4,2) as integer) or
			{USERGENDER}=3 and contact.ageDivers like '%,%' and {USERAGE}>=cast(substring(contact.ageDivers,1,2) as integer) and {USERAGE}<=cast(substring(contact.ageDivers,4,2) as integer)
		)
	) and
	event.contactId=contact.id and
	contact.clientId={CLIENTID} and
	{search}
GROUP BY
	event.id,
	contact.id