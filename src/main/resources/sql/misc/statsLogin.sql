SELECT
	log.contactId,
	log.createdAt
FROM
	Log log
WHERE
	contact.clientId={CLIENTID} and
	log.uri='/authentication/login' and
	TO_DAYS(log.createdAt)>TO_DAYS(current_timestamp)-90
GROUP BY
	TO_DAYS(log.createdAt),
	log.contactId
ORDER BY
	TO_DAYS(log.createdAt)