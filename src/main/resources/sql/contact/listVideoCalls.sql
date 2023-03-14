SELECT
	contactVideoCall.time,
	contactVideoCall.type,
	case when contactVideoCall.id={USERID} then contactVideoCall.id else null end as id
FROM
	ContactVideoCall contactVideoCall
WHERE
	{search}
ORDER BY
	contactVideoCall.id DESC