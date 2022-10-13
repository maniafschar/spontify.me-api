SELECT
	count(*)*100000/(select count(*) from Contact) as count,
	contact.gender,
	contact.verified,
	case when contact.image is null then '' else 'y' end as image,
	cast((contact.age-5)/10 as integer) as age
FROM
	Contact contact
GROUP BY
	contact.gender,
	age,
	verified,
	image
ORDER BY
	age,
	image,
	contact.gender,
	contact.verified