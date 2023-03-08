SELECT
	contact.videoCall,
	case when contact.id={USERID} then contact.id else null end as id
FROM
	Contact contact
WHERE
	{search}