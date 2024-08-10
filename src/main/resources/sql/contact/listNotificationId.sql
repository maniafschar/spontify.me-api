SELECT
	contact.id,
	contactNotification.id
FROM
	Contact contact left join ContactNotification contactNotification on contact.id=contactNotification.contactId
	and contactNotification.action='{search}'
WHERE
	contact.clientId={CLIENTID} and contactNotification.id is null
GROUP BY
	contact.id,
	contactNotification.id