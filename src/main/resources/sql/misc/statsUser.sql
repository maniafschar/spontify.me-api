SELECT
	((count(*)*1.0)/(select count(*) from Contact where clientId={CLIENTID})) as count,
	count(*) as c,
	(select count(*) from Contact where contact.clientId={CLIENTID}) as cototalunt,
	contact.gender,
	contact.verified,
	case when contact.image is null then '' else 'y' end as image,
	cast((contact.age-5+0.5)/10 as integer) as age
FROM
	Contact contact
WHERE
	contact.clientId={CLIENTID}
GROUP BY
	contact.gender,
	contact.verified,
	image,
	age
ORDER BY
	age